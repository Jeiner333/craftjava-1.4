package com.miguel.craft;

import java.util.HashMap;
import java.util.Map;
import org.joml.Vector3f;

public class World {
    private final Map<Long, Chunk> chunks = new HashMap<>();
    private final NoiseGen noise;
    public final int renderDistance;

    public World(long seed, int renderDistance) {
        this.noise = new NoiseGen(seed);
        this.renderDistance = renderDistance;
    }

    private static long key(int cx, int cz) {
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
            generate(c);
            chunks.put(k, c);
        }
        return c;
    }

    /** Genera el terreno de un chunk usando ruido fractal para altura, con capas de tierra/piedra y árboles,
     *  luego talla cuevas y distribuye menas dentro de la piedra. */
    private void generate(Chunk c) {
        int baseX = c.worldX();
        int baseZ = c.worldZ();
        for (int x = 0; x < Chunk.SIZE; x++) {
            for (int z = 0; z < Chunk.SIZE; z++) {
                float n = noise.fbm((baseX + x) * 0.03f, (baseZ + z) * 0.03f, 4, 0.5f);
                int height = 24 + Math.round(n * 14); // altura del terreno
                for (int y = 0; y <= height; y++) {
                    Block b;
                    if (y == 0) b = Block.BEDROCK;
                    else if (y == height) b = (height <= 25) ? Block.SAND : Block.GRASS;
                    else if (y > height - 4) b = Block.DIRT;
                    else b = oreOrStone(baseX + x, y, baseZ + z);
                    c.set(x, y, z, b);
                }
                // agua a nivel bajo
                if (height < 24) {
                    for (int y = height + 1; y <= 24; y++) c.set(x, y, z, Block.WATER);
                }
                // árboles ocasionales
                if (height >= 26 && (pseudoRandom(baseX + x, baseZ + z) < 0.01)) {
                    placeTree(c, x, height + 1, z);
                }
            }
        }
        carveCaves(c);
        relightChunk(c);
    }

    /** Decide si una posición dentro de la piedra es una veta de mena, o piedra normal. */
    private Block oreOrStone(int wx, int wy, int wz) {
        double r = pseudoRandom(wx * 7 + 3, wz * 13 + wy * 31);
        if (wy < 20 && r < 0.015) return Block.IRON_ORE;
        if (wy < 40 && r < 0.03) return Block.COAL_ORE;
        return Block.STONE;
    }

    /** Talla cuevas usando ruido 3D: donde el ruido supera un umbral, el bloque se vacía (se vuelve aire),
     *  siempre que no sea la capa de superficie ni bedrock. */
    private void carveCaves(Chunk c) {
        int baseX = c.worldX();
        int baseZ = c.worldZ();
        for (int x = 0; x < Chunk.SIZE; x++) {
            for (int z = 0; z < Chunk.SIZE; z++) {
                for (int y = 4; y < Chunk.HEIGHT - 6; y++) {
                    Block cur = c.get(x, y, z);
                    if (cur == Block.AIR || cur == Block.WATER || cur == Block.BEDROCK) continue;
                    float density = noise.noise3D((baseX + x) * 0.09f, y * 0.09f, (baseZ + z) * 0.09f);
                    // hace las cuevas más grandes cerca de la roca profunda y las cierra cerca de la superficie
                    float threshold = 0.62f - Math.max(0, (y - 10) * 0.002f);
                    if (density > threshold) {
                        c.set(x, y, z, Block.AIR);
                    }
                }
            }
        }
    }

    private double pseudoRandom(int x, int z) {
        long h = x * 374761393L + z * 668265263L;
        h = (h ^ (h >> 13)) * 1274126177L;
        h = h ^ (h >> 16);
        return Math.abs(h % 10000) / 10000.0;
    }

    /** Determina si la luz puede atravesar/entrar en este bloque (aire, agua, vidrio, antorcha...). */
    private boolean isLightPassable(Block b) {
        return b == Block.AIR || b.transparent;
    }

    /** Limpia la luz de un chunk y la vuelve a sembrar: luz solar en columnas expuestas al cielo,
     *  bloques emisores (antorchas), y además "hereda" la luz que ya exista en los bordes de los
     *  chunks vecinos ya cargados (para que un chunk recién generado no aparezca como una caja negra
     *  junto a una zona ya iluminada). Los puntos sembrados se agregan a la cola en COORDENADAS DE
     *  MUNDO, para que la propagación posterior pueda cruzar libremente a los chunks vecinos. */
    private void seedChunkLight(Chunk c, java.util.ArrayDeque<int[]> queue) {
        int baseX = c.worldX(), baseZ = c.worldZ();

        for (int x = 0; x < Chunk.SIZE; x++)
            for (int y = 0; y < Chunk.HEIGHT; y++)
                for (int z = 0; z < Chunk.SIZE; z++)
                    c.setLight(x, y, z, 0);

        // luz solar: desde arriba de cada columna hasta el primer bloque opaco
        for (int x = 0; x < Chunk.SIZE; x++) {
            for (int z = 0; z < Chunk.SIZE; z++) {
                for (int y = Chunk.HEIGHT - 1; y >= 0; y--) {
                    Block b = c.get(x, y, z);
                    if (!isLightPassable(b)) break;
                    c.setLight(x, y, z, 15);
                    queue.add(new int[]{baseX + x, y, baseZ + z, 15});
                }
            }
        }

        // bloques emisores de luz propia (antorchas)
        for (int x = 0; x < Chunk.SIZE; x++)
            for (int y = 0; y < Chunk.HEIGHT; y++)
                for (int z = 0; z < Chunk.SIZE; z++) {
                    Block b = c.get(x, y, z);
                    if (b.lightEmission > 0 && c.getLight(x, y, z) < b.lightEmission) {
                        c.setLight(x, y, z, b.lightEmission);
                        queue.add(new int[]{baseX + x, y, baseZ + z, b.lightEmission});
                    }
                }

        // heredar luz de los bordes de los chunks vecinos ya cargados
        inheritBorder(c, -1, 0, queue);
        inheritBorder(c, 1, 0, queue);
        inheritBorder(c, 0, -1, queue);
        inheritBorder(c, 0, 1, queue);
    }

    private void inheritBorder(Chunk c, int dcx, int dcz, java.util.ArrayDeque<int[]> queue) {
        Chunk neighbor = getChunk(c.chunkX + dcx, c.chunkZ + dcz);
        if (neighbor == null) return;
        int baseX = c.worldX(), baseZ = c.worldZ();
        for (int y = 0; y < Chunk.HEIGHT; y++) {
            for (int i = 0; i < Chunk.SIZE; i++) {
                int lx = dcx != 0 ? (dcx < 0 ? 0 : Chunk.SIZE - 1) : i;
                int lz = dcz != 0 ? (dcz < 0 ? 0 : Chunk.SIZE - 1) : i;
                int nlx = dcx != 0 ? (dcx < 0 ? Chunk.SIZE - 1 : 0) : i;
                int nlz = dcz != 0 ? (dcz < 0 ? Chunk.SIZE - 1 : 0) : i;
                int neighborLight = neighbor.getLight(nlx, y, nlz);
                if (neighborLight <= 1) continue;
                int wx = baseX + lx, wz = baseZ + lz;
                if (!isLightPassable(c.get(lx, y, lz))) continue;
                int inherited = neighborLight - 1;
                if (c.getLight(lx, y, lz) < inherited) {
                    c.setLight(lx, y, lz, inherited);
                    queue.add(new int[]{wx, y, wz, inherited});
                }
            }
        }
    }

    /** Propaga un flood-fill de luz en coordenadas de mundo, cruzando a cualquier chunk cargado
     *  vecino y marcándolo como modificado (dirty) para que se vuelva a dibujar. */
    private void propagateLight(java.util.ArrayDeque<int[]> queue) {
        int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            int lvl = cur[3];
            if (lvl <= 1) continue;
            for (int[] d : dirs) {
                int nx = cur[0] + d[0], ny = cur[1] + d[1], nz = cur[2] + d[2];
                if (ny < 0 || ny >= Chunk.HEIGHT) continue;
                int ncx = Math.floorDiv(nx, Chunk.SIZE);
                int ncz = Math.floorDiv(nz, Chunk.SIZE);
                Chunk nc = getChunk(ncx, ncz);
                if (nc == null) continue; // no propagamos hacia chunks aún no cargados
                int lx = Math.floorMod(nx, Chunk.SIZE), lz = Math.floorMod(nz, Chunk.SIZE);
                Block nb = nc.get(lx, ny, lz);
                if (!isLightPassable(nb)) continue;
                int newLvl = lvl - 1;
                if (nc.getLight(lx, ny, lz) < newLvl) {
                    nc.setLight(lx, ny, lz, newLvl);
                    nc.dirty = true;
                    queue.add(new int[]{nx, ny, nz, newLvl});
                }
            }
        }
    }

    /** Recalcula la iluminación de un chunk y la propaga libremente hacia sus vecinos cargados. */
    private void relightChunk(Chunk c) {
        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
        seedChunkLight(c, queue);
        propagateLight(queue);
        c.dirty = true;
    }

    /** Nivel de luz (0-15) en una coordenada del mundo; 0 si el chunk no está cargado. */
    public int getLight(int wx, int wy, int wz) {
        if (wy < 0 || wy >= Chunk.HEIGHT) return 0;
        int cx = Math.floorDiv(wx, Chunk.SIZE);
        int cz = Math.floorDiv(wz, Chunk.SIZE);
        Chunk c = getChunk(cx, cz);
        if (c == null) return 0;
        return c.getLight(Math.floorMod(wx, Chunk.SIZE), wy, Math.floorMod(wz, Chunk.SIZE));
    }

    private void placeTree(Chunk c, int x, int y, int z) {
        int trunk = 4;
        for (int i = 0; i < trunk; i++) c.set(x, y + i, z, Block.WOOD);
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
                for (int dy = 0; dy <= 2; dy++) {
                    if (Math.abs(dx) + Math.abs(dz) + dy <= 3)
                        c.set(x + dx, y + trunk + dy, z + dz, Block.LEAVES);
                }
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
        relightChunk(c);
        relightIfLoaded(cx - 1, cz);
        relightIfLoaded(cx + 1, cz);
        relightIfLoaded(cx, cz - 1);
        relightIfLoaded(cx, cz + 1);
    }

    private void relightIfLoaded(int cx, int cz) {
        Chunk c = getChunk(cx, cz);
        if (c != null) relightChunk(c);
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

    public void relightAll() {
        for (Chunk c : chunks.values()) {
            relightChunk(c);
        }
    }

    /** Guarda todos los chunks cargados, el estado del jugador y el inventario en un archivo binario. */
    public void saveToFile(String path, Player player, Map<Block, Integer> inventory) {
        try (var out = new java.io.DataOutputStream(new java.io.BufferedOutputStream(new java.io.FileOutputStream(path)))) {
            // Guardar versión negativa para indicar que incluye estado de jugador e inventario
            out.writeInt(-1);
            
            // Guardar física del jugador
            out.writeFloat(player.pos.x);
            out.writeFloat(player.pos.y);
            out.writeFloat(player.pos.z);
            out.writeFloat(player.yaw);
            out.writeFloat(player.pitch);
            out.writeFloat(player.health);
            out.writeBoolean(player.flying);
            
            // Guardar inventario (solo las cantidades mayores a 0)
            java.util.Map<Block, Integer> activeInv = new java.util.HashMap<>();
            for (var entry : inventory.entrySet()) {
                if (entry.getValue() != null && entry.getValue() > 0) {
                    activeInv.put(entry.getKey(), entry.getValue());
                }
            }
            out.writeInt(activeInv.size());
            for (var entry : activeInv.entrySet()) {
                out.writeByte(entry.getKey().ordinal());
                out.writeInt(entry.getValue());
            }
            
            // Guardar chunks
            out.writeInt(chunks.size());
            for (Chunk c : chunks.values()) {
                out.writeInt(c.chunkX);
                out.writeInt(c.chunkZ);
                for (int x = 0; x < Chunk.SIZE; x++)
                    for (int y = 0; y < Chunk.HEIGHT; y++)
                        for (int z = 0; z < Chunk.SIZE; z++)
                            out.writeByte(c.get(x, y, z).ordinal());
            }
            System.out.println("Mundo guardado en " + path + " (" + chunks.size() + " chunks, jugador e inventario guardados)");
        } catch (java.io.IOException e) {
            System.err.println("Error guardando el mundo: " + e.getMessage());
        }
    }

    /** Carga el mundo, el jugador y el inventario desde un archivo previamente guardado. */
    public void loadFromFile(String path, Player player, Map<Block, Integer> inventory) {
        java.io.File f = new java.io.File(path);
        if (!f.exists()) {
            System.out.println("No hay partida guardada en " + path);
            return;
        }
        try (var in = new java.io.DataInputStream(new java.io.BufferedInputStream(new java.io.FileInputStream(f)))) {
            chunks.clear();
            int firstInt = in.readInt();
            int count;
            Block[] values = Block.VALUES;
            
            if (firstInt < 0) {
                int version = -firstInt;
                if (version == 1) {
                    // Cargar estado del jugador
                    player.pos.x = in.readFloat();
                    player.pos.y = in.readFloat();
                    player.pos.z = in.readFloat();
                    player.yaw = in.readFloat();
                    player.pitch = in.readFloat();
                    player.health = in.readFloat();
                    player.flying = in.readBoolean();
                    
                    // Cargar inventario
                    inventory.clear();
                    int invSize = in.readInt();
                    for (int i = 0; i < invSize; i++) {
                        Block b = values[in.readByte()];
                        int amt = in.readInt();
                        inventory.put(b, amt);
                    }
                    
                    count = in.readInt();
                } else {
                    throw new java.io.IOException("Versión de guardado desconocida: " + version);
                }
            } else {
                count = firstInt;
            }
            
            for (int i = 0; i < count; i++) {
                int cx = in.readInt();
                int cz = in.readInt();
                Chunk c = new Chunk(cx, cz);
                for (int x = 0; x < Chunk.SIZE; x++)
                    for (int y = 0; y < Chunk.HEIGHT; y++)
                        for (int z = 0; z < Chunk.SIZE; z++)
                            c.set(x, y, z, values[in.readByte()]);
                chunks.put(key(cx, cz), c);
            }
            relightAll();
            System.out.println("Mundo cargado desde " + path + " (" + count + " chunks, versión " + (firstInt < 0 ? -firstInt : "antigua") + ")");
        } catch (java.io.IOException e) {
            System.err.println("Error cargando el mundo: " + e.getMessage());
        }
    }

    /** Raycast tipo DDA para hallar el primer bloque sólido y la cara golpeada. */
    public static class RayHit {
        public int x, y, z;       // bloque golpeado
        public int nx, ny, nz;    // normal de la cara (para colocar bloque)
        public boolean hit;
    }

    public RayHit raycast(Vector3f origin, Vector3f dir, float maxDist) {
        RayHit result = new RayHit();
        
        int x = (int) Math.floor(origin.x);
        int y = (int) Math.floor(origin.y);
        int z = (int) Math.floor(origin.z);
        
        float dx = dir.x;
        float dy = dir.y;
        float dz = dir.z;
        
        int stepX = (dx > 0) ? 1 : ((dx < 0) ? -1 : 0);
        int stepY = (dy > 0) ? 1 : ((dy < 0) ? -1 : 0);
        int stepZ = (dz > 0) ? 1 : ((dz < 0) ? -1 : 0);
        
        float deltaX = (stepX != 0) ? Math.abs(1f / dx) : Float.MAX_VALUE;
        float deltaY = (stepY != 0) ? Math.abs(1f / dy) : Float.MAX_VALUE;
        float deltaZ = (stepZ != 0) ? Math.abs(1f / dz) : Float.MAX_VALUE;
        
        float tMaxX = (stepX > 0) ? ((float) Math.floor(origin.x) + 1.0f - origin.x) * deltaX :
                      (stepX < 0) ? (origin.x - (float) Math.floor(origin.x)) * deltaX : Float.MAX_VALUE;
        float tMaxY = (stepY > 0) ? ((float) Math.floor(origin.y) + 1.0f - origin.y) * deltaY :
                      (stepY < 0) ? (origin.y - (float) Math.floor(origin.y)) * deltaY : Float.MAX_VALUE;
        float tMaxZ = (stepZ > 0) ? ((float) Math.floor(origin.z) + 1.0f - origin.z) * deltaZ :
                      (stepZ < 0) ? (origin.z - (float) Math.floor(origin.z)) * deltaZ : Float.MAX_VALUE;
                      
        int lastX = x;
        int lastY = y;
        int lastZ = z;
        
        float t = 0;
        while (t < maxDist) {
            if (isTargetable(x, y, z)) {
                result.hit = true;
                result.x = x; result.y = y; result.z = z;
                result.nx = lastX - x; result.ny = lastY - y; result.nz = lastZ - z;
                return result;
            }
            
            lastX = x; lastY = y; lastZ = z;
            
            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) {
                    t = tMaxX;
                    tMaxX += deltaX;
                    x += stepX;
                } else {
                    t = tMaxZ;
                    tMaxZ += deltaZ;
                    z += stepZ;
                }
            } else {
                if (tMaxY < tMaxZ) {
                    t = tMaxY;
                    tMaxY += deltaY;
                    y += stepY;
                } else {
                    t = tMaxZ;
                    tMaxZ += deltaZ;
                    z += stepZ;
                }
            }
        }
        
        result.hit = false;
        return result;
    }
}
