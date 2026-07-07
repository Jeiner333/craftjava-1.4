package com.miguel.craft;

import java.util.ArrayDeque;

public class LightingEngine {
    private final World world;

    public LightingEngine(World world) {
        this.world = world;
    }

    /** Block.AIR ya tiene transparent = true en el enum, por lo que esta simplificación mantiene el comportamiento exacto. */
    public boolean isLightPassable(Block b) {
        return b.transparent;
    }

    /** Limpia la luz de un chunk y la vuelve a sembrar: luz solar en columnas expuestas al cielo,
     *  bloques emisores (antorchas), y además "hereda" la luz que ya exista en los bordes de los
     *  chunks vecinos ya cargados (para que un chunk recién generado no aparezca como una caja negra
     *  junto a una zona ya iluminada). Los puntos sembrados se agregan a la cola en COORDENADAS DE
     *  MUNDO, para que la propagación posterior pueda cruzar libremente a los chunks vecinos. */
    public void seedChunkLight(Chunk c, ArrayDeque<int[]> queue) {
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

    public void inheritBorder(Chunk c, int dcx, int dcz, ArrayDeque<int[]> queue) {
        Chunk neighbor = world.getChunk(c.chunkX + dcx, c.chunkZ + dcz);
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
    public void propagateLight(ArrayDeque<int[]> queue) {
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
                Chunk nc = world.getChunk(ncx, ncz);
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
    public void relightChunk(Chunk c) {
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        seedChunkLight(c, queue);
        propagateLight(queue);
        c.dirty = true;
    }

    /** Nivel de luz (0-15) en una coordenada del mundo; 0 si el chunk no está cargado. */
    public int getLight(int wx, int wy, int wz) {
        if (wy < 0 || wy >= Chunk.HEIGHT) return 0;
        int cx = Math.floorDiv(wx, Chunk.SIZE);
        int cz = Math.floorDiv(wz, Chunk.SIZE);
        Chunk c = world.getChunk(cx, cz);
        if (c == null) return 0;
        return c.getLight(Math.floorMod(wx, Chunk.SIZE), wy, Math.floorMod(wz, Chunk.SIZE));
    }

    public void relightIfLoaded(int cx, int cz) {
        Chunk c = world.getChunk(cx, cz);
        if (c != null) relightChunk(c);
    }

    public void relightAll() {
        for (Chunk c : world.loadedChunks()) {
            relightChunk(c);
        }
    }
}