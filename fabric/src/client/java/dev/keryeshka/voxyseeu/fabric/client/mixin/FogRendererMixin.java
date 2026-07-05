package dev.keryeshka.voxyseeu.fabric.client.mixin;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.keryeshka.voxyseeu.fabric.client.VoxySeeUClient;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FogRenderer.class)
abstract class FogRendererMixin {
    @Inject(method = "setupFog", at = @At("TAIL"))
    private static void seeu$disableVanillaFog(
            Camera camera,
            FogRenderer.FogMode fogMode,
            float farPlaneDistance,
            boolean isFoggy,
            float partialTick,
            CallbackInfo callback
    ) {
        if (!VoxySeeUClient.shouldDisableVanillaFog(camera)) {
            return;
        }
        RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        RenderSystem.setShaderFogEnd(Float.MAX_VALUE);
        RenderSystem.setShaderFogShape(FogShape.CYLINDER);
    }
}
