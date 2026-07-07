package com.miguel.craft;

import java.util.HashMap;
import java.util.Map;
import org.joml.Vector3f;

public class World {
    final Map<Long, Chunk> chunks = new HashMap<>();
    private final WorldGenerator worldGenerator;
    public final int renderDistance;
    private final RaycastingSystem raycastingSystem;
    private final LightingEngine lightingEngine;
    private final WorldPersistence worldPersistence;

    public World(long seed, int renderDistance) {
        this.worldGenerator = new WorldGenerator(seed);
        this.renderDistance = renderDistance;
        this.raycastingSystem = new RaycastingSystem(this);
        this.lightingEngine = new LightingEngine(this);
        this.worldPersistence = new WorldPersistence(this);
    }

    static long key(int cx, int cz) {
        return (((long) cx) << 32) ^ (cz & 0xffffffffL);
    }

    public Chunk getChunk(int cx, int cz) {
        return chunks.get(key(cx, cz));
    }

    public Chunk getOrCreateChunk(int cx, int cz) {
        long k = key(cx, cz);
        Chunk c = chunks.get(k);
        if (c == null) {
            c = new Chunk(cx, cz);
            worldGenerator.generate(c);
            lightingEngine.relightChunk(c);
            chunks.put(k, c);
        }
        return c;
    }

    public Block getBlock(int wx, int wy, int wz) {
        if (wy < 0 || wy >= Chunk.HEIGHT) return Block.AIR;
        int cx = Math.floorDiv(wx, Chunk.SIZE);
        int cz = Math.floorDiv(wz, Chunk.SIZE);
        Chunk c = getChunk(cx, cz);
        if (c == null) return Block.AIR;
        return c.get(Math.floorMod(wx, Chunk.SIZE), wy, Math.floorMod(wz, Chunk.SIZE));
    }

    public void setBlock(int wx, int wy, int wz, Block b) {
        if (wy < 0 || wy >= Chunk.HEIGHT) return;
        int cx = Math.floorDiv(wx, Chunk.SIZE);
        int cz = Math.floorDiv(wz, Chunk.SIZE);
        Chunk c = getOrCreateChunk(cx, cz);
        c.set(Math.floorMod(wx, Chunk.SIZE), wy, Math.floorMod(wz, Chunk.SIZE), b);

        // relumina el chunk editado y sus 4 vecinos directos: al limpiar y re-sembrar cada uno,
        // tanto agregar como QUITAR una fuente de luz (ej. romper una antorcha) queda bien reflejado
        // en la zona inmediata, y la propagación restante sigue cruzando hacia el resto del mundo cargado.
        lightingEngine.relightChunk(c);
        lightingEngine.relightIfLoaded(cx - 1, cz);
        lightingEngine.relightIfLoaded(cx + 1, cz);
        lightingEngine.relightIfLoaded(cx, cz - 1);
        lightingEngine.relightIfLoaded(cx, cz + 1);
    }

    public boolean isSolid(int wx, int wy, int wz) {
        Block b = getBlock(wx, wy, wz);
        return b != Block.AIR && b != Block.WATER && b != Block.TORCH;
    }

    /** Si el bloque puede ser señalado/roto por el raycast del jugador (incluye la antorcha, que no es sólida para la física). */
    public boolean isTargetable(int wx, int wy, int wz) {
        Block b = getBlock(wx, wy, wz);
        return b != Block.AIR && b != Block.WATER;
    }

    public void ensureChunksAround(int wx, int wz) {
        int cx = Math.floorDiv(wx, Chunk.SIZE);
        int cz = Math.floorDiv(wz, Chunk.SIZE);
        for (int dx = -renderDistance; dx <= renderDistance; dx++)
            for (int dz = -renderDistance; dz <= renderDistance; dz++)
                getOrCreateChunk(cx + dx, cz + dz);
    }

    public Iterable<Chunk> loadedChunks() {
        return chunks.values();
    }

    public void removeChunk(long key) {
        chunks.remove(key);
    }

    public int getLight(int wx, int wy, int wz) {
        return lightingEngine.getLight(wx, wy, wz);
    }

    public void relightAll() {
        lightingEngine.relightAll();
    }

    public World.RayHit raycast(Vector3f origin, Vector3f dir, float maxDist) {
        RaycastingSystem.RayHit hit = raycastingSystem.raycast(origin, dir, maxDist);
        RayHit result = new RayHit();
        result.hit = hit.hit;
        result.x = hit.x; result.y = hit.y; result.z = hit.z;
        result.nx = hit.nx; result.ny = hit.ny; result.nz = hit.nz;
        return result;
    }

    public static class RayHit {
        public int x, y, z;       // bloque golpeado
        public int nx, ny, nz;    // normal de la cara (para colocar bloque)
        public boolean hit;
    }

    public void saveToFile(String path, Player player, Map<Block, Integer> inventory) {
        worldPersistence.saveToFile(path, player, inventory);
    }

    public void loadFromFile(String path, Player player, Map<Block, Integer> inventory) {
        worldPersistence.loadFromFile(path, player, inventory);
    }
}