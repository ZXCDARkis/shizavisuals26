package org.shizavisuals.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.shizavisuals.context.RenderContext;
import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.TargetSelector;
import org.shizavisuals.utils.render.Textures;
import org.shizavisuals.utils.render.Theme;
import org.shizavisuals.utils.render.gui.MsdfFont;
import org.shizavisuals.utils.render.gui.PlayerHead;
import org.shizavisuals.utils.render.gui.Render2DUtil;
import org.shizavisuals.utils.render.gui.TextAlign;
import org.shizavisuals.utils.render.gui.UiFontStyle;
import org.shizavisuals.utils.render.gui.UiFonts;
import org.joml.Matrix3x2fStack;

public final class TargetElement extends HudElement {

    private static final int CARD_BG = ColorUtil.rgba(22, 22, 22, 230);
    private static final int BAR_BG = ColorUtil.rgba(40, 40, 40, 200);
    private static final int RED_TEXT = ColorUtil.rgb(239, 68, 68);
    private static final int HEALTH_RED = ColorUtil.rgb(239, 68, 68);
    private static final int GOLD_ABSORPTION = ColorUtil.rgb(250, 204, 21);

    private static final float CARD_WIDTH = 165.0F;
    private static final float CARD_HEIGHT = 42.0F;
    private static final float AVATAR_SIZE = 26.0F;
    private static final float PADDING = 8.0F;
    private static final float BAR_HEIGHT = 5.0F;
    private static final float FLOATING_ITEM_SIZE = 12.0F;

    private LivingEntity target;
    private LivingEntity fadeEntity;
    private float fadeAlpha;
    private long lastFadeTime;
    private float smoothedHealth;
    private float smoothedAbsorption;

    public TargetElement() {
        super("target", "Target");
    }

    @Override
    protected float defaultX(float unit) {
        return 200.0F * unit;
    }

    @Override
    protected float defaultY(float unit) {
        return 300.0F * unit;
    }

    @Override
    protected void layout(Minecraft mc, float unit) {
        this.target = resolveTarget(mc);
        if (this.target != null) {
            this.fadeEntity = this.target;
        } else if (this.fadeEntity != null && !TargetSelector.isDisplayTarget(mc, this.fadeEntity)) {
            // Цель умерла, ушла в инвиз или дальше 3 блоков — гасим сразу,
            // без анимации затухания (она только для потери прицела).
            this.fadeEntity = null;
            this.fadeAlpha = 0.0F;
        }
        updateFade();

        if (this.fadeEntity == null || (this.target == null && this.fadeAlpha <= 0.02F)) {
            this.width = 0.0F;
            this.height = 0.0F;
            return;
        }

        this.width = CARD_WIDTH * unit;
        this.height = (CARD_HEIGHT + 16.0F) * unit; // 16px for floating armor/weapon items
    }

    private void updateFade() {
        long now = System.currentTimeMillis();
        float delta = this.lastFadeTime == 0L ? 0.016F : Math.min(100L, now - this.lastFadeTime) / 1000.0F;
        this.lastFadeTime = now;
        float goal = this.target != null ? 1.0F : 0.0F;
        float step = Math.clamp(delta * 12.0F, 0.0F, 1.0F);
        this.fadeAlpha += (goal - this.fadeAlpha) * step;
    }

    @Override
    protected void draw(Minecraft mc, float unit) {
        LivingEntity entity = this.target != null ? this.target : this.fadeEntity;
        if (entity == null) {
            return;
        }
        float alpha = this.fadeAlpha;

        MsdfFont font = UiFonts.sfProDisplay();
        float textSize = 10.0F * unit;
        float letterSpacing = textSize * UiFontStyle.MEDIUM.letterSpacingEm();

        float cardY = this.y + 16.0F * unit; // Below floating top equipment items

        // Render Floating Equipment Items (Mainhand, Offhand on left; Armor on right) - ONLY for actual targets, not preview
        if (this.target != null) {
            drawFloatingEquipment(mc, entity, cardY, unit);
        }

        // Main Target Card Container
        Render2DUtil.rect(this.x, cardY, this.width, CARD_HEIGHT * unit)
                .color(ColorUtil.multiplyAlpha(CARD_BG, alpha))
                .radius(8.0F * unit)
                .draw();

        float contentX = this.x + PADDING * unit;
        float avatarY = cardY + PADDING * unit;
        float avatarSize = AVATAR_SIZE * unit;

        // Avatar Skin Head
        if (entity instanceof AbstractClientPlayer player) {
            PlayerHead.draw(contentX, avatarY, avatarSize, player.getSkin().body().texturePath(),
                    avatarSize * 0.2F, ColorUtil.multiplyAlpha(ColorUtil.WHITE, alpha));
        } else {
            Render2DUtil.rect(contentX, avatarY, avatarSize, avatarSize)
                    .color(ColorUtil.multiplyAlpha(ColorUtil.rgba(35, 35, 35, 200), alpha))
                    .radius(avatarSize * 0.2F)
                    .draw();
        }

        // Player Name
        float textX = contentX + avatarSize + 8.0F * unit;
        float nameCenterY = avatarY + 8.0F * unit;
        String name = entity.getName().getString();
        Render2DUtil.text(textX, font.centeredTextY(nameCenterY, textSize), textSize, name)
                .style(UiFontStyle.MEDIUM)
                .color(ColorUtil.multiplyAlpha(ColorUtil.WHITE, alpha))
                .draw();

        // Health Value icon + text with clean HUD font (no Minecraft font fallback)
        float baseHealth = entity.getHealth();
        float absorption = entity.getAbsorptionAmount();
        float totalHealth = baseHealth + absorption;
        String numStr = String.valueOf((int) Math.ceil(totalHealth));

        float numWidth = font.measureWidth(numStr, textSize, letterSpacing);
        float heartSize = 10.0F * unit;
        float heartGap = 3.0F * unit;
        float totalWidth = heartSize + heartGap + numWidth;

        float healthRight = this.x + this.width - PADDING * unit;
        float heartX = healthRight - totalWidth;
        float heartY = nameCenterY - heartSize / 2.0F;

        Render2DUtil.texture(heartX, heartY, heartSize, heartSize, Textures.Icons.SCAN_HEART)
                .color(ColorUtil.multiplyAlpha(Theme.getAccent(), alpha))
                .draw();

        Render2DUtil.text(heartX + heartSize + heartGap, font.centeredTextY(nameCenterY, textSize), textSize, numStr)
                .style(UiFontStyle.MEDIUM)
                .color(ColorUtil.multiplyAlpha(Theme.getAccent(), alpha))
                .draw();

        // Health Bar at Bottom
        float barX = textX;
        float barY = avatarY + avatarSize - BAR_HEIGHT * unit;
        float barWidth = healthRight - textX;
        float maxHealth = Math.max(1.0F, entity.getMaxHealth());
        float maxCapacity = Math.max(maxHealth, baseHealth + absorption);

        // Background track
        Render2DUtil.rect(barX, barY, barWidth, BAR_HEIGHT * unit)
                .color(ColorUtil.multiplyAlpha(BAR_BG, alpha))
                .radius(BAR_HEIGHT * unit / 2.0F)
                .draw();

        // Smooth health animations
        float step = 0.15F;
        this.smoothedHealth += (baseHealth - this.smoothedHealth) * step;
        this.smoothedAbsorption += (absorption - this.smoothedAbsorption) * step;

        float baseFraction = Math.clamp(this.smoothedHealth / maxCapacity, 0.0F, 1.0F);
        float baseFillWidth = barWidth * baseFraction;

        // Base Health Bar (uses Theme Accent)
        if (baseFillWidth > 0.5F) {
            Render2DUtil.rect(barX, barY, baseFillWidth, BAR_HEIGHT * unit)
                    .color(ColorUtil.multiplyAlpha(Theme.getAccent(), alpha))
                    .radius(BAR_HEIGHT * unit / 2.0F)
                    .draw();
        }

        // Golden Absorption Bar (only when absorption > 0 from golden apple / "чарка")
        if (this.smoothedAbsorption > 0.1F) {
            float absFraction = Math.clamp(this.smoothedAbsorption / maxCapacity, 0.0F, 1.0F - baseFraction);
            float absFillWidth = barWidth * absFraction;
            float absX = barX + baseFillWidth;

            if (absFillWidth > 0.5F) {
                Render2DUtil.rect(absX, barY, absFillWidth, BAR_HEIGHT * unit)
                        .color(ColorUtil.multiplyAlpha(GOLD_ABSORPTION, alpha))
                        .radius(BAR_HEIGHT * unit / 2.0F)
                        .draw();
            }
        }
    }

    private void drawFloatingEquipment(Minecraft mc, LivingEntity entity, float cardY, float unit) {
        GuiGraphicsExtractor extractor = RenderContext.currentGuiGraphicsExtractor();
        if (extractor == null) {
            return;
        }

        float itemSize = FLOATING_ITEM_SIZE * unit;
        float scale = itemSize / 16.0F;
        float itemY = cardY - itemSize - 2.0F * unit;

        Render2DUtil.flush();
        Matrix3x2fStack pose = extractor.pose();

        // Left floating items: Mainhand (sword), Offhand (totem)
        ItemStack mainhand = entity.getItemBySlot(EquipmentSlot.MAINHAND);
        ItemStack offhand = entity.getItemBySlot(EquipmentSlot.OFFHAND);
        if (mainhand.isEmpty() && showcase(mc)) mainhand = Items.NETHERITE_SWORD.getDefaultInstance();
        if (offhand.isEmpty() && showcase(mc)) offhand = Items.TOTEM_OF_UNDYING.getDefaultInstance();

        float leftX = this.x + 8.0F * unit;
        if (!mainhand.isEmpty()) {
            pose.pushMatrix();
            pose.translate(leftX, itemY);
            pose.scale(scale);
            extractor.item(mainhand, 0, 0);
            pose.popMatrix();
            leftX += itemSize + 2.0F * unit;
        }
        if (!offhand.isEmpty()) {
            pose.pushMatrix();
            pose.translate(leftX, itemY);
            pose.scale(scale);
            extractor.item(offhand, 0, 0);
            pose.popMatrix();
        }

        // Right floating armor items: Helmet, Chestplate, Leggings, Boots
        EquipmentSlot[] armorSlots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        ItemStack[] showcaseArmor = {
                Items.NETHERITE_HELMET.getDefaultInstance(),
                Items.NETHERITE_CHESTPLATE.getDefaultInstance(),
                Items.NETHERITE_LEGGINGS.getDefaultInstance(),
                Items.NETHERITE_BOOTS.getDefaultInstance()
        };

        float rightX = this.x + this.width - 8.0F * unit - (4 * (itemSize + 2.0F * unit));
        for (int i = 0; i < armorSlots.length; i++) {
            ItemStack armor = entity.getItemBySlot(armorSlots[i]);
            if (armor.isEmpty() && showcase(mc)) armor = showcaseArmor[i];

            if (!armor.isEmpty()) {
                pose.pushMatrix();
                pose.translate(rightX, itemY);
                pose.scale(scale);
                extractor.item(armor, 0, 0);
                pose.popMatrix();
            }
            rightX += itemSize + 2.0F * unit;
        }
    }

    private LivingEntity resolveTarget(Minecraft mc) {
        LivingEntity current = TargetSelector.pick(mc);
        if (current == null && showcase(mc)) {
            current = mc.player;
        }
        if (current == null || !current.isAlive()) {
            return null;
        }
        // Только ближние видимые цели: дальше 3 блоков и невидимки не показываются.
        return TargetSelector.isDisplayTarget(mc, current) ? current : null;
    }
}
