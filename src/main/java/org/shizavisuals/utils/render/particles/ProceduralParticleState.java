package org.shizavisuals.utils.render.particles;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.shizavisuals.utils.render.particles.ProceduralParticleRenderer.Shape;

import java.util.Random;

public final class ProceduralParticleState {
    private static final double PI = 3.141593430080217D;
    private static final double LIFT = 0.0030000000694913086D;
    private static final double DRIFT = 0.001999998851479194D;
    private static final double DRAG = 0.9599996591960513D;
    private static final double Y_SLEEP_THRESHOLD = 0.010000003725784143D;
    private static final double BOUNCE = 0.5500001584216989D;
    private static final double FLOOR_FRICTION = 0.75D;
    private static final double OTHER_AXIS_DAMPING = 0.8999997820741558D;
    private static final double COLLISION_EPSILON_POSITIVE = 9.99999563509513E-5D;
    private static final double COLLISION_EPSILON_NEGATIVE = -9.999997135847346E-5D;
    private static final double VELOCITY_EPSILON = 1.0000000000249859E-5D;
    private static final float ROTATION_DAMPING = 0.9850000143051147F;
    private static final float COLLISION_SPIN = 0.30000001192092896F;
    private static final float FADE_IN_END = 0.20000000298023224F;
    private static final float FADE_OUT_START = 0.800000011920929F;
    private static final double SPAWN_VELOCITY = 0.2000000000678856D;

    private double previousX;
    private double previousY;
    private double previousZ;
    private double x;
    private double y;
    private double z;
    private double velocityX;
    private double velocityY;
    private double velocityZ;
    private final int lifetime;
    private final Shape shape;
    private float age;
    private float rotation;
    private float rotationVelocity;
    private final float seed;
    private final float phase;

    public ProceduralParticleState(double x,
                                   double y,
                                   double z,
                                   double velocityX,
                                   double velocityY,
                                   double velocityZ,
                                   int lifetime,
                                   Shape shape,
                                   Random random) {
        this.previousX = this.x = x;
        this.previousY = this.y = y;
        this.previousZ = this.z = z;
        this.velocityX = velocityX;
        this.velocityY = velocityY;
        this.velocityZ = velocityZ;
        this.lifetime = lifetime;
        this.shape = shape;
        this.rotation = (float) (random.nextDouble() * PI * 2.0D);
        this.rotationVelocity = (float) (random.nextDouble() - 0.5D)
                * 0.20000000298023224F;
        this.seed = random.nextFloat();
        this.phase = random.nextFloat() * 100.0F;
    }

    public static Vec3 randomVelocity(Random random) {
        return new Vec3(
                (random.nextDouble() - 0.5D) * SPAWN_VELOCITY,
                (random.nextDouble() - 0.5D) * SPAWN_VELOCITY,
                (random.nextDouble() - 0.5D) * SPAWN_VELOCITY
        );
    }

    public static boolean isPositionBlocked(Level level, Vec3 position) {
        BlockPos blockPos = BlockPos.containing(position);
        var state = level.getBlockState(blockPos);
        return !state.isAir() && !state.getCollisionShape(level, blockPos).isEmpty();
    }

    public boolean update(Level level, float configuredSize, float delta, Random random) {
        this.age += delta;
        if (this.age >= this.lifetime) {
            return false;
        }

        this.previousX = this.x;
        this.previousY = this.y;
        this.previousZ = this.z;
        this.rotation += this.rotationVelocity * delta;
        this.rotationVelocity *= ROTATION_DAMPING;

        this.velocityX += (random.nextDouble() - 0.5D) * DRIFT * delta;
        this.velocityZ += (random.nextDouble() - 0.5D) * DRIFT * delta;
        double drag = Math.pow(DRAG, delta);
        this.velocityX *= drag;
        this.velocityY = this.velocityY * drag + LIFT * delta;
        this.velocityZ *= drag;
        if (Math.abs(this.velocityY) < Y_SLEEP_THRESHOLD) {
            this.velocityY = 0.0D;
        }

        double requestedX = this.velocityX * delta;
        double requestedY = this.velocityY * delta;
        double requestedZ = this.velocityZ * delta;
        AABB box = new AABB(
                this.x - configuredSize,
                this.y - configuredSize,
                this.z - configuredSize,
                this.x + configuredSize,
                this.y + configuredSize,
                this.z + configuredSize
        );

        double clippedY = clipAxis(level, box, requestedY, Direction.Axis.Y);
        if (clippedY != requestedY) {
            this.velocityY = -this.velocityY * BOUNCE;
            this.rotationVelocity = (float) (random.nextDouble() - 0.5D)
                    * COLLISION_SPIN;
            if (requestedY < 0.0D) {
                this.velocityX *= FLOOR_FRICTION;
                this.velocityZ *= FLOOR_FRICTION;
            }
            clippedY = signedCollisionEpsilon(clippedY, requestedY);
        }
        this.y += clippedY;
        box = box.move(0.0D, clippedY, 0.0D);

        double clippedX = clipAxis(level, box, requestedX, Direction.Axis.X);
        if (clippedX != requestedX) {
            this.velocityX = -this.velocityX * BOUNCE;
            this.velocityY *= OTHER_AXIS_DAMPING;
            this.velocityZ *= OTHER_AXIS_DAMPING;
            clippedX = signedCollisionEpsilon(clippedX, requestedX);
        }
        this.x += clippedX;
        box = box.move(clippedX, 0.0D, 0.0D);

        double clippedZ = clipAxis(level, box, requestedZ, Direction.Axis.Z);
        if (clippedZ != requestedZ) {
            this.velocityZ = -this.velocityZ * BOUNCE;
            this.velocityY *= OTHER_AXIS_DAMPING;
            this.velocityX *= OTHER_AXIS_DAMPING;
            clippedZ = signedCollisionEpsilon(clippedZ, requestedZ);
        }
        this.z += clippedZ;

        if (Math.abs(this.velocityX) < VELOCITY_EPSILON) {
            this.velocityX = 0.0D;
        }
        if (Math.abs(this.velocityY) < VELOCITY_EPSILON) {
            this.velocityY = 0.0D;
        }
        if (Math.abs(this.velocityZ) < VELOCITY_EPSILON) {
            this.velocityZ = 0.0D;
        }
        return true;
    }

    public Vec3 interpolatedPosition(float tickDelta) {
        return new Vec3(
                this.previousX + (this.x - this.previousX) * tickDelta,
                this.previousY + (this.y - this.previousY) * tickDelta,
                this.previousZ + (this.z - this.previousZ) * tickDelta
        );
    }

    public float renderRotation(float tickDelta) {
        return this.rotation + this.rotationVelocity * tickDelta;
    }

    public float normalizedLife() {
        return Math.clamp(this.age / this.lifetime, 0.0F, 1.0F);
    }

    public float opacityEnvelope() {
        float progress = normalizedLife();
        if (progress < FADE_IN_END) {
            return progress / FADE_IN_END;
        }
        if (progress > FADE_OUT_START) {
            return (1.0F - progress) / FADE_IN_END;
        }
        return 1.0F;
    }

    public Shape shape() {
        return this.shape;
    }

    public float seed() {
        return this.seed;
    }

    public float phase() {
        return this.phase;
    }

    private static double clipAxis(Level level,
                                   AABB box,
                                   double movement,
                                   Direction.Axis axis) {
        if (movement == 0.0D) {
            return 0.0D;
        }
        AABB expanded = switch (axis) {
            case X -> box.expandTowards(movement, 0.0D, 0.0D);
            case Y -> box.expandTowards(0.0D, movement, 0.0D);
            case Z -> box.expandTowards(0.0D, 0.0D, movement);
        };
        return Shapes.collide(
                axis,
                box,
                level.getBlockCollisions(null, expanded),
                movement
        );
    }

    private static double signedCollisionEpsilon(double clipped, double requested) {
        if (Math.abs(clipped) >= COLLISION_EPSILON_POSITIVE) {
            return clipped;
        }
        return requested < 0.0D
                ? COLLISION_EPSILON_POSITIVE
                : COLLISION_EPSILON_NEGATIVE;
    }
}
