package com.espmod.render;

import com.espmod.BlockScanner;
import com.espmod.Config;
import com.espmod.Freecam;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Verzamelt alle ESP-vormen voor dit frame en tekent ze door muren heen. */
public final class EspRenderer {
    private static final int BAR_BACKGROUND = 0xFF202020;
    private static final EquipmentSlot[] ARMOR = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private EspRenderer() {}

    public static void render(WorldRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer self = mc.player;
        if (level == null || self == null) return;

        Config cfg = Config.get();
        boolean freecam = Freecam.isActive();
        if (!cfg.blockEsp.enabled && !cfg.playerEsp.enabled && !cfg.entityEsp.enabled && !freecam) return;

        Vec3 cam = context.worldState().cameraRenderState.pos;
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 tracerStart = cam.add(self.getViewVector(pt).scale(0.6));
        float w = cfg.lineWidth;

        Shapes.begin(context.matrices().last().pose(), cam);

        // In freecam: toon waar je eigen lichaam staat
        if (freecam) Shapes.box(interpolatedBox(self, pt), 0xFFFFFFFF, w);

        // ---------------- Block ESP ----------------
        if (cfg.blockEsp.enabled) {
            Config.BlockEsp b = cfg.blockEsp;
            for (BlockScanner.Found f : BlockScanner.results()) {
                AABB box = new AABB(f.x(), f.y(), f.z(), f.x() + 1, f.y() + 1, f.z() + 1).inflate(0.003);
                Shapes.box(box, f.color(), w);
                if (b.tracers) Shapes.line(tracerStart, box.getCenter(), b.tracerArgb, w * 0.6f);
            }
        }

        // ---------------- Player ESP ----------------
        if (cfg.playerEsp.enabled) {
            Config.PlayerEsp p = cfg.playerEsp;
            double r2 = (double) p.range * p.range;
            for (AbstractClientPlayer other : level.players()) {
                if (other == self || other.isRemoved()) continue;
                if (other.distanceToSqr(self) > r2) continue;
                AABB box = interpolatedBox(other, pt);
                Shapes.box(box, p.outlineArgb, w);
                if (p.tracers) Shapes.line(tracerStart, box.getCenter(), p.tracerArgb, w * 0.6f);
                if (p.showHealth || p.showArmor) drawPlayerInfo(other, box, cam, p);
            }
        }

        // ---------------- Entity ESP ----------------
        if (cfg.entityEsp.enabled && !cfg.entityColors.isEmpty()) {
            Config.EntityEsp e = cfg.entityEsp;
            double r2 = (double) e.range * e.range;
            for (Entity entity : level.entitiesForRendering()) {
                if (entity == self || entity instanceof Player || entity.isRemoved()) continue;
                Integer color = cfg.entityColors.get(entity.getType());
                if (color == null) continue;
                if (entity.distanceToSqr(self) > r2) continue;
                AABB box = interpolatedBox(entity, pt);
                Shapes.box(box, color, w);
                if (e.tracers) Shapes.line(tracerStart, box.getCenter(), e.tracerArgb, w * 0.6f);
            }
        }

        Shapes.finish(mc);
    }

    private static AABB interpolatedBox(Entity e, float pt) {
        Vec3 delta = e.getPosition(pt).subtract(e.position());
        return e.getBoundingBox().move(delta);
    }

    // ---------------------------------------------------------------- health + armor

    private static void drawPlayerInfo(Player player, AABB box, Vec3 cam, Config.PlayerEsp cfg) {
        double cx = box.getCenter().x, cz = box.getCenter().z;

        // richting camera -> speler (horizontaal), en daarvan de "rechts"-vector
        double vx = cx - cam.x, vz = cz - cam.z;
        double len = Math.sqrt(vx * vx + vz * vz);
        if (len < 1e-3) {
            vx = 0;
            vz = 1;
            len = 1;
        }
        vx /= len;
        vz /= len;
        double rx = -vz, rz = vx;

        double y0 = box.minY, y1 = box.maxY, h = y1 - y0;
        double side = (box.maxX - box.minX) / 2.0 + 0.15;

        // Health: rechts van de hitbox
        if (cfg.showHealth) {
            float max = Math.max(1f, player.getMaxHealth());
            float frac = Math.max(0f, Math.min(1f, player.getHealth() / max));
            bar(cx + rx * side, cz + rz * side, rx, rz, 0.04, y0, y1, frac, healthColor(frac));
        }

        // Armor durability: links van de hitbox, 4 smalle balken (helm, borstplaat, broek, schoenen)
        if (cfg.showArmor) {
            for (int i = 0; i < ARMOR.length; i++) {
                ItemStack stack = player.getItemBySlot(ARMOR[i]);
                double off = side + i * 0.075;
                double bx = cx - rx * off, bz = cz - rz * off;
                if (stack.isEmpty()) {
                    bar(bx, bz, rx, rz, 0.025, y0, y1, 0f, 0xFF000000);
                } else if (stack.isDamageableItem() && stack.getMaxDamage() > 0) {
                    float frac = 1f - (float) stack.getDamageValue() / (float) stack.getMaxDamage();
                    frac = Math.max(0f, Math.min(1f, frac));
                    bar(bx, bz, rx, rz, 0.025, y0, y1, frac, healthColor(frac));
                } else {
                    bar(bx, bz, rx, rz, 0.025, y0, y1, 1f, 0xFFB0B0B0);
                }
            }
        }
    }

    /** Verticale balk; gevuld deel onderaan in 'color', rest donker. */
    private static void bar(double cx, double cz, double rx, double rz, double halfW,
                            double y0, double y1, float frac, int color) {
        double yf = y0 + (y1 - y0) * frac;
        double ax = cx - rx * halfW, az = cz - rz * halfW;
        double bx = cx + rx * halfW, bz = cz + rz * halfW;
        if (frac > 0f) {
            Shapes.quad(ax, y0, az, bx, y0, bz, bx, yf, bz, ax, yf, az, color);
        }
        if (frac < 1f) {
            Shapes.quad(ax, yf, az, bx, yf, bz, bx, y1, bz, ax, y1, az, BAR_BACKGROUND);
        }
    }

    /** Groen (vol) -> geel -> rood (leeg). */
    private static int healthColor(float f) {
        int r, g;
        if (f > 0.5f) {
            r = (int) ((1f - f) * 2f * 255f);
            g = 255;
        } else {
            r = 255;
            g = (int) (f * 2f * 255f);
        }
        return 0xFF000000 | (r << 16) | (g << 8);
    }
}
