package org.shizavisuals.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import org.shizavisuals.feature.impl.visual.TargetEsp;
import org.shizavisuals.mixin.accessor.EquipmentLayerRendererAccessor;
import org.shizavisuals.utils.render.esp.TargetScanRenderer;
import org.shizavisuals.utils.render.esp.TargetScanState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(HumanoidArmorLayer.class)
public abstract class TargetScanArmorMixin<
        S extends HumanoidRenderState,
        M extends HumanoidModel<S>,
        A extends HumanoidModel<S>> {

    @Shadow
    private A getArmorModel(S state, EquipmentSlot slot) {
        throw new AssertionError();
    }

    @Shadow
    @Final
    private EquipmentLayerRenderer equipmentRenderer;

    @Inject(method = "renderArmorPiece", at = @At("TAIL"))
    private void shiza$submitTargetScanOnArmor(
            PoseStack poseStack,
            SubmitNodeCollector collector,
            ItemStack itemStack,
            EquipmentSlot slot,
            int lightCoords,
            S state,
            CallbackInfo ci) {
        if (!((TargetScanState) state).shiza$isTargetScanTarget()) {
            return;
        }

        Equippable equippable = itemStack.get(DataComponents.EQUIPPABLE);
        if (equippable == null || equippable.assetId().isEmpty()) {
            return;
        }
        EquipmentClientInfo.LayerType layerType = state.isBaby && state.entityType != EntityTypes.ARMOR_STAND
                ? EquipmentClientInfo.LayerType.HUMANOID_BABY
                : (slot == EquipmentSlot.LEGS
                        ? EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS
                        : EquipmentClientInfo.LayerType.HUMANOID);
        List<EquipmentClientInfo.Layer> layers = ((EquipmentLayerRendererAccessor) this.equipmentRenderer)
                .shiza$getEquipmentAssets()
                .get(equippable.assetId().orElseThrow())
                .getLayers(layerType);
        if (layers.isEmpty()) {
            return;
        }
        Identifier armorTexture = layers.getFirst().getTextureLocation(layerType);

        TargetEsp targetEsp = TargetEsp.getEnabled();
        if (targetEsp == null || !targetEsp.isScanMode()) {
            return;
        }

        TargetScanRenderer.submitArmor(
                this.getArmorModel(state, slot),
                state,
                poseStack,
                collector,
                armorTexture,
                slot,
                targetEsp.scanColor(),
                targetEsp.scanSecondColor(),
                targetEsp.scanSaturation(),
                targetEsp.scanAnimation(),
                targetEsp.scanSpeed(),
                targetEsp.scanGlow());
    }
}
