package com.espmod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Instellingen, opgeslagen in .minecraft/config/espmod.json */
public class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("espmod.json");
    private static Config instance = new Config();

    public boolean fullbright = false;
    /** Halve breedte van lijnen/outlines in blokken. */
    public float lineWidth = 0.025f;
    /** Freecam snelheid in blokken per tick. */
    public float freecamSpeed = 1.0f;

    public BlockEsp blockEsp = new BlockEsp();
    public PlayerEsp playerEsp = new PlayerEsp();
    public EntityEsp entityEsp = new EntityEsp();

    public static class Esp {
        public boolean enabled = false;
        public int range = 64;
        public boolean tracers = false;
        public String tracerColor = "#FFFFFF";
        /** Standaard outline-kleur (voor blocks/entities zonder eigen kleur). */
        public String outlineColor = "#00FFFF";

        public transient int tracerArgb = 0xFFFFFFFF;
        public transient int outlineArgb = 0xFF00FFFF;
    }

    public static class BlockEsp extends Esp {
        public int maxBlocks = 1500;
        /** block-id -> kleur */
        public Map<String, String> blocks = new LinkedHashMap<>();

        public BlockEsp() {
            range = 32;
            blocks.put("minecraft:diamond_ore", "#00FFFF");
            blocks.put("minecraft:deepslate_diamond_ore", "#00FFFF");
            blocks.put("minecraft:ancient_debris", "#B5651D");
        }
    }

    public static class PlayerEsp extends Esp {
        public boolean showHealth = true;
        public boolean showArmor = true;

        public PlayerEsp() {
            range = 128;
            outlineColor = "#FF3030";
        }
    }

    public static class EntityEsp extends Esp {
        /** entity-id -> kleur */
        public Map<String, String> entities = new LinkedHashMap<>();

        public EntityEsp() {
            range = 64;
            outlineColor = "#FFD400";
            entities.put("minecraft:creeper", "#00FF00");
            entities.put("minecraft:enderman", "#AA00FF");
        }
    }

    // Caches (niet opgeslagen)
    public transient Map<Block, Integer> blockColors = new HashMap<>();
    public transient Map<EntityType<?>, Integer> entityColors = new HashMap<>();

    public static Config get() {
        return instance;
    }

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                try (Reader r = Files.newBufferedReader(FILE)) {
                    Config c = GSON.fromJson(r, Config.class);
                    if (c != null) instance = c;
                }
            }
        } catch (Exception e) {
            EspMod.LOGGER.error("Kon config niet laden, defaults gebruikt", e);
        }
        save();
    }

    public static void save() {
        instance.rebuild();
        try {
            Files.writeString(FILE, GSON.toJson(instance));
        } catch (IOException e) {
            EspMod.LOGGER.error("Kon config niet opslaan", e);
        }
    }

    private void rebuild() {
        blockEsp.tracerArgb = parseColor(blockEsp.tracerColor, 0xFFFFFFFF);
        blockEsp.outlineArgb = parseColor(blockEsp.outlineColor, 0xFF00FFFF);
        playerEsp.tracerArgb = parseColor(playerEsp.tracerColor, 0xFFFFFFFF);
        playerEsp.outlineArgb = parseColor(playerEsp.outlineColor, 0xFFFF3030);
        entityEsp.tracerArgb = parseColor(entityEsp.tracerColor, 0xFFFFFFFF);
        entityEsp.outlineArgb = parseColor(entityEsp.outlineColor, 0xFFFFD400);

        blockColors = new HashMap<>();
        for (Map.Entry<String, String> e : blockEsp.blocks.entrySet()) {
            Identifier id = Identifier.tryParse(e.getKey());
            if (id == null) continue;
            int color = parseColor(e.getValue(), blockEsp.outlineArgb);
            BuiltInRegistries.BLOCK.getOptional(id).ifPresent(b -> blockColors.put(b, color));
        }

        entityColors = new HashMap<>();
        for (Map.Entry<String, String> e : entityEsp.entities.entrySet()) {
            Identifier id = Identifier.tryParse(e.getKey());
            if (id == null) continue;
            int color = parseColor(e.getValue(), entityEsp.outlineArgb);
            BuiltInRegistries.ENTITY_TYPE.getOptional(id).ifPresent(t -> entityColors.put(t, color));
        }
    }

    // ---- kleur helpers ----

    public static boolean isValidHex(String s) {
        return s != null && s.matches("#?[0-9a-fA-F]{6}");
    }

    public static String normalizeHex(String s) {
        return "#" + (s.startsWith("#") ? s.substring(1) : s).toUpperCase();
    }

    /** "#RRGGBB" of "#AARRGGBB" -> ARGB int. */
    public static int parseColor(String s, int fallback) {
        if (s == null) return fallback;
        String h = s.startsWith("#") ? s.substring(1) : s;
        try {
            if (h.length() == 6) return 0xFF000000 | (int) Long.parseLong(h, 16);
            if (h.length() == 8) return (int) Long.parseLong(h, 16);
        } catch (NumberFormatException ignored) {
        }
        return fallback;
    }
}
