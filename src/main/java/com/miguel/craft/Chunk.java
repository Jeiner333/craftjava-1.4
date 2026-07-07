package com.miguel.craft;

public class Chunk {
    public static final int SIZE = 16;   // ancho/profundidad
    public static final int HEIGHT = 64; // altura del chunk
    private static final boolean DEBUG = Boolean.getBoolean("craft.debug");

    public final int chunkX, chunkZ; // coordenadas de chunk (no de bloque)
    private final Block[][][] blocks = new Block[SIZE][HEIGHT][SIZE];
    /** Nivel de luz combinado (cielo + antorchas) de cada bloque, 0 (oscuro) a 15 (brillante). */
    private final byte[][][] light = new byte[SIZE][HEIGHT][SIZE];
    public boolean dirty = true; // si necesita regenerar su lista de despliegue
    private boolean modified = false; // si ha sido editado desde su generación/carga inicial

    public Chunk(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.modified = false;
        for (int x = 0; x < SIZE; x++)
            for (int y = 0; y < HEIGHT; y++)
                for (int z = 0; z < SIZE; z++)
                    blocks[x][y][z] = Block.AIR;
    }

    public Block get(int x, int y, int z) {
        if (x < 0 || x >= SIZE || y < 0 || y >= HEIGHT || z < 0 || z >= SIZE) {
            if (DEBUG) {
                throw new IllegalArgumentException(
                        "Chunk.get() out of bounds: x=" + x + ", y=" + y + ", z=" + z +
                        " (expected 0<=x<" + SIZE + ", 0<=y<" + HEIGHT + ", 0<=z<" + SIZE + ")"
                );
            }
            return Block.AIR;
        }
        return blocks[x][y][z];
    }

    public void set(int x, int y, int z, Block b) {
        if (x < 0 || x >= SIZE || y < 0 || y >= HEIGHT || z < 0 || z >= SIZE) {
            if (DEBUG) {
                throw new IllegalArgumentException(
                        "Chunk.set() out of bounds: x=" + x + ", y=" + y + ", z=" + z +
                        " (expected 0<=x<" + SIZE + ", 0<=y<" + HEIGHT + ", 0<=z<" + SIZE + ")"
                );
            }
            return;
        }
        blocks[x][y][z] = b;
        dirty = true;
        modified = true;
    }

    public boolean isModified() {
        return modified;
    }

    public void setModified(boolean modified) {
        this.modified = modified;
    }

    public void markModified() {
        this.modified = true;
    }

    public int getLight(int x, int y, int z) {
        if (x < 0 || x >= SIZE || y < 0 || y >= HEIGHT || z < 0 || z >= SIZE) {
            if (DEBUG) {
                throw new IllegalArgumentException(
                        "Chunk.getLight() out of bounds: x=" + x + ", y=" + y + ", z=" + z +
                        " (expected 0<=x<" + SIZE + ", 0<=y<" + HEIGHT + ", 0<=z<" + SIZE + ")"
                );
            }
            return 0;
        }
        return light[x][y][z];
    }

    public void setLight(int x, int y, int z, int level) {
        if (x < 0 || x >= SIZE || y < 0 || y >= HEIGHT || z < 0 || z >= SIZE) {
            if (DEBUG) {
                throw new IllegalArgumentException(
                        "Chunk.setLight() out of bounds: x=" + x + ", y=" + y + ", z=" + z +
                        " (expected 0<=x<" + SIZE + ", 0<=y<" + HEIGHT + ", 0<=z<" + SIZE + ")"
                );
            }
            return;
        }
        light[x][y][z] = (byte) Math.max(0, Math.min(15, level));
    }

    public int worldX() { return chunkX * SIZE; }
    public int worldZ() { return chunkZ * SIZE; }
}
