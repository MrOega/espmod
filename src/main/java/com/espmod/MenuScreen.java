package com.espmod;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.Supplier;

/** Het ESP-menu (standaard Right Shift). */
public class MenuScreen extends Screen {
    private static final String[] TABS = {"General", "Block", "Player", "Entity", "Freecam"};
    private static final String[] COLOR_NAMES = {"White", "Red", "Orange", "Yellow", "Green", "Cyan", "Blue", "Purple", "Pink", "Black"};
    private static final String[] COLOR_HEX = {"#FFFFFF", "#FF3030", "#FF8800", "#FFD400", "#00FF00", "#00FFFF", "#3060FF", "#AA00FF", "#FF55FF", "#000000"};

    private static int page = 0;
    private static String lastId = "";
    private static String newColor = "#00FFFF";

    private record Swatch(Button button, Supplier<String> hex) {}

    private final List<Swatch> swatches = new ArrayList<>();
    private KeyMapping listening;
    private boolean dirty;
    private String status = "";
    private EditBox idBox;
    private Map<String, String> listMap;
    private int listY;

    public MenuScreen() {
        super(Component.literal("ESP Menu"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        if (dirty) Config.save();
    }

    // ------------------------------------------------------------------ layout

    @Override
    protected void init() {
        swatches.clear();
        listMap = null;
        Config cfg = Config.get();
        int cx = width / 2;
        int L = cx - 140;

        int tw = 56;
        int tx = cx - (TABS.length * (tw + 2)) / 2;
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            Button b = Button.builder(Component.literal(TABS[i]), bb -> {
                page = idx;
                listening = null;
                status = "";
                rebuildWidgets();
            }).bounds(tx + i * (tw + 2), 20, tw, 20).build();
            b.active = (i != page);
            addRenderableWidget(b);
        }

        int y = 48;
        switch (page) {
            case 0 -> {
                Button[] ref = new Button[1];
                ref[0] = btn(L, y, 270, keyLabel("Menu key: ", Keys.menu), () -> {
                    listening = Keys.menu;
                    rebuildWidgets();
                });
                y += 24;
                addRenderableWidget(new ValueSlider(L, y, 270, "Line width", 0.005, 0.1, cfg.lineWidth, false,
                        v -> {
                            cfg.lineWidth = (float) v;
                            dirty = true;
                        }));
                y += 24;
                toggleRow(L, y, "Fullbright", () -> cfg.fullbright, () -> {
                    cfg.fullbright = !cfg.fullbright;
                    Config.save();
                }, Keys.fullbright);
            }
            case 1 -> {
                Config.BlockEsp e = cfg.blockEsp;
                toggleRow(L, y, "Block ESP", () -> e.enabled, () -> {
                    e.enabled = !e.enabled;
                    Config.save();
                    BlockScanner.invalidate();
                }, Keys.block);
                y += 24;
                rangeSlider(L, y, e, 128);
                y += 24;
                tracerRow(L, y, e);
                y += 24;
                colorButton(L, y, 130, "Outline", () -> e.outlineColor, v -> {
                    e.outlineColor = v;
                    Config.save();
                });
                colorButton(L + 135, y, 135, "New block", () -> newColor, v -> newColor = v);
                y += 24;
                listRow(L, y, e.blocks, BuiltInRegistries.BLOCK, BlockScanner::invalidate, "block id (diamond_ore)");
                y += 24;
                listMap = e.blocks;
                listY = y + 12;
            }
            case 2 -> {
                Config.PlayerEsp e = cfg.playerEsp;
                toggleRow(L, y, "Player ESP", () -> e.enabled, () -> {
                    e.enabled = !e.enabled;
                    Config.save();
                }, Keys.player);
                y += 24;
                rangeSlider(L, y, e, 256);
                y += 24;
                tracerRow(L, y, e);
                y += 24;
                colorButton(L, y, 130, "Outline", () -> e.outlineColor, v -> {
                    e.outlineColor = v;
                    Config.save();
                });
                Button[] h = new Button[1];
                h[0] = btn(L + 135, y, 135, onOff("Health", e.showHealth), () -> {
                    e.showHealth = !e.showHealth;
                    Config.save();
                    h[0].setMessage(onOff("Health", e.showHealth));
                });
                y += 24;
                Button[] a = new Button[1];
                a[0] = btn(L, y, 270, onOff("Armor durability", e.showArmor), () -> {
                    e.showArmor = !e.showArmor;
                    Config.save();
                    a[0].setMessage(onOff("Armor durability", e.showArmor));
                });
            }
            case 3 -> {
                Config.EntityEsp e = cfg.entityEsp;
                toggleRow(L, y, "Entity ESP", () -> e.enabled, () -> {
                    e.enabled = !e.enabled;
                    Config.save();
                }, Keys.entity);
                y += 24;
                rangeSlider(L, y, e, 256);
                y += 24;
                tracerRow(L, y, e);
                y += 24;
                colorButton(L, y, 130, "Outline", () -> e.outlineColor, v -> {
                    e.outlineColor = v;
                    Config.save();
                });
                colorButton(L + 135, y, 135, "New entity", () -> newColor, v -> newColor = v);
                y += 24;
                listRow(L, y, e.entities, BuiltInRegistries.ENTITY_TYPE, () -> {}, "entity id (zombie)");
                y += 24;
                listMap = e.entities;
                listY = y + 12;
            }
            case 4 -> {
                toggleRow(L, y, "Freecam", Freecam::isActive, () -> Freecam.toggle(Minecraft.getInstance()), Keys.freecam);
                y += 24;
                addRenderableWidget(new ValueSlider(L, y, 270, "Speed", 0.1, 5.0, cfg.freecamSpeed, false, v -> {
                    cfg.freecamSpeed = (float) v;
                    dirty = true;
                }));
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private Button btn(int x, int y, int w, Component label, Runnable action) {
        Button b = Button.builder(label, bb -> action.run()).bounds(x, y, w, 20).build();
        addRenderableWidget(b);
        return b;
    }

    private Component keyLabel(String prefix, KeyMapping km) {
        if (km == listening) return Component.literal("> press a key <");
        return Component.literal(prefix).append(km.getTranslatedKeyMessage());
    }

    private static Component onOff(String name, boolean on) {
        return Component.literal(name + ": " + (on ? "ON" : "OFF"));
    }

    /** [Toggle] [Key: X] [X]  */
    private void toggleRow(int L, int y, String name, BooleanSupplier get, Runnable toggle, KeyMapping key) {
        Button[] t = new Button[1];
        t[0] = btn(L, y, 140, onOff(name, get.getAsBoolean()), () -> {
            toggle.run();
            t[0].setMessage(onOff(name, get.getAsBoolean()));
        });
        btn(L + 145, y, 100, keyLabel("Key: ", key), () -> {
            listening = key;
            rebuildWidgets();
        });
        btn(L + 250, y, 20, Component.literal("X"), () -> {
            Keys.unbind(key);
            listening = null;
            rebuildWidgets();
        });
    }

    private void rangeSlider(int L, int y, Config.Esp e, int max) {
        addRenderableWidget(new ValueSlider(L, y, 270, "Range", 1, max, e.range, true, v -> {
            e.range = (int) v;
            dirty = true;
            BlockScanner.invalidate();
        }));
    }

    private void tracerRow(int L, int y, Config.Esp e) {
        Button[] t = new Button[1];
        t[0] = btn(L, y, 130, onOff("Tracers", e.tracers), () -> {
            e.tracers = !e.tracers;
            Config.save();
            t[0].setMessage(onOff("Tracers", e.tracers));
        });
        colorButton(L + 135, y, 135, "Tracer", () -> e.tracerColor, v -> {
            e.tracerColor = v;
            Config.save();
        });
    }

    private void colorButton(int x, int y, int w, String name, Supplier<String> get, Consumer<String> set) {
        Button[] ref = new Button[1];
        ref[0] = btn(x, y, w, colorLabel(name, get.get()), () -> {
            set.accept(nextColor(get.get()));
            ref[0].setMessage(colorLabel(name, get.get()));
        });
        swatches.add(new Swatch(ref[0], get));
    }

    private static String colorName(String hex) {
        for (int i = 0; i < COLOR_HEX.length; i++) {
            if (COLOR_HEX[i].equalsIgnoreCase(hex)) return COLOR_NAMES[i];
        }
        return "Custom";
    }

    private static Component colorLabel(String name, String hex) {
        return Component.literal(name + ": " + colorName(hex));
    }

    private static String nextColor(String hex) {
        for (int i = 0; i < COLOR_HEX.length; i++) {
            if (COLOR_HEX[i].equalsIgnoreCase(hex)) return COLOR_HEX[(i + 1) % COLOR_HEX.length];
        }
        return COLOR_HEX[0];
    }

    /** Tekstveld + Add + Remove voor blocks/entities. */
    private void listRow(int L, int y, Map<String, String> map, Registry<?> registry, Runnable onChange, String hint) {
        idBox = new EditBox(this.font, L, y, 160, 20, Component.literal("id"));
        idBox.setHint(Component.literal(hint));
        idBox.setMaxLength(100);
        idBox.setValue(lastId);
        idBox.setResponder(s -> lastId = s);
        addRenderableWidget(idBox);

        btn(L + 163, y, 50, Component.literal("Add"), () -> {
            Identifier id = Identifier.tryParse(idBox.getValue().trim());
            if (id == null || !registry.containsKey(id)) {
                status = "Unknown id: " + idBox.getValue().trim();
                return;
            }
            map.put(id.toString(), newColor);
            Config.save();
            onChange.run();
            status = "Added " + id;
        });
        btn(L + 216, y, 54, Component.literal("Remove"), () -> {
            Identifier id = Identifier.tryParse(idBox.getValue().trim());
            if (id == null || map.remove(id.toString()) == null) {
                status = "Not in list: " + idBox.getValue().trim();
                return;
            }
            Config.save();
            onChange.run();
            status = "Removed " + id;
        });
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (listening != null) {
            if (key == InputConstants.KEY_ESCAPE) {
                // annuleren
            } else if (key == InputConstants.KEY_DELETE || key == InputConstants.KEY_BACKSPACE) {
                Keys.unbind(listening);
            } else {
                Keys.bind(listening, key);
            }
            listening = null;
            rebuildWidgets();
            return true;
        }
        if (key == InputConstants.KEY_RSHIFT && !(getFocused() instanceof EditBox)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(this.font, Component.literal("ESP Menu  -  Right Shift / Esc = close"), width / 2, 8, 0xFFFFFFFF);

        for (Swatch s : swatches) {
            Button b = s.button();
            int c = Config.parseColor(s.hex().get(), 0xFFFFFFFF);
            int x = b.getX() + b.getWidth() - 16;
            int y = b.getY() + 5;
            g.fill(x - 1, y - 1, x + 11, y + 11, 0xFF000000);
            g.fill(x, y, x + 10, y + 10, c);
        }

        int L = width / 2 - 140;
        if (listening != null) {
            g.drawString(this.font, "Press a key to bind it  (Delete = no key, Esc = cancel)", L, height - 14, 0xFFFFFF55);
        } else if (page == 0 || page == 4) {
            g.drawString(this.font, "Click a Key button and press a key. X = no key.", L, height - 14, 0xFFAAAAAA);
        }
        if (page == 4) {
            g.drawString(this.font, "WASD = move, Space = up, Shift = down.", L, 100, 0xFFAAAAAA);
            g.drawString(this.font, "Don't attack/mine in freecam: actions start at your body.", L, 112, 0xFFAAAAAA);
        }

        if (listMap != null) {
            g.drawString(this.font, status, L, listY - 10, 0xFFFFFF55);
            int maxLines = Math.max(1, (height - listY - 20) / 10);
            int n = 0;
            for (Map.Entry<String, String> e : listMap.entrySet()) {
                if (n >= maxLines) {
                    g.drawString(this.font, "... +" + (listMap.size() - n) + " more", L, listY + n * 10, 0xFFAAAAAA);
                    break;
                }
                int c = Config.parseColor(e.getValue(), 0xFFFFFFFF);
                g.fill(L, listY + n * 10 + 1, L + 7, listY + n * 10 + 8, c);
                g.drawString(this.font, e.getKey(), L + 11, listY + n * 10, 0xFFFFFFFF);
                n++;
            }
        }
    }

    // ------------------------------------------------------------------ slider

    private static class ValueSlider extends AbstractSliderButton {
        private final String label;
        private final double min, max;
        private final boolean integer;
        private final DoubleConsumer onChange;

        ValueSlider(int x, int y, int w, String label, double min, double max, double cur,
                    boolean integer, DoubleConsumer onChange) {
            super(x, y, w, 20, Component.empty(), (cur - min) / (max - min));
            this.label = label;
            this.min = min;
            this.max = max;
            this.integer = integer;
            this.onChange = onChange;
            updateMessage();
        }

        private double val() {
            double v = min + this.value * (max - min);
            return integer ? Math.round(v) : v;
        }

        @Override
        protected void updateMessage() {
            double v = val();
            setMessage(Component.literal(label + ": " + (integer ? String.valueOf((int) v) : String.format("%.3f", v))));
        }

        @Override
        protected void applyValue() {
            onChange.accept(val());
        }
    }
}
