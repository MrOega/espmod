package com.espmod;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Alle instellingen via /esp ... (client-side commando's).
 *
 *  /esp
 *  /esp fullbright
 *  /esp block|player|entity  toggle | range <n> | tracers | tracercolor <hex> | outlinecolor <hex>
 *  /esp block  add <id> | remove <id> | color <hex> <id> | list | clear | max <n>
 *  /esp entity add <id> | remove <id> | color <hex> <id> | list | clear
 *  /esp player health | armor
 *  /esp linewidth <0.005-0.2>
 */
public final class EspCommands {
    private EspCommands() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommandManager.literal("esp");

            root.executes(ctx -> {
                status(ctx.getSource());
                return 1;
            });

            root.then(ClientCommandManager.literal("fullbright").executes(ctx -> {
                Config c = Config.get();
                c.fullbright = !c.fullbright;
                Config.save();
                msg(ctx.getSource(), "Fullbright: " + onOff(c.fullbright));
                return 1;
            }));

            root.then(ClientCommandManager.literal("linewidth").then(
                    ClientCommandManager.argument("width", StringArgumentType.word()).executes(ctx -> {
                        String s = StringArgumentType.getString(ctx, "width");
                        try {
                            float w = Float.parseFloat(s);
                            if (w < 0.005f || w > 0.2f) throw new NumberFormatException();
                            Config.get().lineWidth = w;
                            Config.save();
                            msg(ctx.getSource(), "Lijndikte: " + w);
                        } catch (NumberFormatException e) {
                            msg(ctx.getSource(), "Geef een getal tussen 0.005 en 0.2 (bijv. 0.025)");
                        }
                        return 1;
                    })));

            root.then(ClientCommandManager.literal("freecam").executes(ctx -> {
                Freecam.toggle(net.minecraft.client.Minecraft.getInstance());
                return 1;
            }));

            // ---- block ----
            LiteralArgumentBuilder<FabricClientCommandSource> block =
                    common("block", () -> Config.get().blockEsp, 128);
            block.then(ClientCommandManager.literal("max").then(
                    ClientCommandManager.argument("n", IntegerArgumentType.integer(1, 10000)).executes(ctx -> {
                        Config.get().blockEsp.maxBlocks = IntegerArgumentType.getInteger(ctx, "n");
                        Config.save();
                        BlockScanner.invalidate();
                        msg(ctx.getSource(), "Max blocks: " + Config.get().blockEsp.maxBlocks);
                        return 1;
                    })));
            mapCommands(block, "block", () -> Config.get().blockEsp.blocks,
                    () -> Config.get().blockEsp.outlineColor, BuiltInRegistries.BLOCK, BlockScanner::invalidate);
            root.then(block);

            // ---- player ----
            LiteralArgumentBuilder<FabricClientCommandSource> player =
                    common("player", () -> Config.get().playerEsp, 256);
            player.then(ClientCommandManager.literal("health").executes(ctx -> {
                Config.PlayerEsp p = Config.get().playerEsp;
                p.showHealth = !p.showHealth;
                Config.save();
                msg(ctx.getSource(), "Player health: " + onOff(p.showHealth));
                return 1;
            }));
            player.then(ClientCommandManager.literal("armor").executes(ctx -> {
                Config.PlayerEsp p = Config.get().playerEsp;
                p.showArmor = !p.showArmor;
                Config.save();
                msg(ctx.getSource(), "Player armor: " + onOff(p.showArmor));
                return 1;
            }));
            root.then(player);

            // ---- entity ----
            LiteralArgumentBuilder<FabricClientCommandSource> entity =
                    common("entity", () -> Config.get().entityEsp, 256);
            mapCommands(entity, "entity", () -> Config.get().entityEsp.entities,
                    () -> Config.get().entityEsp.outlineColor, BuiltInRegistries.ENTITY_TYPE, () -> {});
            root.then(entity);

            dispatcher.register(root);
        });
    }

    // ---------------------------------------------------------------- gedeelde commando's

    private static LiteralArgumentBuilder<FabricClientCommandSource> common(
            String name, Supplier<Config.Esp> cfg, int maxRange) {
        LiteralArgumentBuilder<FabricClientCommandSource> n = ClientCommandManager.literal(name);

        n.executes(ctx -> {
            Config.Esp e = cfg.get();
            msg(ctx.getSource(), name + " ESP: " + onOff(e.enabled) + " | range " + e.range
                    + " | tracers " + onOff(e.tracers) + " | tracerkleur " + e.tracerColor
                    + " | outlinekleur " + e.outlineColor);
            return 1;
        });

        n.then(ClientCommandManager.literal("toggle").executes(ctx -> {
            Config.Esp e = cfg.get();
            e.enabled = !e.enabled;
            Config.save();
            BlockScanner.invalidate();
            msg(ctx.getSource(), name + " ESP: " + onOff(e.enabled));
            return 1;
        }));

        n.then(ClientCommandManager.literal("range").then(
                ClientCommandManager.argument("blocks", IntegerArgumentType.integer(1, maxRange)).executes(ctx -> {
                    Config.Esp e = cfg.get();
                    e.range = IntegerArgumentType.getInteger(ctx, "blocks");
                    Config.save();
                    BlockScanner.invalidate();
                    msg(ctx.getSource(), name + " range: " + e.range + " blokken");
                    return 1;
                })));

        n.then(ClientCommandManager.literal("tracers").executes(ctx -> {
            Config.Esp e = cfg.get();
            e.tracers = !e.tracers;
            Config.save();
            msg(ctx.getSource(), name + " tracers: " + onOff(e.tracers));
            return 1;
        }));

        n.then(ClientCommandManager.literal("tracercolor").then(
                ClientCommandManager.argument("hex", StringArgumentType.word()).executes(ctx -> {
                    String hex = StringArgumentType.getString(ctx, "hex");
                    if (!Config.isValidHex(hex)) {
                        msg(ctx.getSource(), "Gebruik een hex-kleur zoals FF0000 of #FF0000");
                        return 0;
                    }
                    cfg.get().tracerColor = Config.normalizeHex(hex);
                    Config.save();
                    msg(ctx.getSource(), name + " tracerkleur: " + cfg.get().tracerColor);
                    return 1;
                })));

        n.then(ClientCommandManager.literal("outlinecolor").then(
                ClientCommandManager.argument("hex", StringArgumentType.word()).executes(ctx -> {
                    String hex = StringArgumentType.getString(ctx, "hex");
                    if (!Config.isValidHex(hex)) {
                        msg(ctx.getSource(), "Gebruik een hex-kleur zoals FF0000 of #FF0000");
                        return 0;
                    }
                    cfg.get().outlineColor = Config.normalizeHex(hex);
                    Config.save();
                    BlockScanner.invalidate();
                    msg(ctx.getSource(), name + " outlinekleur: " + cfg.get().outlineColor);
                    return 1;
                })));

        return n;
    }

    /** add / remove / color / list / clear voor blocks en entities. */
    private static void mapCommands(LiteralArgumentBuilder<FabricClientCommandSource> n, String name,
                                    Supplier<Map<String, String>> map, Supplier<String> defaultColor,
                                    Registry<?> registry, Runnable onChange) {
        SuggestionProvider<FabricClientCommandSource> suggest = (ctx, builder) -> {
            String rem = builder.getRemainingLowerCase();
            for (Identifier id : registry.keySet()) {
                String s = id.toString();
                if (s.startsWith(rem) || id.getPath().startsWith(rem)) builder.suggest(s);
            }
            return builder.buildFuture();
        };

        RequiredArgumentBuilder<FabricClientCommandSource, String> idArg =
                ClientCommandManager.argument("id", StringArgumentType.greedyString());

        n.then(ClientCommandManager.literal("add").then(idArg.suggests(suggest).executes(ctx -> {
            Identifier id = parseId(StringArgumentType.getString(ctx, "id"), registry);
            if (id == null) {
                msg(ctx.getSource(), "Onbekend " + name + ": " + StringArgumentType.getString(ctx, "id"));
                return 0;
            }
            map.get().put(id.toString(), defaultColor.get());
            Config.save();
            onChange.run();
            msg(ctx.getSource(), "Toegevoegd: " + id);
            return 1;
        })));

        n.then(ClientCommandManager.literal("remove").then(
                ClientCommandManager.argument("id", StringArgumentType.greedyString()).suggests(suggest).executes(ctx -> {
                    Identifier id = parseId(StringArgumentType.getString(ctx, "id"), registry);
                    if (id == null || map.get().remove(id.toString()) == null) {
                        msg(ctx.getSource(), "Staat niet in de lijst: " + StringArgumentType.getString(ctx, "id"));
                        return 0;
                    }
                    Config.save();
                    onChange.run();
                    msg(ctx.getSource(), "Verwijderd: " + id);
                    return 1;
                })));

        n.then(ClientCommandManager.literal("color").then(
                ClientCommandManager.argument("hex", StringArgumentType.word()).then(
                        ClientCommandManager.argument("id", StringArgumentType.greedyString()).suggests(suggest)
                                .executes(ctx -> {
                                    String hex = StringArgumentType.getString(ctx, "hex");
                                    if (!Config.isValidHex(hex)) {
                                        msg(ctx.getSource(), "Gebruik een hex-kleur zoals FF0000");
                                        return 0;
                                    }
                                    Identifier id = parseId(StringArgumentType.getString(ctx, "id"), registry);
                                    if (id == null) {
                                        msg(ctx.getSource(), "Onbekend " + name + ": "
                                                + StringArgumentType.getString(ctx, "id"));
                                        return 0;
                                    }
                                    map.get().put(id.toString(), Config.normalizeHex(hex));
                                    Config.save();
                                    onChange.run();
                                    msg(ctx.getSource(), id + " kleur: " + Config.normalizeHex(hex));
                                    return 1;
                                }))));

        n.then(ClientCommandManager.literal("list").executes(ctx -> {
            if (map.get().isEmpty()) {
                msg(ctx.getSource(), "Lijst is leeg.");
            } else {
                map.get().forEach((k, v) -> msg(ctx.getSource(), k + "  " + v));
            }
            return 1;
        }));

        n.then(ClientCommandManager.literal("clear").executes(ctx -> {
            map.get().clear();
            Config.save();
            onChange.run();
            msg(ctx.getSource(), "Lijst geleegd.");
            return 1;
        }));
    }

    private static Identifier parseId(String raw, Registry<?> registry) {
        Identifier id = Identifier.tryParse(raw.trim());
        if (id == null || !registry.containsKey(id)) return null;
        return id;
    }

    private static void status(FabricClientCommandSource src) {
        Config c = Config.get();
        msg(src, "Fullbright: " + onOff(c.fullbright));
        msg(src, "Block ESP: " + onOff(c.blockEsp.enabled) + " (range " + c.blockEsp.range + ", "
                + c.blockEsp.blocks.size() + " blokken)");
        msg(src, "Player ESP: " + onOff(c.playerEsp.enabled) + " (range " + c.playerEsp.range + ")");
        msg(src, "Entity ESP: " + onOff(c.entityEsp.enabled) + " (range " + c.entityEsp.range + ", "
                + c.entityEsp.entities.size() + " types)");
        msg(src, "Subcommando's: fullbright, block, player, entity, linewidth");
    }

    private static void msg(FabricClientCommandSource src, String text) {
        src.sendFeedback(Component.literal(text));
    }

    private static String onOff(boolean b) {
        return b ? "AAN" : "UIT";
    }
}
