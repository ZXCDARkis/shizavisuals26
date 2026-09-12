package org.shizavisuals.webpanel;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.dimaskama.mcef.api.MCEFBrowser;
import org.jetbrains.annotations.Nullable;

public class WebPanelInstance {

    public enum Part {
        PAGE, BAR, CLOSE, CORNER_BL, CORNER_BR, CORNER_TL, CORNER_TR;

        public boolean isCorner() {
            return this.ordinal() >= CORNER_BL.ordinal();
        }
    }

    public record Hit(Part part, double u, double v, double t) {
    }

    public enum Quality {
        LOW(520.0, 1280, 960, 900.0),
        MEDIUM(780.0, 1920, 1280, 1000.0),
        HIGH(1080.0, 2400, 1600, 1100.0),
        ULTRA(1440.0, 3072, 2048, 1200.0);

        final double pixelsPerBlock;
        final int maxWidth;
        final int maxHeight;
        final double cssReference;

        Quality(double pixelsPerBlock, int maxWidth, int maxHeight, double cssReference) {
            this.pixelsPerBlock = pixelsPerBlock;
            this.maxWidth = maxWidth;
            this.maxHeight = maxHeight;
            this.cssReference = cssReference;
        }
    }

    private static final double BAR_WIDTH = 0.155;
    private static final double BAR_HEIGHT = 0.0100;
    private static final double BAR_GAP = 0.0140;

    private static final double CLOSE_OFFSET = 3.0;

    private static final double CLOSE_RADIUS_DOT = 0.55;
    private static final double CLOSE_RADIUS_OPEN = 1.10;

    private static final double CORNER_THICKNESS = 0.55;

    private static final double CHROME_REFERENCE = 1.8;

    private static final double BAR_MAX_SHARE = 0.45;

    public static final float CORNER_RADIUS_SDF = 14.0f;

    private static final int TEX_MIN = 320;

    public static final float MAX_FREE_PITCH = 62.0f;

    public static final float MAX_PITCH = 90.0f;

    private static final double BEND_MAX_ANGLE = Math.toRadians(64.0);

    private static final float BEND_EPSILON = 0.004f;

    private static final float DORMANT_DELAY = 0.6f;

    private static volatile Quality quality = Quality.HIGH;

    private static volatile float bend;

    public static void setQuality(Quality value) {
        quality = value == null ? Quality.HIGH : value;
    }

    public static Quality getQuality() {
        return quality;
    }

    public static void setBend(float value) {
        bend = Mth.clamp(value, 0.0f, 1.0f);
    }

    public static double curveAcross(double radius, double u) {
        return radius <= 0.0 ? u : radius * Math.sin(u / radius);
    }

    public static double curveDepth(double radius, double u) {
        return radius <= 0.0 ? 0.0 : radius * (1.0 - Math.cos(u / radius));
    }

    private Vec3 origin;
    private Vec3 right;
    private Vec3 up;
    private Vec3 normal;
    private float yaw;
    private float pitch;

    private double width;
    private double height;
    private int texWidth;
    private int texHeight;
    private Quality appliedQuality;

    private final WebPanelTab page;

    private float appear;
    private boolean closing;

    private float hoverBar;
    private float hoverClose;
    private final float[] hoverCorner = new float[4];

    private boolean grabbed;
    private boolean chromeHidden;
    private float chrome = 1.0f;

    private double targetWidth;
    private double targetHeight;

    private boolean dormant;
    private float awayTime;

    private boolean remote;
    private String ownerKey = "";
    private String ownerName = "";
    private int remoteIndex;

    public WebPanelInstance(Vec3 center, float yawDegrees, float pitchDegrees,
                            double width, double height, String url) {
        this.width = width;
        this.height = height;
        this.targetWidth = width;
        this.targetHeight = height;
        this.page = new WebPanelTab(url);
        setOrientation(yawDegrees, pitchDegrees);
        this.origin = center.subtract(this.up.scale(height * 0.5));
        updateTextureSize();
    }

    public Vec3 getOrigin() {
        return this.origin;
    }

    public void setOrigin(Vec3 origin) {
        this.origin = origin;
    }

    public Vec3 getRight() {
        return this.right;
    }

    public Vec3 getUp() {
        return this.up;
    }

    public Vec3 getNormal() {
        return this.normal;
    }

    public float getYaw() {
        return this.yaw;
    }

    public float getPitch() {
        return this.pitch;
    }

    public void setOrientation(float yawDegrees, float pitchDegrees) {
        this.yaw = Mth.wrapDegrees(yawDegrees);
        this.pitch = Mth.clamp(pitchDegrees, -MAX_PITCH, MAX_PITCH);
        float yawRad = this.yaw * Mth.DEG_TO_RAD;
        float pitchRad = this.pitch * Mth.DEG_TO_RAD;
        float sinY = Mth.sin(yawRad);
        float cosY = Mth.cos(yawRad);
        float sinP = Mth.sin(pitchRad);
        float cosP = Mth.cos(pitchRad);
        this.normal = new Vec3(sinY * cosP, sinP, -cosY * cosP);
        this.right = new Vec3(-cosY, 0.0, -sinY);
        this.up = new Vec3(-sinP * sinY, cosP, sinP * cosY);
    }

    public double getWidth() {
        return this.width;
    }

    public double getHeight() {
        return this.height;
    }

    public void setTargetSize(double width, double height) {
        this.targetWidth = width;
        this.targetHeight = height;
    }

    public void snapSize() {
        this.width = this.targetWidth;
        this.height = this.targetHeight;
    }

    public void applyAspect() {
        updateTextureSize();
    }

    public double boundingRadius() {
        return 0.5 * Math.hypot(this.width, this.height);
    }

    public double bendRadius() {
        float amount = bend;
        if (amount <= BEND_EPSILON) {
            return 0.0;
        }
        return this.width / (BEND_MAX_ANGLE * amount);
    }

    public boolean isBent() {
        return bend > BEND_EPSILON;
    }

    private void updateTextureSize() {
        Quality q = quality;
        this.appliedQuality = q;
        double aspect = this.height > 1.0E-6 ? this.width / this.height : 1.0;
        if (aspect <= 1.0E-6) {
            aspect = 1.0;
        }
        int w = Mth.clamp((int) Math.round(this.width * q.pixelsPerBlock), 640, q.maxWidth);
        int h = (int) Math.round(w / aspect);
        if (h > q.maxHeight) {
            h = q.maxHeight;
            w = (int) Math.round(h * aspect);
        }
        if (h < TEX_MIN) {
            h = TEX_MIN;
            w = (int) Math.round(h * aspect);
        }
        this.texWidth = Mth.clamp(w & ~1, TEX_MIN, q.maxWidth);
        this.texHeight = Mth.clamp(h & ~1, TEX_MIN, q.maxHeight);
    }

    public int getTexWidth() {
        return this.texWidth;
    }

    public int getTexHeight() {
        return this.texHeight;
    }

    public double pageZoom() {
        double css = Mth.clamp(quality.cssReference * this.width / CHROME_REFERENCE,
                640.0, 1800.0);
        return Mth.clamp(this.texWidth / css, 0.5, 4.0);
    }

    public WebPanelTab page() {
        return this.page;
    }

    @Nullable
    public MCEFBrowser getBrowser() {
        return this.page.getBrowser();
    }

    public String getUrl() {
        return this.page.getUrl();
    }

    public float getContentAlpha() {
        return this.page.getContentAlpha();
    }

    public boolean hasContent() {
        return this.page.hasContent();
    }

    public void setFocused(boolean focused) {
        this.page.setFocused(focused);
    }

    public Vec3 getCenter() {
        return this.origin.add(this.up.scale(this.height * 0.5));
    }

    private double chromeScale() {
        return Math.sqrt(this.width * CHROME_REFERENCE);
    }

    public double barWidth() {
        return Math.min(chromeScale() * BAR_WIDTH, this.width * BAR_MAX_SHARE);
    }

    public double barHeight() {
        return chromeScale() * BAR_HEIGHT;
    }

    public double barCenterV() {
        return -(chromeScale() * BAR_GAP + barHeight() * 0.5);
    }

    public double closeCenterU() {
        return -barWidth() * 0.5 - barHeight() * CLOSE_OFFSET;
    }

    public double closeRadius() {
        return barHeight() * Mth.lerp(this.hoverClose, CLOSE_RADIUS_DOT, CLOSE_RADIUS_OPEN);
    }

    public double cornerThickness() {
        return barHeight() * CORNER_THICKNESS;
    }

    public double cornerRadius() {
        return Math.min(CORNER_RADIUS_SDF / 256.0 * this.height,
                Math.min(this.width, this.height) * 0.5);
    }

    public double[] cornerCenter(int index) {
        double r = cornerRadius();
        double u = (index == 0 || index == 2) ? -(this.width * 0.5 - r) : (this.width * 0.5 - r);
        double v = (index < 2) ? r : (this.height - r);
        return new double[]{u, v};
    }

    @Nullable
    private double[] intersectPlane(Vec3 eye, Vec3 direction, double maxDistance) {
        double denominator = direction.dot(this.normal);
        if (Math.abs(denominator) < 1.0E-6) {
            return null;
        }
        double t = this.origin.subtract(eye).dot(this.normal) / denominator;
        if (t < 0.0 || t > maxDistance) {
            return null;
        }
        Vec3 local = eye.add(direction.scale(t)).subtract(this.origin);
        return new double[]{local.dot(this.right), local.dot(this.up), t};
    }

    @Nullable
    private double[] intersectArc(Vec3 eye, Vec3 direction, double maxDistance, double radius) {
        Vec3 local = eye.subtract(this.origin);
        double oa = local.dot(this.right);
        double ob = local.dot(this.up);
        double oc = local.dot(this.normal);
        double da = direction.dot(this.right);
        double db = direction.dot(this.up);
        double dc = direction.dot(this.normal);

        double a = da * da + dc * dc;
        if (a < 1.0E-12) {
            return null;
        }
        double b = 2.0 * (oa * da + (oc - radius) * dc);
        double c = oa * oa + (oc - radius) * (oc - radius) - radius * radius;
        double discriminant = b * b - 4.0 * a * c;
        if (discriminant < 0.0) {
            return null;
        }
        double root = Math.sqrt(discriminant);
        for (int i = 0; i < 2; i++) {
            double t = ((i == 0 ? -root : root) - b) / (2.0 * a);
            if (t < 0.0 || t > maxDistance) {
                continue;
            }
            double depth = radius - (oc + t * dc);
            if (depth <= 1.0E-6) {
                continue;
            }
            return new double[]{radius * Math.atan2(oa + t * da, depth), ob + t * db, t};
        }
        return null;
    }

    @Nullable
    private double[] intersectSurface(Vec3 eye, Vec3 direction, double maxDistance) {
        double radius = bendRadius();
        return radius <= 0.0
                ? intersectPlane(eye, direction, maxDistance)
                : intersectArc(eye, direction, maxDistance, radius);
    }

    @Nullable
    public Hit hitTest(Vec3 eye, Vec3 direction, double maxDistance) {
        if (this.appear < 0.35f) {
            return null;
        }
        double[] surface = intersectSurface(eye, direction, maxDistance);
        if (surface == null) {
            return null;
        }
        double u = surface[0];
        double v = surface[1];
        double t = surface[2];
        double bar = barHeight();

        if (!this.remote) {
            double closeDU = u - closeCenterU();
            double closeDV = v - barCenterV();
            if (closeDU * closeDU + closeDV * closeDV <= (bar * 1.9) * (bar * 1.9)) {
                return new Hit(Part.CLOSE, u, v, t);
            }
            if (Math.abs(u) <= barWidth() * 0.5 + bar * 1.4
                    && Math.abs(v - barCenterV()) <= bar * 2.2) {
                return new Hit(Part.BAR, u, v, t);
            }

            double r = cornerRadius();
            if (r > 1.0E-4) {
                double outer = r + cornerThickness() * 4.0;
                for (int i = 0; i < 4; i++) {
                    double[] c = cornerCenter(i);
                    boolean left = (i == 0 || i == 2);
                    boolean bottom = i < 2;
                    if ((left ? u > c[0] : u < c[0]) || (bottom ? v > c[1] : v < c[1])) {
                        continue;
                    }
                    double d = Math.hypot(u - c[0], v - c[1]);
                    if (d >= r * 0.85 && d <= outer) {
                        return new Hit(Part.values()[Part.CORNER_BL.ordinal() + i], u, v, t);
                    }
                }
            }
        }

        double half = this.width * 0.5;
        if (u >= -half && u <= half && v >= 0.0 && v <= this.height) {
            return new Hit(Part.PAGE, u, v, t);
        }
        return null;
    }

    public int[] toPixels(double u, double v) {
        int px = (int) Mth.clamp((u + this.width * 0.5) / this.width * this.texWidth,
                0.0, this.texWidth - 1.0);
        int py = (int) Mth.clamp((1.0 - v / this.height) * this.texHeight,
                0.0, this.texHeight - 1.0);
        return new int[]{px, py};
    }

    public void animate(float deltaSeconds, @Nullable Part hovered) {
        float sizeSmooth = 1.0f - (float) Math.exp(-deltaSeconds / 0.055f);
        this.width += (this.targetWidth - this.width) * sizeSmooth;
        this.height += (this.targetHeight - this.height) * sizeSmooth;

        if (this.appliedQuality != quality) {
            updateTextureSize();
        }

        this.chrome = approach(this.chrome, this.chromeHidden ? 0.0f : 1.0f,
                deltaSeconds / (this.chromeHidden ? 0.09f : 0.24f));

        this.appear = approach(this.appear, this.closing ? 0.0f : 1.0f,
                deltaSeconds / (this.closing ? 0.13f : 0.18f));

        boolean barLit = hovered == Part.BAR;
        this.hoverBar = approach(this.hoverBar, barLit ? 1.0f : 0.0f, deltaSeconds / 0.10f);
        this.hoverClose = approach(this.hoverClose, hovered == Part.CLOSE ? 1.0f : 0.0f,
                deltaSeconds / 0.11f);
        for (int i = 0; i < 4; i++) {
            boolean on = hovered != null && hovered.isCorner()
                    && hovered.ordinal() - Part.CORNER_BL.ordinal() == i;
            this.hoverCorner[i] = approach(this.hoverCorner[i], on ? 1.0f : 0.0f, deltaSeconds / 0.12f);
        }
    }

    private static float approach(float current, float target, float step) {
        return target > current ? Math.min(target, current + step) : Math.max(target, current - step);
    }

    private float easedAppear() {
        float t = Mth.clamp(this.appear, 0.0f, 1.0f);
        float inv = 1.0f - t;
        return 1.0f - inv * inv * inv;
    }

    public float getRenderScale() {
        return 0.87f + 0.13f * easedAppear();
    }

    public float getRenderAlpha() {
        return easedAppear();
    }

    public float getHoverBar() {
        return this.hoverBar;
    }

    public float getHoverClose() {
        return this.hoverClose;
    }

    public float getHoverCorner(int index) {
        return this.hoverCorner[index];
    }

    public void setGrabbed(boolean grabbed) {
        this.grabbed = grabbed;
    }

    public boolean isGrabbed() {
        return this.grabbed;
    }

    public void setChromeHidden(boolean hidden) {
        this.chromeHidden = hidden;
    }

    public float getChrome() {
        return this.chrome;
    }

    public float getChromeEased() {
        float inv = 1.0f - Mth.clamp(this.chrome, 0.0f, 1.0f);
        return 1.0f - inv * inv * inv;
    }

    public void requestClose() {
        this.closing = true;
    }

    public boolean isClosing() {
        return this.closing;
    }

    public boolean isDead() {
        return this.closing && this.appear <= 0.001f;
    }

    public void updateDormancy(boolean visible, float deltaSeconds) {
        if (visible) {
            this.awayTime = 0.0f;
            this.dormant = false;
            return;
        }
        this.awayTime += deltaSeconds;
        if (this.awayTime >= DORMANT_DELAY) {
            this.dormant = true;
        }
    }

    public boolean isDormant() {
        return this.dormant;
    }

    public boolean isRemote() {
        return this.remote;
    }

    public void markRemote(String ownerKey, String ownerName, int index) {
        this.remote = true;
        this.ownerKey = ownerKey == null ? "" : ownerKey;
        this.ownerName = ownerName == null ? "" : ownerName;
        this.remoteIndex = index;
    }

    public String getOwnerKey() {
        return this.ownerKey;
    }

    public String getOwnerName() {
        return this.ownerName;
    }

    public int getRemoteIndex() {
        return this.remoteIndex;
    }

    public void tickBrowser(float deltaSeconds, float attention) {
        if (this.remote) {
            return;
        }
        this.page.setHidden(this.dormant);
        this.page.setFrameRate(attention >= 0.99f ? 60
                : attention >= 0.5f ? 30
                : attention > 0.0f ? 15 : 8);
        this.page.tick(deltaSeconds, this.texWidth, this.texHeight, pageZoom());
    }

    public void close() {
        this.page.close();
    }
}
