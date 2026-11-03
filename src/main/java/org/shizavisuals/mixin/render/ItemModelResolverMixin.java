package org.shizavisuals.mixin.render;

import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.Level;
import org.shizavisuals.feature.impl.visual.ItemReplacerFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Swaps the sword model by writing CUSTOM_MODEL_DATA for the duration of model
 * resolution only. The original component is restored on RETURN, so the stack is
 * never actually modified: the custom texture disappears the moment the feature
 * is toggled off and nothing leaks into the saved item NBT.
 */
@Mixin(ItemModelResolver.class)
public abstract class ItemModelResolverMixin {
    private ItemStack shizavisuals$touchedStack;
    private CustomModelData shizavisuals$originalModelData;

    @Inject(method = "updateForTopItem", at = @At("HEAD"))
    private void shizavisuals$replaceItemModel(ItemStackRenderState renderState,
                                               ItemStack stack,
                                               ItemDisplayContext displayContext,
                                               Level level,
                                               ItemOwner itemOwner,
                                               int seed,
                                               CallbackInfo ci) {
        this.shizavisuals$touchedStack = null;
        this.shizavisuals$originalModelData = null;
        ItemReplacerFeature feature = ItemReplacerFeature.getEnabled();
        if (feature == null || stack.isEmpty() || !ItemReplacerFeature.isSword(stack)) {
            return;
        }
        if (!feature.shouldApply(itemOwner)) {
            return;
        }
        this.shizavisuals$touchedStack = stack;
        CustomModelData existing = stack.getComponents().get(DataComponents.CUSTOM_MODEL_DATA);
        // Older builds wrote the custom model data straight into the stack; treat
        // that leftover as "no data" so RETURN strips it instead of restoring it.
        this.shizavisuals$originalModelData = ItemReplacerFeature.isInjectedModelData(existing)
                ? null
                : existing;
        feature.apply(stack, level, itemOwner);
    }

    @Inject(method = "updateForTopItem", at = @At("RETURN"))
    private void shizavisuals$restoreItemModel(ItemStackRenderState renderState,
                                               ItemStack stack,
                                               ItemDisplayContext displayContext,
                                               Level level,
                                               ItemOwner itemOwner,
                                               int seed,
                                               CallbackInfo ci) {
        ItemStack touched = this.shizavisuals$touchedStack;
        CustomModelData original = this.shizavisuals$originalModelData;
        this.shizavisuals$touchedStack = null;
        this.shizavisuals$originalModelData = null;
        if (touched == null || touched != stack) {
            return;
        }
        if (original == null) {
            stack.remove(DataComponents.CUSTOM_MODEL_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_MODEL_DATA, original);
        }
    }
}
