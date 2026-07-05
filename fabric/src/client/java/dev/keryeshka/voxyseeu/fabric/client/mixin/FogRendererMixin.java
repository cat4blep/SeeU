package dev.keryeshka.voxyseeu.fabric.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import dev.keryeshka.voxyseeu.fabric.client.VoxySeeUClient;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
abstract class FogRendererMixin {
    @Inject(method = "getBuffer", at = @At("HEAD"), cancellable = true)
    private void seeu$useNoFogBuffer(
            FogRenderer.FogMode fogMode,
            CallbackInfoReturnable<GpuBufferSlice> callback
    ) {
        if (fogMode == FogRenderer.FogMode.WORLD && VoxySeeUClient.shouldDisableVanillaFog()) {
            callback.setReturnValue(((FogRenderer) (Object) this).getBuffer(FogRenderer.FogMode.NONE));
        }
    }

    @Inject(
            method = "setupFog",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/fog/FogRenderer;updateBuffer(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V"
            )
    )
    private void seeu$disableVanillaFog(
            Camera camera,
            int renderDistance,
            DeltaTracker deltaTracker,
            float darkenWorldAmount,
            ClientLevel level,
            CallbackInfoReturnable<Vector4f> callback,
            @Local FogData fogData
    ) {
        if (!VoxySeeUClient.shouldDisableVanillaFog(camera)) {
            return;
        }
        fogData.environmentalStart = Float.MAX_VALUE;
        fogData.environmentalEnd = Float.MAX_VALUE;
        fogData.renderDistanceStart = Float.MAX_VALUE;
        fogData.renderDistanceEnd = Float.MAX_VALUE;
        fogData.skyEnd = Float.MAX_VALUE;
        fogData.cloudEnd = Float.MAX_VALUE;
    }
}
