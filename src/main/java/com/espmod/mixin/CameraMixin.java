package com.espmod.mixin;

import com.espmod.Freecam;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Inject(method = "setup", at = @At("RETURN"), require = 0)
    private void espmod$freecam(CallbackInfo ci) {
        if (Freecam.isActive()) {
            float pt = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
            Vec3 p = Freecam.position(pt);
            setPosition(p.x, p.y, p.z);
        }
    }
}
