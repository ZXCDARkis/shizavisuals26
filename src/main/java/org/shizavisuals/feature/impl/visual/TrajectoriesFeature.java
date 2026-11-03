package org.shizavisuals.feature.impl.visual;

import org.shizavisuals.event.EventTarget;
import org.shizavisuals.event.events.lifecycle.WorldLeaveEvent;
import org.shizavisuals.event.events.render.Render2DEvent;
import org.shizavisuals.context.MinecraftContext;
import org.shizavisuals.feature.Feature;
import org.shizavisuals.feature.FeatureCategory;
import org.shizavisuals.feature.FeatureManager;
import org.shizavisuals.feature.setting.BindSetting;
import org.shizavisuals.feature.setting.BooleanSetting;
import org.shizavisuals.feature.setting.MultiSelectSetting;
import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.render.Theme;
import org.shizavisuals.utils.render.Render3DUtil;
import org.shizavisuals.utils.render.world.WorldMeshRenderer;
import org.shizavisuals.utils.render.gui.Render2DUtil;
import org.shizavisuals.utils.render.gui.ScaleUtil;
import org.shizavisuals.utils.render.gui.TextAlign;
import org.shizavisuals.utils.render.gui.UiFontStyle;
import org.shizavisuals.utils.render.gui.UiFonts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEgg;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownLingeringPotion;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.EggItem;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.ExperienceBottleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.LingeringPotionItem;
import net.minecraft.world.item.SnowballItem;
import net.minecraft.world.item.SplashPotionItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class TrajectoriesFeature extends Feature implements MinecraftContext {

    private static final String TARGET_SELF = "Self";
    private static final String TARGET_PLAYERS = "Players";

    private final MultiSelectSetting predictTrajectory = register(new MultiSelectSetting(
            "Predict Trajectory",
            Set.of(TARGET_SELF),
            TARGET_SELF, TARGET_PLAYERS
    ));
    private final BooleanSetting showOwner = register(new BooleanSetting(
            "Show Owner",
            true
    ));

    private final List<ScreenLine> pendingScreenLines = new ArrayList<>();
    private final List<ImpactPoint> points = new ArrayList<>();
    private int tpSkipTicks;

    public TrajectoriesFeature() {
        super("Trajectories", "Предсказывает траектории снарядов и точку падения", FeatureCategory.VISUAL, BindSetting.UNBOUND);
    }

    public static TrajectoriesFeature getEnabled() {
        return FeatureManager.INSTANCE.getEnabled(TrajectoriesFeature.class);
    }

    @EventTarget
    public void on2DRender(Render2DEvent event) {
        if (pendingScreenLines.isEmpty() && points.isEmpty()) {
            return;
        }

        float unit = ScaleUtil.toGuiPixels(DESIGN_SCALE, mc.getWindow().getGuiScale());
        float centerX = event.getGuiGraphicsExtractor().guiWidth() * 0.5F;
        float centerY = event.getGuiGraphicsExtractor().guiHeight() * 0.5F;
        float connectorThickness = Math.max(0.5F, SCREEN_CONNECTOR_THICKNESS * unit);
        for (ScreenLine line : pendingScreenLines) {
            drawScreenLine(event, centerX, centerY, line.x(), line.y(), connectorThickness, line.color());
        }

        for (ImpactPoint point : points) {
            ScreenPoint screen = projectToScreen(point.pos());
            if (screen == null) {
                continue;
            }
            renderImpactTag(event, point, screen.x(), screen.y(), unit);
        }
    }

    @EventTarget
    public void onWorldLeave(WorldLeaveEvent event) {
        clear();
    }

    public void renderWorld() {
        pendingScreenLines.clear();
        points.clear();
        if (mc.level == null || mc.player == null) {
            clear();
            return;
        }

        Vec3 oldPlayerPos = mc.player.oldPosition();
        double dx = mc.player.getX() - oldPlayerPos.x;
        double dy = mc.player.getY() - oldPlayerPos.y;
        double dz = mc.player.getZ() - oldPlayerPos.z;
        if (dx * dx + dy * dy + dz * dz > 36.0D) {
            tpSkipTicks = 2;
        }
        if (tpSkipTicks > 0) {
            tpSkipTicks--;
            pendingScreenLines.clear();
            return;
        }

        List<WorldMeshRenderer.Line> lines = new ArrayList<>();
        List<WorldMeshRenderer.Ring> rings = new ArrayList<>();
        List<WorldMeshRenderer.PlaneRect> planeRects = new ArrayList<>();

        if (predictTrajectory.isSelected(TARGET_SELF)) {
            drawPredictionInHand(lines, rings, planeRects);
        }
        if (predictTrajectory.isSelected(TARGET_PLAYERS)) {
            drawOtherPlayersPrediction(lines, rings, planeRects);
        }

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof Projectile) && !(entity instanceof ItemEntity)) {
                continue;
            }
            if (isStationary(entity)) {
                continue;
            }

            Vec3 motion = entity.getDeltaMovement();
            float partialTicks = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            double startX = Mth.lerp(partialTicks, entity.xo, entity.getX());
            double startY = Mth.lerp(partialTicks, entity.yo, entity.getY());
            double startZ = Mth.lerp(partialTicks, entity.zo, entity.getZ());
            Vec3 pos = new Vec3(startX, startY, startZ);

            for (int tick = 0; tick < MAX_SIMULATION_TICKS; tick++) {
                Vec3 prevPos = pos;
                TrajectoryStep step;
                if (entity instanceof Projectile projectile) {
                    step = simulateProjectileStep(projectile, pos, motion);
                } else {
                    Vec3 nextPos = pos.add(motion);
                    Vec3 nextMotion = calculateMotion(entity, prevPos, motion);
                    HitResult hit = raycastBlock(prevPos, nextPos, entity);
                    step = new TrajectoryStep(
                            hit.getType() != HitResult.Type.MISS ? hit.getLocation() : nextPos,
                            nextMotion,
                            hit.getType() != HitResult.Type.MISS ? hit : null
                    );
                }

                Vec3 renderEnd = step.hitResult() != null ? step.hitResult().getLocation() : step.nextPos();
                float alpha = Mth.clamp(tick / 7.0F, 0.0F, 1.0F);
                lines.add(new WorldMeshRenderer.Line(prevPos, renderEnd, ColorUtil.applyAlpha(animatedAccentColor(tick), alpha)));

                if (step.hitResult() != null || step.nextPos().y < -128.0D) {
                    registerImpact(entity, renderEnd, tick);
                    break;
                }

                pos = step.nextPos();
                motion = step.nextMotion();
            }
        }

        WorldMeshRenderer.render(new WorldMeshRenderer.WorldMesh(lines, rings, planeRects));
    }

    @Override
    protected void onDisable() {
        clear();
    }

    private void drawPredictionInHand(
            List<WorldMeshRenderer.Line> lines,
            List<WorldMeshRenderer.Ring> rings,
            List<WorldMeshRenderer.PlaneRect> planeRects
    ) {
        if (mc.level == null || mc.player == null) {
            return;
        }

        drawPredictionForPlayer(mc.player, true, lines, rings, planeRects);
    }

    private void drawOtherPlayersPrediction(
            List<WorldMeshRenderer.Line> lines,
            List<WorldMeshRenderer.Ring> rings,
            List<WorldMeshRenderer.PlaneRect> planeRects
    ) {
        if (mc.level == null || mc.player == null) {
            return;
        }

        for (Player player : mc.level.players()) {
            if (player == mc.player || !player.isAlive() || player.isRemoved()) {
                continue;
            }
            drawPredictionForPlayer(player, false, lines, rings, planeRects);
        }
    }

    private void drawPredictionForPlayer(
            Player player,
            boolean screenAnchored,
            List<WorldMeshRenderer.Line> lines,
            List<WorldMeshRenderer.Ring> rings,
            List<WorldMeshRenderer.PlaneRect> planeRects
    ) {
        if (mc.level == null || player == null) {
            return;
        }

        ItemStack activeStack = player.getUseItem();
        ItemStack[] stacks = {player.getMainHandItem(), player.getOffhandItem()};

        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }

            float partialTicks = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            float pitch = Mth.lerp(partialTicks, player.xRotO, player.getXRot());
            float yaw = Mth.lerp(partialTicks, player.yRotO, player.getYRot());

            List<Prediction> predictions = new ArrayList<>();
            var item = stack.getItem();
            if (item instanceof ExperienceBottleItem) {
                addPrediction(predictions, checkTrajectory(player, new ThrownExperienceBottle(mc.level, player, stack), 0.7D, pitch, yaw, -20.0F, screenAnchored));
            } else if (item instanceof SplashPotionItem) {
                addPrediction(predictions, checkTrajectory(player, new ThrownSplashPotion(mc.level, player, stack), 0.5D, pitch, yaw, -20.0F, screenAnchored));
            } else if (item instanceof LingeringPotionItem) {
                addPrediction(predictions, checkTrajectory(player, new ThrownLingeringPotion(mc.level, player, stack), 0.5D, pitch, yaw, -20.0F, screenAnchored));
            } else if (item instanceof TridentItem && !activeStack.isEmpty() && activeStack.getItem() == item && player.getTicksUsingItem() >= 10) {
                addPrediction(predictions, checkTrajectory(player, new ThrownTrident(mc.level, player, stack), 2.5D, pitch, yaw, 0.0F, screenAnchored));
            } else if (item instanceof SnowballItem) {
                addPrediction(predictions, checkTrajectory(player, new Snowball(mc.level, player, stack), 1.5D, pitch, yaw, 0.0F, screenAnchored));
            } else if (item instanceof EggItem) {
                addPrediction(predictions, checkTrajectory(player, new ThrownEgg(mc.level, player, stack), 1.5D, pitch, yaw, 0.0F, screenAnchored));
            } else if (item instanceof EnderpearlItem) {
                addPrediction(predictions, checkTrajectory(player, new ThrownEnderpearl(mc.level, player, stack), 1.5D, pitch, yaw, 0.0F, screenAnchored));
            } else if (item instanceof BowItem && !activeStack.isEmpty() && activeStack.getItem() == item && player.isUsingItem()) {
                float power = BowItem.getPowerForTime(player.getTicksUsingItem()) * 3.0F;
                addPrediction(predictions, checkTrajectory(player, newArrow(player, stack), power, pitch, yaw, 0.0F, screenAnchored));
            } else if (item instanceof CrossbowItem && CrossbowItem.isCharged(stack)) {
                ChargedProjectiles charged = stack.get(DataComponents.CHARGED_PROJECTILES);
                if (charged != null && !charged.isEmpty()) {
                    double velocity = charged.contains(Items.FIREWORK_ROCKET) ? 1.6D : 3.15D;
                    addPrediction(predictions, checkTrajectory(player, buildCrossbowDirection(player, partialTicks, 0.0F), newArrow(player, stack), velocity, screenAnchored));
                    if (charged.itemCopies().size() > 2) {
                        addPrediction(predictions, checkTrajectory(player, buildCrossbowDirection(player, partialTicks, -10.0F), newArrow(player, stack), velocity, screenAnchored));
                        addPrediction(predictions, checkTrajectory(player, buildCrossbowDirection(player, partialTicks, 10.0F), newArrow(player, stack), velocity, screenAnchored));
                    }
                }
            }

            for (Prediction prediction : predictions) {
                if (screenAnchored) {
                    ScreenPoint connectorEnd = projectToScreen(prediction.screenConnectorEnd());
                    if (connectorEnd != null) {
                        pendingScreenLines.add(new ScreenLine(connectorEnd.x(), connectorEnd.y(), animatedAccentColor(0)));
                    }
                }
                lines.addAll(prediction.lines());
                if (prediction.result() != null) {
                    addImpactMarker(lines, rings, planeRects, prediction.projectile(), prediction.result());
                }
            }
        }
    }

    private Arrow newArrow(LivingEntity shooter, ItemStack weapon) {
        return new Arrow(mc.level, shooter.getX(), shooter.getEyeY() - 0.1D, shooter.getZ(), new ItemStack(Items.ARROW), weapon);
    }

    private Prediction checkTrajectory(LivingEntity shooter, Projectile entity, double velocity, float pitch, float yaw, float angleOffset, boolean screenAnchored) {
        return checkTrajectory(shooter, buildShootFromRotationDirection(pitch, yaw, angleOffset), entity, velocity, screenAnchored);
    }

    private Prediction checkTrajectory(LivingEntity shooter, Vec3 lookVec, Projectile entity, double velocity, boolean screenAnchored) {
        if (shooter == null) {
            return new Prediction(entity, null, List.of(), null);
        }

        float partialTicks = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double startX = Mth.lerp(partialTicks, shooter.xo, shooter.getX());
        double startY = Mth.lerp(partialTicks, shooter.yo, shooter.getY()) + shooter.getEyeHeight();
        double startZ = Mth.lerp(partialTicks, shooter.zo, shooter.getZ());
        Vec3 startPos = new Vec3(startX, startY - 0.1D, startZ);
        entity.setPos(startPos.x, startPos.y, startPos.z);

        Vec3 motion;
        if (entity instanceof AbstractArrow arrow && arrow.getWeaponItem() != null && arrow.getWeaponItem().getItem() == Items.CROSSBOW) {
            motion = lookVec.normalize().scale(velocity);
        } else {
            motion = buildShootFromRotationMotion(shooter, lookVec, velocity, true);
        }
        return traceTrajectory(startPos, motion, entity, screenAnchored);
    }

    private Prediction traceTrajectory(Vec3 start, Vec3 startMotion, Projectile entity, boolean screenAnchored) {
        List<WorldMeshRenderer.Line> lines = new ArrayList<>();
        Vec3 screenConnectorEnd = null;
        Vec3 pos = start;
        Vec3 motion = startMotion;

        for (int tick = 0; tick < MAX_SIMULATION_TICKS; tick++) {
            TrajectoryStep step = simulateProjectileStep(entity, pos, motion);
            Vec3 renderEnd = step.hitResult() != null ? step.hitResult().getLocation() : step.nextPos();
            float alpha = Mth.clamp(tick / 7.0F, 0.0F, 1.0F);
            if (tick == 0 && screenAnchored) {
                screenConnectorEnd = renderEnd;
            } else {
                lines.add(new WorldMeshRenderer.Line(pos, renderEnd, ColorUtil.applyAlpha(animatedAccentColor(tick), alpha)));
            }
            if (step.hitResult() != null) {
                return new Prediction(entity, step.hitResult(), lines, screenConnectorEnd);
            }
            if (step.nextPos().y < -128.0D) {
                break;
            }

            pos = step.nextPos();
            motion = step.nextMotion();
        }

        return new Prediction(entity, null, lines, screenConnectorEnd);
    }

    private TrajectoryStep simulateProjectileStep(Projectile projectile, Vec3 pos, Vec3 motion) {
        if (projectile instanceof AbstractArrow arrow) {
            return simulateArrowStep(arrow, pos, motion);
        }
        return simulateThrowableStep(projectile, pos, motion);
    }

    private TrajectoryStep simulateThrowableStep(Projectile projectile, Vec3 pos, Vec3 motion) {
        Vec3 nextMotion = motion
                .add(0.0D, -projectileGravity(projectile), 0.0D)
                .scale(isInWater(pos) ? THROWABLE_WATER_INERTIA : AIR_INERTIA);
        HitResult hit = raycastProjectile(pos, nextMotion, projectile);
        return new TrajectoryStep(
                hit != null ? hit.getLocation() : pos.add(nextMotion),
                nextMotion,
                hit
        );
    }

    private TrajectoryStep simulateArrowStep(AbstractArrow arrow, Vec3 pos, Vec3 motion) {
        boolean inWater = isInWater(pos);
        Vec3 moveDelta = inWater ? motion.scale(arrow instanceof ThrownTrident ? TRIDENT_WATER_INERTIA : ARROW_WATER_INERTIA) : motion;
        HitResult hit = raycastProjectile(pos, moveDelta, arrow);
        Vec3 nextPos = hit != null ? hit.getLocation() : pos.add(moveDelta);
        Vec3 nextMotion = inWater
                ? moveDelta.add(0.0D, -ARROW_GRAVITY, 0.0D)
                : moveDelta.scale(AIR_INERTIA).add(0.0D, -ARROW_GRAVITY, 0.0D);
        return new TrajectoryStep(nextPos, nextMotion, hit);
    }

    private HitResult raycastProjectile(Vec3 start, Vec3 motion, Projectile projectile) {
        if (mc.level == null) {
            return null;
        }

        Vec3 end = start.add(motion);
        HitResult hit = mc.level.clipIncludingBorder(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, projectile));
        if (hit.getType() != HitResult.Type.MISS) {
            end = hit.getLocation();
        }

        AABB boxAtStart = projectile.getBoundingBox().move(start.subtract(projectile.position()));
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                mc.level,
                projectile,
                start,
                end,
                boxAtStart.expandTowards(motion).inflate(1.0D),
                candidate -> canHitProjectileTarget(projectile, candidate)
        );
        if (entityHit != null) {
            hit = entityHit;
        }
        return hit.getType() != HitResult.Type.MISS ? hit : null;
    }

    private boolean canHitProjectileTarget(Projectile projectile, Entity candidate) {
        if (!candidate.canBeHitByProjectile()) {
            return false;
        }

        Entity owner = projectile.getOwner();
        if (owner == null) {
            return true;
        }
        if (candidate == owner || owner.isPassengerOfSameVehicle(candidate)) {
            return false;
        }

        return !(projectile instanceof AbstractArrow && owner instanceof Player ownerPlayer && candidate instanceof Player targetPlayer && !ownerPlayer.canHarmPlayer(targetPlayer));
    }

    private double projectileGravity(Projectile projectile) {
        if (projectile instanceof AbstractArrow) {
            return ARROW_GRAVITY;
        }
        if (projectile instanceof ThrownExperienceBottle) {
            return EXPERIENCE_BOTTLE_GRAVITY;
        }
        if (projectile instanceof AbstractThrownPotion) {
            return POTION_GRAVITY;
        }
        if (projectile instanceof ThrowableItemProjectile) {
            return THROWABLE_GRAVITY;
        }
        return THROWABLE_GRAVITY;
    }

    private boolean isInWater(Vec3 pos) {
        return mc.level != null && mc.level.getFluidState(BlockPos.containing(pos)).is(FluidTags.WATER);
    }

    private Vec3 buildShootFromRotationMotion(LivingEntity shooter, Vec3 direction, double velocity, boolean addShooterMovement) {
        Vec3 motion = direction.normalize().scale(velocity);
        if (addShooterMovement) {
            Vec3 knownMovement = shooter.getKnownMovement();
            motion = motion.add(knownMovement.x, shooter.onGround() ? 0.0D : knownMovement.y, knownMovement.z);
        }
        return motion;
    }

    private Vec3 buildShootFromRotationDirection(float pitch, float yaw, float angleOffset) {
        double pitchRad = pitch * (Math.PI / 180.0D);
        double yawRad = yaw * (Math.PI / 180.0D);
        double x = -Math.sin(yawRad) * Math.cos(pitchRad);
        double y = -Math.sin((pitch + angleOffset) * (Math.PI / 180.0D));
        double z = Math.cos(yawRad) * Math.cos(pitchRad);
        return new Vec3(x, y, z);
    }

    private Vec3 buildCrossbowDirection(LivingEntity shooter, float partialTicks, float angle) {
        if (angle == 0.0F) {
            return shooter.getViewVector(partialTicks);
        }

        Vec3 up = shooter.getUpVector(partialTicks);
        Quaternionf rotation = new Quaternionf().setAngleAxis(angle * (Math.PI / 180.0D), up.x, up.y, up.z);
        Vector3f rotated = shooter.getViewVector(partialTicks).toVector3f().rotate(rotation);
        return new Vec3(rotated.x, rotated.y, rotated.z);
    }

    private Vec3 calculateMotion(Entity entity, Vec3 prevPos, Vec3 motion) {
        boolean water = mc.level != null && mc.level.getFluidState(BlockPos.containing(prevPos)).is(FluidTags.WATER);
        double inertia;
        double gravity;

        if (entity instanceof ThrownTrident) {
            inertia = 0.99D;
            gravity = 0.05D;
        } else if (entity instanceof AbstractArrow) {
            inertia = water ? 0.6D : 0.99D;
            gravity = 0.05D;
        } else if (entity instanceof ThrownExperienceBottle) {
            inertia = water ? 0.8D : 0.99D;
            gravity = 0.07D;
        } else if (entity instanceof AbstractThrownPotion) {
            inertia = water ? 0.8D : 0.99D;
            gravity = 0.05D;
        } else if (entity instanceof ThrowableItemProjectile) {
            inertia = water ? 0.8D : 0.99D;
            gravity = 0.03D;
        } else if (entity instanceof ItemEntity) {
            inertia = water ? 0.8D : 0.98D;
            gravity = 0.04D;
        } else {
            inertia = 0.99D;
            gravity = 0.03D;
        }

        return motion.scale(inertia).add(0.0D, -gravity, 0.0D);
    }

    private void addImpactMarker(
            List<WorldMeshRenderer.Line> lines,
            List<WorldMeshRenderer.Ring> rings,
            List<WorldMeshRenderer.PlaneRect> planeRects,
            Projectile projectile,
            HitResult result
    ) {
        Direction direction = getDirection(result);
        int color = result.getType() == HitResult.Type.ENTITY ? 0xFFFF4444 : animatedAccentColor(0);
        Vec3 center = result.getLocation().add(direction.getStepX() * 0.01D, direction.getStepY() * 0.01D, direction.getStepZ() * 0.01D);
        double width = 0.12D;
        Vec3 u;
        Vec3 v;
        switch (direction.getAxis()) {
            case X -> {
                u = new Vec3(0.0D, 1.0D, 0.0D);
                v = new Vec3(0.0D, 0.0D, 1.0D);
            }
            case Y -> {
                u = new Vec3(1.0D, 0.0D, 0.0D);
                v = new Vec3(0.0D, 0.0D, 1.0D);
            }
            case Z -> {
                u = new Vec3(1.0D, 0.0D, 0.0D);
                v = new Vec3(0.0D, 1.0D, 0.0D);
            }
            default -> {
                u = new Vec3(1.0D, 0.0D, 0.0D);
                v = new Vec3(0.0D, 0.0D, 1.0D);
            }
        }

        rings.add(new WorldMeshRenderer.Ring(center, u, v, width, IMPACT_RING_HALF_WIDTH, color, IMPACT_RING_SEGMENTS));
        double crossRadius = width * IMPACT_CROSS_RADIUS_FACTOR;
        planeRects.add(new WorldMeshRenderer.PlaneRect(center, u, v, crossRadius, IMPACT_CROSS_HALF_WIDTH, color));
        planeRects.add(new WorldMeshRenderer.PlaneRect(center, v, u, crossRadius, IMPACT_CROSS_HALF_WIDTH, color));

        if (result instanceof EntityHitResult entityHit) {
            addEntityHitBox(lines, planeRects, entityHit.getEntity());
        }

        Double areaRadius = potionAreaRadius(projectile);
        if (areaRadius == null) {
            return;
        }

        Vec3 areaCenter = resolvePotionAreaCenter(projectile, result);
        int areaColor = animatedAccentColor(0);
        rings.add(new WorldMeshRenderer.Ring(
                areaCenter,
                new Vec3(1.0D, 0.0D, 0.0D),
                new Vec3(0.0D, 0.0D, 1.0D),
                areaRadius,
                POTION_AREA_RING_HALF_WIDTH,
                ColorUtil.applyAlpha(areaColor, POTION_AREA_ALPHA),
                POTION_AREA_RING_SEGMENTS
        ));

        if (areaCenter.distanceToSqr(result.getLocation()) > 1.0E-4D) {
            lines.add(new WorldMeshRenderer.Line(result.getLocation(), areaCenter, ColorUtil.applyAlpha(areaColor, POTION_AREA_CONNECTOR_ALPHA)));
        }

        if (projectile instanceof ThrownSplashPotion splashPotion) {
            addSplashExposureLines(lines, splashPotion, result, areaCenter);
        }
    }

    private void registerImpact(Entity entity, Vec3 pos, int ticks) {
        ItemStack stack = ItemStack.EMPTY;
        String ownerName = null;

        if (entity instanceof ItemEntity itemEntity) {
            stack = itemEntity.getItem();
        } else if (entity instanceof ThrowableItemProjectile throwable) {
            stack = throwable.getItem();
            ownerName = ownerName(throwable.getOwner());
        } else if (entity instanceof ThrownTrident trident) {
            stack = new ItemStack(Items.TRIDENT);
            ownerName = ownerName(trident.getOwner());
        } else if (entity instanceof AbstractArrow arrow) {
            stack = arrow.getPickupItemStackOrigin();
            ownerName = ownerName(arrow.getOwner());
        }

        List<MobEffectInstance> effects = new ArrayList<>();
        var potionContents = stack.get(DataComponents.POTION_CONTENTS);
        if (potionContents != null) {
            potionContents.getAllEffects().forEach(effects::add);
        }
        points.add(new ImpactPoint(stack.copy(), pos, ticks, entity.tickCount, ownerName, List.copyOf(effects)));
    }

    private String ownerName(Entity owner) {
        return owner instanceof LivingEntity living ? living.getName().getString() : null;
    }

    private void renderImpactTag(Render2DEvent event, ImpactPoint point, float screenX, float screenY, float unit) {
        float width = TAG_WIDTH * unit;
        float card = TAG_CARD * unit;
        float height = TAG_HEIGHT * unit;
        float gap = TAG_GAP * unit;
        float radius = TAG_RADIUS * unit;
        float border = Math.max(1.0F, TAG_BORDER * unit);
        float icon = TAG_ICON * unit;
        float textSize = TAG_TEXT_SIZE * unit;
        float textRow = TAG_TEXT_ROW * unit;

        float x = screenX - width * 0.5F;
        float y = screenY - height * 0.5F;

        int totalTicks = point.ticks() + point.entityAge();
        float progress = totalTicks > 0
                ? Mth.clamp((float) point.ticks() / totalTicks, 0.0F, 1.0F)
                : 0.0F;
        int accent = Theme.getAccent();

        Render2DUtil.rect(x, y, card, card)
                .color(Theme.Colors.BACKGROUND_PRIMARY_50)
                .radius(radius)
                .blur(8.0F * unit)
                .draw();

        drawRoundedProgressBorder(event, x, y, card, radius, border, progress, accent);

        float iconOffset = (card - icon) * 0.5F;
        drawScaledItem(event, point.stack(), x + iconOffset, y + iconOffset, icon);

        String time = formatSeconds(point.ticks());
        float textCenterX = x + width * 0.5F;
        float textCenterY = y + card + gap + textRow * 0.5F;
        Render2DUtil.text(textCenterX, UiFonts.sfProDisplay().centeredTextY(textCenterY, textSize), textSize, time)
                .style(UiFontStyle.MEDIUM)
                .align(TextAlign.CENTER)
                .color(Theme.Colors.TEXT_TITLE)
                .draw();
    }

    private void drawScaledItem(Render2DEvent event, ItemStack stack, float x, float y, float size) {
        if (stack.isEmpty()) {
            return;
        }

        Render2DUtil.flush();
        double guiScale = mc.getWindow().getGuiScale();
        float scale = size / 16.0F;
        float itemX = (float) (Math.round(x * guiScale) / guiScale);
        float itemY = (float) (Math.round(y * guiScale) / guiScale);
        var pose = event.getGuiGraphicsExtractor().pose();
        pose.pushMatrix();
        pose.translate(itemX, itemY);
        pose.scale(scale);
        event.getGuiGraphicsExtractor().item(stack, 0, 0);
        pose.popMatrix();
    }

    private void drawRoundedProgressBorder(Render2DEvent event, float x, float y, float size, float radius, float thickness, float progress, int color) {
        progress = Mth.clamp(progress, 0.0F, 1.0F);
        if (progress <= 0.001F || thickness <= 0.0F) {
            return;
        }

        float straight = Math.max(0.0F, size - radius * 2.0F);
        float arc = (float) (Math.PI * 0.5D * radius);
        float perimeter = straight * 4.0F + arc * 4.0F;
        float target = perimeter * progress;
        float half = thickness * 0.5F;

        int samples = Math.max(48, Math.round(perimeter / 1.5F));
        float traveled = 0.0F;
        float prevX = x + size * 0.5F;
        float prevY = y;
        for (int i = 1; i <= samples; i++) {
            float distance = perimeter * (i / (float) samples);
            float[] point = pointOnRoundedRect(x, y, size, radius, straight, arc, distance);
            float seg = distance - traveled;
            if (traveled < target) {
                float drawLen = Math.min(seg, target - traveled);
                float t = drawLen / seg;
                float endX = prevX + (point[0] - prevX) * t;
                float endY = prevY + (point[1] - prevY) * t;
                strokeSegment(event, prevX, prevY, endX, endY, half, color);
            }
            traveled = distance;
            prevX = point[0];
            prevY = point[1];
            if (traveled >= target) {
                break;
            }
        }
    }

    private static float[] pointOnRoundedRect(float x, float y, float size, float radius, float straight, float arc, float distance) {
        float cursor = distance;

        float halfTop = straight * 0.5F;
        if (cursor <= halfTop) {
            return new float[]{x + size * 0.5F + cursor, y};
        }
        cursor -= halfTop;

        if (cursor <= arc) {
            float angle = (float) (-Math.PI * 0.5D + cursor / radius); 
            return new float[]{
                    x + size - radius + (float) Math.cos(angle) * radius,
                    y + radius + (float) Math.sin(angle) * radius
            };
        }
        cursor -= arc;

        if (cursor <= straight) {
            return new float[]{x + size, y + radius + cursor};
        }
        cursor -= straight;

        if (cursor <= arc) {
            float angle = cursor / radius; 
            return new float[]{
                    x + size - radius + (float) Math.cos(angle) * radius,
                    y + size - radius + (float) Math.sin(angle) * radius
            };
        }
        cursor -= arc;

        if (cursor <= straight) {
            return new float[]{x + size - radius - cursor, y + size};
        }
        cursor -= straight;

        if (cursor <= arc) {
            float angle = (float) (Math.PI * 0.5D + cursor / radius); 
            return new float[]{
                    x + radius + (float) Math.cos(angle) * radius,
                    y + size - radius + (float) Math.sin(angle) * radius
            };
        }
        cursor -= arc;

        if (cursor <= straight) {
            return new float[]{x, y + size - radius - cursor};
        }
        cursor -= straight;

        if (cursor <= arc) {
            float angle = (float) (Math.PI + cursor / radius); 
            return new float[]{
                    x + radius + (float) Math.cos(angle) * radius,
                    y + radius + (float) Math.sin(angle) * radius
            };
        }
        cursor -= arc;

        return new float[]{x + radius + Math.min(cursor, halfTop), y};
    }

    private void strokeSegment(Render2DEvent event, float x1, float y1, float x2, float y2, float halfThickness, int color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 0.001F) {
            Render2DUtil.rect(x1 - halfThickness, y1 - halfThickness, halfThickness * 2.0F, halfThickness * 2.0F)
                    .color(color)
                    .draw();
            return;
        }
        var pose = event.getGuiGraphicsExtractor().pose();
        pose.pushMatrix();
        pose.translate(x1, y1);
        pose.rotate((float) Math.atan2(dy, dx));
        float overlap = halfThickness * 0.75F;
        Render2DUtil.rect(-overlap, -halfThickness, length + overlap * 2.0F, halfThickness * 2.0F)
                .color(color)
                .draw();
        pose.popMatrix();
    }

    private void addEntityHitBox(List<WorldMeshRenderer.Line> lines, List<WorldMeshRenderer.PlaneRect> planeRects, Entity entity) {
        AABB box = entity.getBoundingBox().inflate(ENTITY_HIT_BOX_EXPAND);
        Vec3 center = new Vec3((box.minX + box.maxX) * 0.5D, (box.minY + box.maxY) * 0.5D, (box.minZ + box.maxZ) * 0.5D);
        double hx = box.getXsize() * 0.5D;
        double hy = box.getYsize() * 0.5D;
        double hz = box.getZsize() * 0.5D;
        int fillColor = ColorUtil.applyAlpha(animatedAccentColor(22), ENTITY_HIT_BOX_FILL_ALPHA);

        planeRects.add(new WorldMeshRenderer.PlaneRect(new Vec3(box.minX, center.y, center.z), new Vec3(0.0D, 1.0D, 0.0D), new Vec3(0.0D, 0.0D, 1.0D), hy, hz, fillColor));
        planeRects.add(new WorldMeshRenderer.PlaneRect(new Vec3(box.maxX, center.y, center.z), new Vec3(0.0D, 1.0D, 0.0D), new Vec3(0.0D, 0.0D, 1.0D), hy, hz, fillColor));
        planeRects.add(new WorldMeshRenderer.PlaneRect(new Vec3(center.x, box.minY, center.z), new Vec3(1.0D, 0.0D, 0.0D), new Vec3(0.0D, 0.0D, 1.0D), hx, hz, fillColor));
        planeRects.add(new WorldMeshRenderer.PlaneRect(new Vec3(center.x, box.maxY, center.z), new Vec3(1.0D, 0.0D, 0.0D), new Vec3(0.0D, 0.0D, 1.0D), hx, hz, fillColor));
        planeRects.add(new WorldMeshRenderer.PlaneRect(new Vec3(center.x, center.y, box.minZ), new Vec3(1.0D, 0.0D, 0.0D), new Vec3(0.0D, 1.0D, 0.0D), hx, hy, fillColor));
        planeRects.add(new WorldMeshRenderer.PlaneRect(new Vec3(center.x, center.y, box.maxZ), new Vec3(1.0D, 0.0D, 0.0D), new Vec3(0.0D, 1.0D, 0.0D), hx, hy, fillColor));

        Vec3[] corners = {
                new Vec3(box.minX, box.minY, box.minZ),
                new Vec3(box.minX, box.minY, box.maxZ),
                new Vec3(box.minX, box.maxY, box.minZ),
                new Vec3(box.minX, box.maxY, box.maxZ),
                new Vec3(box.maxX, box.minY, box.minZ),
                new Vec3(box.maxX, box.minY, box.maxZ),
                new Vec3(box.maxX, box.maxY, box.minZ),
                new Vec3(box.maxX, box.maxY, box.maxZ)
        };
        int[][] edges = {
                {0, 1}, {0, 2}, {0, 4},
                {1, 3}, {1, 5},
                {2, 3}, {2, 6},
                {3, 7},
                {4, 5}, {4, 6},
                {5, 7},
                {6, 7}
        };

        for (int i = 0; i < edges.length; i++) {
            int edgeColor = ColorUtil.applyAlpha(animatedAccentColor(i * 11), ENTITY_HIT_BOX_ALPHA);
            lines.add(new WorldMeshRenderer.Line(corners[edges[i][0]], corners[edges[i][1]], edgeColor));
        }
    }

    private void addSplashExposureLines(List<WorldMeshRenderer.Line> lines, ThrownSplashPotion projectile, HitResult result, Vec3 areaCenter) {
        if (mc.level == null) {
            return;
        }

        Entity hitEntity = result instanceof EntityHitResult entityHit ? entityHit.getEntity() : null;
        AABB affectedBox = new AABB(
                areaCenter.x - SPLASH_POTION_RADIUS,
                areaCenter.y - SPLASH_ENTITY_VERTICAL_RANGE,
                areaCenter.z - SPLASH_POTION_RADIUS,
                areaCenter.x + SPLASH_POTION_RADIUS,
                areaCenter.y + SPLASH_ENTITY_VERTICAL_RANGE,
                areaCenter.z + SPLASH_POTION_RADIUS
        );

        for (LivingEntity entity : mc.level.getEntitiesOfClass(LivingEntity.class, affectedBox, candidate -> candidate.isAlive() && !candidate.isRemoved())) {
            if (entity == mc.player) {
                continue;
            }

            Vec3 targetPoint = new Vec3(entity.getX(), areaCenter.y, entity.getZ());
            Vec3 offset = targetPoint.subtract(areaCenter);
            Vec3 horizontal = new Vec3(offset.x, 0.0D, offset.z);
            double distance = Math.sqrt(horizontal.x * horizontal.x + horizontal.z * horizontal.z);
            if (distance > SPLASH_POTION_RADIUS || distance < SPLASH_MIN_DISTANCE) {
                continue;
            }

            double exposure = entity == hitEntity ? 1.0D : Mth.clamp(1.0D - distance / SPLASH_POTION_RADIUS, 0.0D, 1.0D);
            float alpha = (float) (SPLASH_LINE_MIN_ALPHA + (SPLASH_LINE_MAX_ALPHA - SPLASH_LINE_MIN_ALPHA) * exposure);
            lines.add(new WorldMeshRenderer.Line(
                    areaCenter,
                    targetPoint,
                    ColorUtil.applyAlpha(SPLASH_LINE_START_COLOR, alpha),
                    ColorUtil.applyAlpha(SPLASH_LINE_END_COLOR, alpha)
            ));
        }
    }

    private Double potionAreaRadius(Projectile projectile) {
        if (projectile instanceof ThrownSplashPotion) {
            return SPLASH_POTION_RADIUS;
        }
        if (projectile instanceof ThrownLingeringPotion) {
            return LINGERING_POTION_RADIUS;
        }
        return null;
    }

    private Vec3 resolvePotionAreaCenter(Projectile projectile, HitResult result) {
        if (mc.level == null) {
            return result.getLocation();
        }

        Vec3 start = result.getLocation().add(0.0D, POTION_GROUND_SEARCH_UP, 0.0D);
        Vec3 end = result.getLocation().add(0.0D, -POTION_GROUND_SEARCH_DOWN, 0.0D);
        HitResult groundHit = mc.level.clipIncludingBorder(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, projectile));
        return groundHit.getType() != HitResult.Type.MISS
                ? groundHit.getLocation().add(0.0D, POTION_AREA_Y_OFFSET, 0.0D)
                : result.getLocation();
    }

    private Direction getDirection(HitResult result) {
        if (result instanceof BlockHitResult blockHit) {
            return blockHit.getDirection();
        }
        if (mc.player == null) {
            return Direction.UP;
        }
        Vec3 vec = result.getLocation().subtract(mc.player.getEyePosition()).normalize();
        return Direction.getApproximateNearest((float) vec.x, (float) vec.y, (float) vec.z);
    }

    private boolean isStationary(Entity entity) {
        boolean posChange = entity.position().equals(entity.oldPosition());
        boolean itemEntityCheck = entity instanceof ItemEntity && (entity.onGround() || (mc.level != null && mc.level.getFluidState(entity.blockPosition()).is(FluidTags.WATER)));
        return posChange || itemEntityCheck;
    }

    private HitResult raycastBlock(Vec3 start, Vec3 end, Entity entity) {
        if (mc.level == null) {
            return BlockHitResult.miss(end, Direction.UP, BlockPos.containing(end));
        }
        return mc.level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
    }

    private void drawScreenLine(Render2DEvent event, float x1, float y1, float x2, float y2, float thickness, int color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 0.001F) {
            return;
        }
        var pose = event.getGuiGraphicsExtractor().pose();
        pose.pushMatrix();
        pose.translate(x1, y1);
        pose.rotate((float) Math.atan2(dy, dx));
        Render2DUtil.rect(0.0F, -thickness * 0.5F, length, thickness)
                .color(color)
                .draw();
        pose.popMatrix();
    }

    private String formatSeconds(int ticks) {
        int seconds = Math.max(0, Math.round(ticks / 20.0F));
        return seconds + "s";
    }

    private ScreenPoint projectToScreen(Vec3 pos) {
        Render3DUtil.ScreenPoint point = Render3DUtil.projectToScreen(mc, pos);
        return point == null ? null : new ScreenPoint(point.x(), point.y());
    }

    private void addPrediction(List<Prediction> predictions, Prediction prediction) {
        if (prediction != null) {
            predictions.add(prediction);
        }
    }

    private void clear() {
        tpSkipTicks = 0;
        pendingScreenLines.clear();
        points.clear();
    }

    private int animatedAccentColor(int index) {
        return ColorUtil.fade(8, index * 11, Theme.getAccent(), ColorUtil.rgb(101, 228, 255));
    }

    private record Prediction(Projectile projectile, HitResult result, List<WorldMeshRenderer.Line> lines, Vec3 screenConnectorEnd) {
    }

    private record ScreenLine(float x, float y, int color) {
    }

    private record ScreenPoint(float x, float y) {
    }

    private record ImpactPoint(ItemStack stack, Vec3 pos, int ticks, int entityAge, String ownerName, List<MobEffectInstance> effects) {
    }

    private record TrajectoryStep(Vec3 nextPos, Vec3 nextMotion, HitResult hitResult) {
    }

    private static final int MAX_SIMULATION_TICKS = 300;

    private static final float DESIGN_SCALE = 1.6F;
    private static final float SCREEN_CONNECTOR_THICKNESS = 1.35F;
    private static final float TAG_WIDTH = 36.0F;
    private static final float TAG_CARD = 36.0F;
    private static final float TAG_HEIGHT = 48.0F;
    private static final float TAG_GAP = 4.0F;
    private static final float TAG_RADIUS = 10.0F;
    private static final float TAG_BORDER = 1.2F;
    private static final float TAG_PADDING = 6.0F;
    private static final float TAG_ICON = 20.0F;
    private static final float TAG_TEXT_SIZE = 8.0F;
    private static final float TAG_TEXT_ROW = TAG_HEIGHT - TAG_CARD - TAG_GAP;
    private static final int IMPACT_RING_SEGMENTS = 180;
    private static final double IMPACT_RING_HALF_WIDTH = 0.004D;
    private static final double IMPACT_CROSS_RADIUS_FACTOR = 0.72D;
    private static final double IMPACT_CROSS_HALF_WIDTH = 0.003D;
    private static final int POTION_AREA_RING_SEGMENTS = 220;
    private static final double POTION_AREA_RING_HALF_WIDTH = 0.018D;
    private static final double POTION_AREA_Y_OFFSET = 0.012D;
    private static final double POTION_GROUND_SEARCH_UP = 0.35D;
    private static final double POTION_GROUND_SEARCH_DOWN = 2.5D;
    private static final double SPLASH_POTION_RADIUS = 4.0D;
    private static final double LINGERING_POTION_RADIUS = 3.0D;
    private static final double SPLASH_ENTITY_VERTICAL_RANGE = 2.0D;
    private static final double SPLASH_MIN_DISTANCE = 1.0E-3D;
    private static final double SPLASH_LINE_MIN_ALPHA = 0.38D;
    private static final double SPLASH_LINE_MAX_ALPHA = 0.92D;
    private static final float POTION_AREA_ALPHA = 0.55F;
    private static final float POTION_AREA_CONNECTOR_ALPHA = 0.67F;
    private static final double ENTITY_HIT_BOX_EXPAND = 0.028D;
    private static final float ENTITY_HIT_BOX_ALPHA = 0.94F;
    private static final float ENTITY_HIT_BOX_FILL_ALPHA = 0.09F;
    private static final int SPLASH_LINE_START_COLOR = 0xFF42FF85;
    private static final int SPLASH_LINE_END_COLOR = 0xFFFF5858;
    private static final double AIR_INERTIA = 0.99D;
    private static final double THROWABLE_WATER_INERTIA = 0.8D;
    private static final double ARROW_WATER_INERTIA = 0.6D;
    private static final double TRIDENT_WATER_INERTIA = 0.99D;
    private static final double THROWABLE_GRAVITY = 0.03D;
    private static final double POTION_GRAVITY = 0.05D;
    private static final double EXPERIENCE_BOTTLE_GRAVITY = 0.07D;
    private static final double ARROW_GRAVITY = 0.05D;
}
