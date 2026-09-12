package org.shizavisuals.mixin.render;

import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.shaders.ShaderType;
import net.minecraft.resources.Identifier;
import org.shizavisuals.utils.render.ShaderFallback;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(targets = "com.mojang.blaze3d.vulkan.VulkanDevice")
public class VulkanDeviceMixin {

    @ModifyVariable(method = "getOrCompileShader", at = @At("HEAD"), argsOnly = true)
    private ShaderSource wrapShaderSource(ShaderSource source, Identifier id, ShaderType type) {
        return (shaderId, shaderType) -> {
            String result = null;
            try {
                result = source.get(shaderId, shaderType);
            } catch (Throwable ignored) {}

            if (result == null) {
                result = ShaderFallback.load(shaderId, shaderType);
            }
            return result;
        };
    }
}
