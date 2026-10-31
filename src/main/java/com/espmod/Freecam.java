package com.espmod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Freecam: de camera vliegt los van je speler.
 * - Je echte speler blijft staan (zijn input wordt tijdelijk vervangen door een lege input).
 * - Muisbewegingen draaien nog wel je speler; bij uitzetten wordt de oude kijkrichting hersteld.
 * - De camerapositie wordt in CameraMixin overschreven.
 */
public final class Freecam {
    private static boolean active = false;
    private static Vec3 pos = Vec3.ZERO, prev = Vec3.ZERO;
    private static float savedYaw, savedPitch;
    private static ClientInput savedInput;

    private Freecam() {}

    public static boolean isActive() {
        return active;
    }

    public static Vec3 position(float partialTick) {
        return prev.lerp(pos, partialTick);
    }

    public static void toggle(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null) return;
        if (!active) {
            pos = prev = p.getEyePosition();
            savedYaw = p.getYRot();
            savedPitch = p.getXRot();
            savedInput = p.input;
            p.input = new ClientInput() {};
            active = true;
        } else {
            disable(p);
        }
        p.displayClientMessage(Component.literal("Freecam: " + (active ? "AAN" : "UIT")), true);
    }

    private static void disable(LocalPlayer p) {
        if (savedInput != null) p.input = savedInput;
        p.setYRot(savedYaw);
        p.setXRot(savedPitch);
        savedInput = null;
        active = false;
    }

    public static void tick(Minecraft mc) {
        if (!active) return;
        LocalPlayer p = mc.player;
        if (p == null) {
            active = false;
            savedInput = null;
            return;
        }
        prev = pos;
        Options o = mc.options;
        double fwd = (o.keyUp.isDown() ? 1 : 0) - (o.keyDown.isDown() ? 1 : 0);
        double strafe = (o.keyLeft.isDown() ? 1 : 0) - (o.keyRight.isDown() ? 1 : 0);
        double vert = (o.keyJump.isDown() ? 1 : 0) - (o.keyShift.isDown() ? 1 : 0);

        float yaw = p.getYRot() * Mth.DEG_TO_RAD;
        Vec3 look = p.getViewVector(1.0f);
        Vec3 left = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
        Vec3 move = look.scale(fwd).add(left.scale(strafe)).add(0, vert, 0);
        if (move.lengthSqr() > 1.0E-6) {
            move = move.normalize().scale(Config.get().freecamSpeed);
            pos = pos.add(move);
        }
    }
}
