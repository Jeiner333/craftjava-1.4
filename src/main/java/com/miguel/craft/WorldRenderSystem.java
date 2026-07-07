package com.miguel.craft;

import org.joml.FrustumIntersection;

public class WorldRenderSystem {
    public void render(World world, WorldManager worldManager, FrustumIntersection frustum) {
        for (Chunk c : world.loadedChunks()) {
            long key = (((long) c.chunkX) << 32) ^ (c.chunkZ & 0xffffffffL);
            float minX = c.worldX();
            float minZ = c.worldZ();
            float maxX = minX + Chunk.SIZE;
            float maxZ = minZ + Chunk.SIZE;
            
            if (frustum.testAab(minX, 0, minZ, maxX, Chunk.HEIGHT, maxZ)) {
                ChunkRenderer r = worldManager.getOrCreateRenderer(key);
                r.render(world, c);
            }
        }
    }
}
