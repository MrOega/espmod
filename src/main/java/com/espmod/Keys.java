package com.espmod;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Alle toetsen. Het zijn gewone Minecraft key mappings, dus ze staan ook in
 * Options > Controls en worden door Minecraft opgeslagen. Vanuit het ESP-menu
 * kun je ze veranderen of uitzetten (= geen toets).
 */
public final class Keys {
    public static KeyMapping menu, fullbright, block, player, entity, freecam;

    private Keys() {}

    public static void register() {
        KeyMapping.Category cat = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(EspMod.MOD_ID, "main"));
        menu = reg("menu", InputConstants.KEY_RSHIFT, cat);
        fullbright = reg("fullbright", InputConstants.KEY_G, cat);
        block = reg("block_esp", InputConstants.KEY_H, cat);
        player = reg("player_esp", InputConstants.KEY_J, cat);
        entity = reg("entity_esp", InputConstants.KEY_K, cat);
        freecam = reg("freecam", InputConstants.KEY_V, cat);
    }

    private static KeyMapping reg(String name, int key, KeyMapping.Category cat) {
        return KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key." + EspMod.MOD_ID + "." + name, InputConstants.Type.KEYSYM, key, cat));
    }

    public static void bind(KeyMapping km, int keyCode) {
        km.setKey(InputConstants.Type.KEYSYM.getOrCreate(keyCode));
        apply();
    }

    /** Geen toets meer: de hack kan alleen nog via het menu of /esp worden bediend. */
    public static void unbind(KeyMapping km) {
        km.setKey(InputConstants.UNKNOWN);
        apply();
    }

    private static void apply() {
        KeyMapping.resetMapping();
        Minecraft.getInstance().options.save();
    }
}
