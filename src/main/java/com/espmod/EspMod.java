package com.espmod;

import com.espmod.render.EspRenderer;
import com.espmod.render.Shapes;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EspMod implements ClientModInitializer {
    public static final String MOD_ID = "espmod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        Config.load();
        Shapes.init();
        Keys.register();
        EspCommands.register();

        ClientTickEvents.END_CLIENT_TICK.register(EspMod::onTick);
        WorldRenderEvents.BEFORE_TRANSLUCENT.register(EspRenderer::render);
    }

    private static void onTick(Minecraft mc) {
        Config cfg = Config.get();

        while (Keys.menu.consumeClick()) {
            if (mc.screen == null) mc.setScreen(new MenuScreen());
        }
        while (Keys.fullbright.consumeClick()) {
            cfg.fullbright = !cfg.fullbright;
            Config.save();
            notify(mc, "Fullbright", cfg.fullbright);
        }
        while (Keys.block.consumeClick()) {
            cfg.blockEsp.enabled = !cfg.blockEsp.enabled;
            Config.save();
            BlockScanner.invalidate();
            notify(mc, "Block ESP", cfg.blockEsp.enabled);
        }
        while (Keys.player.consumeClick()) {
            cfg.playerEsp.enabled = !cfg.playerEsp.enabled;
            Config.save();
            notify(mc, "Player ESP", cfg.playerEsp.enabled);
        }
        while (Keys.entity.consumeClick()) {
            cfg.entityEsp.enabled = !cfg.entityEsp.enabled;
            Config.save();
            notify(mc, "Entity ESP", cfg.entityEsp.enabled);
        }
        while (Keys.freecam.consumeClick()) {
            Freecam.toggle(mc);
        }

        Freecam.tick(mc);
        Fullbright.tick(mc);
        BlockScanner.tick(mc);
    }

    private static void notify(Minecraft mc, String name, boolean on) {
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(name + ": " + (on ? "AAN" : "UIT")), true);
        }
    }
}
