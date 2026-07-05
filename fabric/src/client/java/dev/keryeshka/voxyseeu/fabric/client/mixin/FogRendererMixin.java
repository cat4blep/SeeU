package dev.keryeshka.voxyseeu.fabric.client.mixin;

import dev.keryeshka.voxyseeu.fabric.client.VoxySeeUClient;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
abstract class FogRendererMixin {
    @Inject(method = "setupFog", at = @At("RETURN"))
    private void seeu$disableVanillaFog(
            Camera camera,
            int renderDistance,
            DeltaTracker deltaTracker,
            float darkenWorldAmount,
            ClientLevel level,
            CallbackInfoReturnable<FogData> callback
    ) {
        if (!VoxySeeUClient.shouldDisableVanillaFog(camera)) {
            return;
        }
        FogData fogData = callback.getReturnValue();
        fogData.environmentalStart = Float.MAX_VALUE;
        fogData.environmentalEnd = Float.MAX_VALUE;
        fogData.renderDistanceStart = Float.MAX_VALUE;
        fogData.renderDistanceEnd = Float.MAX_VALUE;
        fogData.skyEnd = Float.MAX_VALUE;
        fogData.cloudEnd = Float.MAX_VALUE;
    }
}
