package org.shizavisuals.utils.render.chams;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import org.shizavisuals.utils.render.post.PostPipelines;

import java.util.HashMap;
import java.util.Map;

public final class PopChamsRenderTypes {
    private static final Map<Key, RenderType> CACHE = new HashMap<>();

    private PopChamsRenderTypes() {
    }

    public static RenderType model(Identifier texture, boolean textured, boolean additive) {
        Kind kind;
        if (additive) {
            kind = textured ? Kind.ADDITIVE : Kind.ADDITIVE_SOLID;
        } else {
            kind = textured ? Kind.TRANSLUCENT : Kind.TRANSLUCENT_SOLID;
        }
        return get(texture, kind);
    }

    public static RenderType mask(Identifier texture, boolean textured) {
        return get(texture, textured ? Kind.MASK : Kind.MASK_SOLID);
    }

    private static RenderType get(Identifier texture, Kind kind) {
        return CACHE.computeIfAbsent(new Key(texture, kind), key -> {
            RenderSetup setup = RenderSetup.builder(kind.pipeline)
                    .withTexture("Sampler0", texture)
                    .createRenderSetup();
            return RenderType.create("shizavisuals_popchams_" + kind.name().toLowerCase(), setup);
        });
    }

    private record Key(Identifier texture, Kind kind) {
    }

    private enum Kind {
        ADDITIVE(PostPipelines.POPCHAMS_ADDITIVE),
        ADDITIVE_SOLID(PostPipelines.POPCHAMS_ADDITIVE_SOLID),
        TRANSLUCENT(PostPipelines.POPCHAMS_TRANSLUCENT),
        TRANSLUCENT_SOLID(PostPipelines.POPCHAMS_TRANSLUCENT_SOLID),
        MASK(PostPipelines.POPCHAMS_MASK),
        MASK_SOLID(PostPipelines.POPCHAMS_MASK_SOLID);

        private final RenderPipeline pipeline;

        Kind(RenderPipeline pipeline) {
            this.pipeline = pipeline;
        }
    }
}
