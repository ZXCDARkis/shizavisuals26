package org.shizavisuals.hud.island;

public interface IslandEasing {
    IslandEasing LINEAR = progress -> progress;
    IslandEasing BAKEK = bezier(0.45D, 1.45D, 0.49D, 1.15D);
    IslandEasing BAKEK_SIZE = bezier(0.27D, 1.09D, 0.49D, 1.06D);
    IslandEasing BAKEK_BACK = bezier(0.34D, 1.56D, 0.64D, 1.0D);
    IslandEasing BAKEK_SMALLER = bezier(0.25D, 1.0D, 0.5D, 1.0D);
    IslandEasing FIGMA_EASE_IN_OUT = bezier(0.42D, 0.0D, 0.58D, 1.0D);
    IslandEasing CUBIC_IN_OUT = progress -> progress < 0.5F
            ? 4.0F * progress * progress * progress
            : 1.0F - (float) Math.pow(-2.0F * progress + 2.0F, 3.0D) / 2.0F;

    float ease(float progress);

    static IslandEasing bezier(final double x1, final double y1, final double x2, final double y2) {
        return new IslandEasing() {
            @Override
            public float ease(float progress) {
                if (progress <= 0.0F) {
                    return 0.0F;
                }
                if (progress >= 1.0F) {
                    return 1.0F;
                }
                float t = solveT(progress);
                return bezierY(t);
            }

            private float solveT(float progress) {
                float t = progress;
                for (int i = 0; i < 8; i++) {
                    float x = bezierX(t);
                    float dx = bezierDX(t);
                    if (Math.abs(x - progress) < 1.0E-5F || Math.abs(dx) < 1.0E-6F) {
                        break;
                    }
                    t -= (x - progress) / dx;
                    t = Math.max(0.0F, Math.min(1.0F, t));
                }
                return t;
            }

            private float bezierX(float t) {
                return 3.0F * (1.0F - t) * (1.0F - t) * t * (float) x1
                        + 3.0F * (1.0F - t) * t * t * (float) x2
                        + t * t * t;
            }

            private float bezierDX(float t) {
                return 3.0F * ((1.0F - t) * (1.0F - 3.0F * t) * (float) x1
                        + (2.0F * t - 3.0F * t * t) * (float) x2)
                        + 3.0F * t * t;
            }

            private float bezierY(float t) {
                return 3.0F * (1.0F - t) * (1.0F - t) * t * (float) y1
                        + 3.0F * (1.0F - t) * t * t * (float) y2
                        + t * t * t;
            }
        };
    }
}
