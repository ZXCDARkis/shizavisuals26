package org.shizavisuals.utils.render.post;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.TextureTarget;

public final class PostTarget {
    private final String label;
    private final GpuFormat format;
    private final boolean useDepth;
    private TextureTarget target;

    public PostTarget(String label, GpuFormat format, boolean useDepth) {
        this.label = label;
        this.format = format;
        this.useDepth = useDepth;
    }

    public TextureTarget ensure(int width, int height) {
        if (this.target == null || this.target.width != width || this.target.height != height) {
            if (this.target != null) {
                this.target.destroyBuffers();
            }
            this.target = new TextureTarget(this.label, Math.max(1, width), Math.max(1, height), this.useDepth, this.format);
        }
        return this.target;
    }

    public TextureTarget get() {
        return this.target;
    }

    public void release() {
        if (this.target != null) {
            this.target.destroyBuffers();
            this.target = null;
        }
    }
}
