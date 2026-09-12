package org.shizavisuals.feature.impl.misc;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.shizavisuals.event.EventTarget;
import org.shizavisuals.event.events.game.GameTickEvent;
import org.shizavisuals.event.events.lifecycle.WorldLeaveEvent;
import org.shizavisuals.event.events.packet.PacketReceiveEvent;
import org.shizavisuals.event.events.packet.PacketSendEvent;
import org.shizavisuals.feature.Feature;
import org.shizavisuals.feature.FeatureCategory;
import org.shizavisuals.feature.setting.BindSetting;
import org.shizavisuals.feature.setting.BooleanSetting;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class FakePlayerFeature extends Feature {
    public static final UUID HEROBRINE_UUID = UUID.nameUUIDFromBytes(
            "OfflinePlayer:Herobrine".getBytes(StandardCharsets.UTF_8)
    );

    public final BooleanSetting copyInventory = register(new BooleanSetting("Копировать-Инв", false));
    public final BooleanSetting record = register(new BooleanSetting("Записывать", false));
    public final BooleanSetting play = register(new BooleanSetting("Запуск", false));
    public final BooleanSetting autoTotem = register(new BooleanSetting("Авто-Тотем", false));
    public final BooleanSetting takeDamage = register(new BooleanSetting("Получать урон", true));
    public final BooleanSetting respawnOnDeath = register(new BooleanSetting("Респавн", true));

    private RemotePlayer fakePlayer;

    private final List<PlayerState> positions = new ArrayList<>();
    private int movementTick;
    private int deathTime;

    private int fakeIdCounter = -1000;

    private double respawnX;
    private double respawnY;
    private double respawnZ;
    private float respawnYaw;
    private float respawnPitch;
    private boolean needsRespawn;
    private int respawnDelay;

    public FakePlayerFeature() {
        super("FakePlayer", "Спавнит фейкового игрока для теста ауры и кристаллов", FeatureCategory.MISC, BindSetting.UNBOUND);
    }

    public static FakePlayerFeature getEnabled() {
        return org.shizavisuals.feature.FeatureManager.INSTANCE.getEnabled(FakePlayerFeature.class);
    }

    public RemotePlayer getFakePlayer() {
        return fakePlayer;
    }

    @EventTarget
    public void onTick(GameTickEvent event) {
        Minecraft mc = event.getClient();
        if (mc.player == null || mc.level == null) {
            return;
        }

        if (needsRespawn) {
            respawnDelay++;
            if (respawnDelay >= 15) {
                spawnFakePlayer(mc, respawnX, respawnY, respawnZ, respawnYaw, respawnPitch);
                needsRespawn = false;
                respawnDelay = 0;
                deathTime = 0;
            }
            return;
        }

        if (fakePlayer != null && fakePlayer.isRemoved()) {
            double x = fakePlayer.getX();
            double y = fakePlayer.getY();
            double z = fakePlayer.getZ();
            float yaw = fakePlayer.getYRot();
            float pitch = fakePlayer.getXRot();
            fakePlayer = null;
            spawnFakePlayer(mc, x, y, z, yaw, pitch);
            return;
        }

        if (fakePlayer == null) {
            return;
        }

        if (!autoTotem.getValue()) {
            if (fakePlayer.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
                fakePlayer.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            }
        } else {
            if (!fakePlayer.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
                fakePlayer.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            }
        }

        if (record.getValue()) {
            positions.add(new PlayerState(
                    mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    mc.player.getYRot(), mc.player.getXRot()
            ));
            return;
        }

        if (play.getValue() && !positions.isEmpty()) {
            movementTick++;
            if (movementTick >= positions.size()) {
                movementTick = 0;
            }

            PlayerState state = positions.get(movementTick);
            fakePlayer.absSnapTo(state.x(), state.y(), state.z(), state.yaw(), state.pitch());
            fakePlayer.setYRot(state.yaw());
            fakePlayer.setXRot(state.pitch());
            fakePlayer.setYHeadRot(state.yaw());
            fakePlayer.setYBodyRot(state.yaw());
        } else {
            movementTick = 0;
        }

        if (fakePlayer.isDeadOrDying() || fakePlayer.getHealth() <= 0.0F) {
            deathTime++;
            if (deathTime >= 10) {
                if (respawnOnDeath.getValue()) {
                    respawnX = fakePlayer.getX();
                    respawnY = fakePlayer.getY();
                    respawnZ = fakePlayer.getZ();
                    respawnYaw = fakePlayer.getYRot();
                    respawnPitch = fakePlayer.getXRot();

                    removeFakePlayer();
                    needsRespawn = true;
                    respawnDelay = 0;
                } else {
                    setEnabled(false);
                }
            }
        } else {
            deathTime = 0;
        }
    }

    @EventTarget
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPhase() != PacketReceiveEvent.Phase.PRE) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || fakePlayer == null) {
            return;
        }
        if (!(event.getPacket() instanceof ClientboundExplodePacket)) {
            return;
        }
        if (fakePlayer.hurtTime != 0) {
            return;
        }

        applyDamage(mc, 4.0F, mc.level.damageSources().generic());
    }

    @EventTarget
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPhase() != PacketSendEvent.Phase.PRE) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || fakePlayer == null || !takeDamage.getValue()) {
            return;
        }
        if (!(event.getPacket() instanceof ServerboundInteractPacket packet)) {
            return;
        }
        if (packet.entityId() != fakePlayer.getId()) {
            return;
        }
        if (packet.location() != null || packet.hand() == null) {
            return;
        }

        applyDamage(mc, 4.0F, mc.level.damageSources().playerAttack(mc.player));
    }

    @EventTarget
    public void onWorldLeave(WorldLeaveEvent event) {
        fakePlayer = null;
        positions.clear();
        deathTime = 0;
        movementTick = 0;
        needsRespawn = false;
        respawnDelay = 0;
    }

    private void applyDamage(Minecraft mc, float amount, net.minecraft.world.damagesource.DamageSource source) {
        if (fakePlayer == null || mc.level == null) {
            return;
        }

        float newHealth = fakePlayer.getHealth() - amount;

        fakePlayer.hurtTime = 10;
        fakePlayer.hurtDuration = 10;

        mc.level.playSound(
                fakePlayer,
                fakePlayer.getX(), fakePlayer.getY(), fakePlayer.getZ(),
                SoundEvents.PLAYER_HURT,
                SoundSource.PLAYERS,
                1.0F, 1.0F
        );

        if (newHealth <= 0.0F) {
            ItemStack offHand = fakePlayer.getOffhandItem();
            if (offHand.is(Items.TOTEM_OF_UNDYING)) {
                fakePlayer.setHealth(10.0F);
                fakePlayer.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);

                mc.level.playSound(
                        fakePlayer,
                        fakePlayer.getX(), fakePlayer.getY(), fakePlayer.getZ(),
                        SoundEvents.TOTEM_USE,
                        SoundSource.PLAYERS,
                        1.0F, 1.0F
                );

                fakePlayer.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
                fakePlayer.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
                fakePlayer.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0));
                return;
            }

            fakePlayer.setHealth(0.0F);

            mc.level.playSound(
                    fakePlayer,
                    fakePlayer.getX(), fakePlayer.getY(), fakePlayer.getZ(),
                    SoundEvents.PLAYER_DEATH,
                    SoundSource.PLAYERS,
                    1.0F, 1.0F
            );
            return;
        }

        fakePlayer.setHealth(newHealth);
    }

    private void spawnFakePlayer(Minecraft mc, double x, double y, double z, float yaw, float pitch) {
        if (mc.player == null || mc.level == null) {
            return;
        }

        ClientLevel level = mc.level;
        fakePlayer = new RemotePlayer(level, new GameProfile(HEROBRINE_UUID, "Herobrine"));

        fakePlayer.setId(fakeIdCounter--);

        fakePlayer.absSnapTo(x, y, z, yaw, pitch);
        fakePlayer.setYRot(yaw);
        fakePlayer.setXRot(pitch);
        fakePlayer.setYHeadRot(yaw);
        fakePlayer.setYBodyRot(yaw);
        fakePlayer.setHealth(20.0F);
        fakePlayer.hurtTime = 0;
        fakePlayer.fallDistance = 0.0F;

        if (copyInventory.getValue()) {
            fakePlayer.setItemInHand(InteractionHand.MAIN_HAND, mc.player.getMainHandItem().copy());
            fakePlayer.setItemInHand(InteractionHand.OFF_HAND, mc.player.getOffhandItem().copy());

            for (EquipmentSlot slot : new EquipmentSlot[]{
                    EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
            }) {
                fakePlayer.setItemSlot(slot, mc.player.getItemBySlot(slot).copy());
            }
        } else {
            if (autoTotem.getValue()) {
                fakePlayer.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            }
        }

        level.addEntity(fakePlayer);
        fakePlayer.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 9999, 1));
        fakePlayer.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 9999, 2));
        fakePlayer.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 9999, 0));
    }

    private void removeFakePlayer() {
        if (fakePlayer != null) {
            fakePlayer.setRemoved(Entity.RemovalReason.KILLED);
            fakePlayer = null;
        }
    }

    @Override
    protected void onEnable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }

        needsRespawn = false;
        respawnDelay = 0;
        deathTime = 0;
        movementTick = 0;

        spawnFakePlayer(
                mc,
                mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                mc.player.getYRot(), mc.player.getXRot()
        );
    }

    @Override
    protected void onDisable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            removeFakePlayer();
        } else {
            fakePlayer = null;
        }

        positions.clear();
        deathTime = 0;
        movementTick = 0;
        needsRespawn = false;
        respawnDelay = 0;
    }

    private record PlayerState(double x, double y, double z, float yaw, float pitch) {}
}
