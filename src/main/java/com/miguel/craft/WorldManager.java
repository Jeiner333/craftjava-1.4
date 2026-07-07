package com.miguel.craft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WorldManager {
    private final World world;
    private final Map<Long, ChunkRenderer> renderers = new HashMap<>();
    private static final boolean ENABLE_CHUNK_UNLOAD = Boolean.getBoolean("craft.chunk.unload");

    public WorldManager(World world) {
        this.world = world;
    }

    public void initialize(Player player) {
        world.ensureChunksAround((int) player.pos.x, (int) player.pos.z);
    }

    public void update(Player player) {
        world.ensureChunksAround((int) player.pos.x, (int) player.pos.z);
        // Desalojo deshabilitado por defecto. Se puede activar con -Dcraft.chunk.unload=true
        // solo después de implementar el guardado incremental por chunk; de lo contrario hay riesgo de pérdida de datos.
        if (ENABLE_CHUNK_UNLOAD) {
            unloadFarChunks(player);
        }
    }

    public ChunkRenderer getOrCreateRenderer(long key) {
        return renderers.computeIfAbsent(key, k -> new ChunkRenderer());
    }

    public void cleanup() {
        for (ChunkRenderer r : renderers.values()) {
            r.cleanup();
        }
        renderers.clear();
    }

    public void invalidateAll() {
        renderers.clear();
    }

    private void unloadFarChunks(Player player) {
        int cx = Math.floorDiv((int) player.pos.x, Chunk.SIZE);
        int cz = Math.floorDiv((int) player.pos.z, Chunk.SIZE);
        int limit = world.renderDistance + 2;

        List<Long> toRemove = new ArrayList<>();
        for (Chunk c : world.loadedChunks()) {
            if (Math.abs(c.chunkX - cx) > limit || Math.abs(c.chunkZ - cz) > limit) {
                toRemove.add((((long) c.chunkX) << 32) ^ (c.chunkZ & 0xffffffffL));
            }
        }

        for (long key : toRemove) {
            ChunkRenderer r = renderers.remove(key);
            if (r != null) {
                r.cleanup();
            }
            world.removeChunk(key);
        }
    }
}
