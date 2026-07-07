package com.miguel.craft;

public class WorldGenerator {
    private final NoiseGen noise;

    public WorldGenerator(long seed) {
        this.noise = new NoiseGen(seed);
    }

    /** Genera el terreno de un chunk usando ruido fractal para altura, con capas de tierra/piedra y árboles,
     *  luego talla cuevas y distribuye menas dentro de la piedra. */
    public void generate(Chunk c) {
        int baseX = c.worldX();
        int baseZ = c.worldZ();
        int[][] heights = new int[Chunk.SIZE][Chunk.SIZE];
        generateTerrain(c, baseX, baseZ, heights);
        placeTrees(c, baseX, baseZ, heights);
        carveCaves(c, 0.09f, 0.62f, 0.002f);
    }

    /** Recorre todas las columnas del chunk y genera el terreno superficial y agua. */
    private void generateTerrain(Chunk c, int baseX, int baseZ, int[][] heights) {
        for (int x = 0; x < Chunk.SIZE; x++) {
            for (int z = 0; z < Chunk.SIZE; z++) {
                float n = noise.fbm((baseX + x) * 0.03f, (baseZ + z) * 0.03f, 4, 0.5f);
                int height = 24 + Math.round(n * 14); // altura del terreno
                heights[x][z] = height;
                for (int y = 0; y <= height; y++) {
                    Block b = determineBlock(baseX + x, y, baseZ + z, height);
                    c.set(x, y, z, b);
                }
                fillWater(c, height, x, z);
            }
        }
    }

    /** Decide si debe colocar un árbol en la posición superficial dada, basándose en la altura y el ruido pseudoaleatorio. */
    private boolean shouldPlaceTree(int worldX, int worldZ, int height) {
        return height >= 26 && pseudoRandom(worldX, worldZ) < 0.01;
    }

    /** Recorre las alturas calculadas del terreno e intenta colocar árboles en las columnas adecuadas. */
    private void placeTrees(Chunk c, int baseX, int baseZ, int[][] heights) {
        for (int x = 0; x < Chunk.SIZE; x++) {
            for (int z = 0; z < Chunk.SIZE; z++) {
                int height = heights[x][z];
                tryPlaceTree(c, x, height, z, baseX, baseZ);
            }
        }
    }

    /** Decide si una posición dentro de la piedra es una veta de mena, o piedra normal. */
    private Block oreOrStone(int wx, int wy, int wz) {
        double r = pseudoRandom(wx * 7 + 3, wz * 13 + wy * 31);
        if (wy < 20 && r < 0.015) return Block.IRON_ORE;
        if (wy < 40 && r < 0.03) return Block.COAL_ORE;
        return Block.STONE;
    }

    /** Define la estratigrafía del terreno: qué bloque corresponde a cada altura en una columna. */
    private Block determineBlock(int worldX, int worldY, int worldZ, int height) {
        if (worldY == 0) return Block.BEDROCK;
        if (worldY == height) return (height <= 25) ? Block.SAND : Block.GRASS;
        if (worldY > height - 4) return Block.DIRT;
        return oreOrStone(worldX, worldY, worldZ);
    }

    /** Rellena la capa de agua hasta y = 24 para columnas de terreno bajo ese nivel. */
    private void fillWater(Chunk c, int height, int x, int z) {
        if (height < 24) {
            for (int y = height + 1; y <= 24; y++) {
                c.set(x, y, z, Block.WATER);
            }
        }
    }

    /** Talla cuevas usando ruido 3D.
     *
     * @param c       chunk a modificar
     * @param frequency factor de escala para las coordenadas del ruido 3D (valores menores = cuevas más grandes y dispersas)
     * @param thresholdBase umbral base de densidad para decidir si un voxel se vacía (valores mayores = cuevas más pequeñas)
     * @param thresholdSlope pendiente del umbral con la altura (valores mayores = cuevas más cerradas en superficie)
     */
    private void carveCaves(Chunk c, float frequency, float thresholdBase, float thresholdSlope) {
        int baseX = c.worldX();
        int baseZ = c.worldZ();
        for (int x = 0; x < Chunk.SIZE; x++) {
            for (int z = 0; z < Chunk.SIZE; z++) {
                for (int y = 4; y < Chunk.HEIGHT - 6; y++) {
                    Block cur = c.get(x, y, z);
                    if (cur == Block.AIR || cur == Block.WATER || cur == Block.BEDROCK) continue;
                    float density = noise.noise3D((baseX + x) * frequency, y * frequency, (baseZ + z) * frequency);
                    // hace las cuevas más grandes cerca de la roca profunda y las cierra cerca de la superficie
                    float threshold = thresholdBase - Math.max(0, (y - 10) * thresholdSlope);
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

    /** Determina si el bloque superficial es adecuado para generar un árbol sobre él. */
    private boolean isSurfaceBlock(Block b) {
        return b == Block.GRASS || b == Block.SAND || b == Block.DIRT;
    }

    /** Intenta colocar un árbol en la posición local del chunk indicada, si cumple las condiciones de superficie y azar. */
    private void tryPlaceTree(Chunk c, int localX, int height, int localZ, int baseX, int baseZ) {
        int worldX = baseX + localX;
        int worldZ = baseZ + localZ;
        if (shouldPlaceTree(worldX, worldZ, height)) {
            placeTree(c, localX, height + 1, localZ);
        }
    }

    private void placeTree(Chunk c, int x, int y, int z) {
        int trunk = 4;
        for (int i = 0; i < trunk; i++) {
            if (x >= 0 && x < Chunk.SIZE && z >= 0 && z < Chunk.SIZE && y + i < Chunk.HEIGHT) {
                c.set(x, y + i, z, Block.WOOD);
            }
        }
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
                for (int dy = 0; dy <= 2; dy++) {
                    if (Math.abs(dx) + Math.abs(dz) + dy <= 3) {
                        int leafX = x + dx;
                        int leafZ = z + dz;
                        if (leafX >= 0 && leafX < Chunk.SIZE && leafZ >= 0 && leafZ < Chunk.SIZE
                                && y + trunk + dy < Chunk.HEIGHT) {
                            c.set(leafX, y + trunk + dy, leafZ, Block.LEAVES);
                        }
                    }
                }
    }
}
