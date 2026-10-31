package com.espmod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/** Fullbright via een onzichtbaar, alleen-client Night Vision effect. */
public final class Fullbright {
    private static boolean applied = false;

    private Fullbright() {}

    public static void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) {
            applied = false;
            return;
        }
        if (Config.get().fullbright) {
            MobEffectInstance current = player.getEffect(MobEffects.NIGHT_VISION);
            if (current == null || current.getDuration() < 2000) {
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 100000, 0, false, false, false));
                applied = true;
            }
        } else if (applied) {
            player.removeEffect(MobEffects.NIGHT_VISION);
            applied = false;
        }
    }
}
