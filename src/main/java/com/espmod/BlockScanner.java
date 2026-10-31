package com.espmod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Zoekt periodiek de gekozen blokken in de geladen chunks rond de speler. */
public final class BlockScanner {
    public record Found(int x, int y, int z, int color) {}

    private static List<Found> results = List.of();
    private static int ticks = 0;

    private BlockScanner() {}

    public static List<Found> results() {
        return results;
    }

    public static void tick(Minecraft mc) {
        Config.BlockEsp cfg = Config.get().blockEsp;
        if (!cfg.enabled || mc.level == null || mc.player == null) {
            results = List.of();
            ticks = 0;
            return;
        }
        if (ticks-- > 0) return;
        ticks = 10; // elke halve seconde
        scan(mc.level, mc.player, cfg);
    }

    /** Forceer een nieuwe scan bij de volgende tick. */
    public static void invalidate() {
        ticks = 0;
    }

    private static void scan(ClientLevel level, LocalPlayer player, Config.BlockEsp cfg) {
        Map<net.minecraft.world.level.block.Block, Integer> colors = Config.get().blockColors;
        if (colors.isEmpty()) {
            results = List.of();
            return;
        }
        Predicate<BlockState> wanted = s -> colors.containsKey(s.getBlock());

        BlockPos pp = player.blockPosition();
        int r = cfg.range;
        long r2 = (long) r * r;
        int minSectionY = level.dimensionType().minY() >> 4;

        int minCx = (pp.getX() - r) >> 4, maxCx = (pp.getX() + r) >> 4;
        int minCz = (pp.getZ() - r) >> 4, maxCz = (pp.getZ() + r) >> 4;

        List<Found> found = new ArrayList<>();
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                ChunkAccess chunk = level.getChunk(cx, cz, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                LevelChunkSection[] sections = chunk.getSections();
                for (int i = 0; i < sections.length; i++) {
                    LevelChunkSection sec = sections[i];
                    if (sec == null || sec.hasOnlyAir() || !sec.maybeHas(wanted)) continue;
                    int baseY = (minSectionY + i) << 4;
                    if (baseY > pp.getY() + r || baseY + 15 < pp.getY() - r) continue;
                    for (int x = 0; x < 16; x++) {
                        for (int y = 0; y < 16; y++) {
                            for (int z = 0; z < 16; z++) {
                                BlockState state = sec.getBlockState(x, y, z);
                                Integer color = colors.get(state.getBlock());
                                if (color == null) continue;
                                int wx = (cx << 4) + x, wy = baseY + y, wz = (cz << 4) + z;
                                long dx = wx - pp.getX(), dy = wy - pp.getY(), dz = wz - pp.getZ();
                                if (dx * dx + dy * dy + dz * dz > r2) continue;
                                found.add(new Found(wx, wy, wz, color));
                            }
                        }
                    }
                }
            }
        }

        final int px = pp.getX(), py = pp.getY(), pz = pp.getZ();
        found.sort(Comparator.comparingLong(f -> {
            long dx = f.x() - px, dy = f.y() - py, dz = f.z() - pz;
            return dx * dx + dy * dy + dz * dz;
        }));
        if (found.size() > cfg.maxBlocks) found = new ArrayList<>(found.subList(0, cfg.maxBlocks));
        results = found;
    }
}
