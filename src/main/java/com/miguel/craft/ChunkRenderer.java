package com.miguel.craft;

import static org.lwjgl.opengl.GL11.*;

/** Dibuja un chunk usando una display list de OpenGL, regenerada solo cuando el chunk cambia.
 *  Cada vértice de cada cara se ilumina muestreando la luz (cielo + antorchas) de las celdas que
 *  tocan esa esquina, promediadas — la misma idea que el "smooth lighting" de Minecraft: el resultado
 *  es un degradado suave entre zonas iluminadas y sombra, y un efecto de oclusión ambiental gratis
 *  en las esquinas interiores (donde varias celdas vecinas están ocupadas, la luz promedio baja). */
public class ChunkRenderer {
    private int displayList = -1;

    // brillo mínimo ambiental para que nada quede completamente negro
    private static final float MIN_BRIGHTNESS = 0.12f;

    public void render(World world, Chunk chunk) {
        if (chunk.dirty || displayList == -1) {
            rebuild(world, chunk);
            chunk.dirty = false;
        }
        glCallList(displayList);
    }

    public void cleanup() {
        if (displayList != -1) {
            glDeleteLists(displayList, 1);
            displayList = -1;
        }
    }

    private void rebuild(World world, Chunk chunk) {
        if (displayList == -1) displayList = glGenLists(1);
        glNewList(displayList, GL_COMPILE);
        glBegin(GL_QUADS);
        int bx = chunk.worldX();
        int bz = chunk.worldZ();
        for (int x = 0; x < Chunk.SIZE; x++) {
            for (int y = 0; y < Chunk.HEIGHT; y++) {
                for (int z = 0; z < Chunk.SIZE; z++) {
                    Block b = chunk.get(x, y, z);
                    if (b == Block.AIR) continue;
                    int wx = bx + x, wy = y, wz = bz + z;
                    if (b == Block.TORCH) {
                        renderTorch(world, wx, wy, wz);
                    } else {
                        addBlockFaces(world, b, wx, wy, wz);
                    }
                }
            }
        }
        glEnd();
        glEndList();
    }

    private void addBlockFaces(World world, Block b, int x, int y, int z) {
        // solo dibuja caras que colindan con aire/agua/bloques transparentes (culling básico)
        if (shouldDraw(world, b, x, y + 1, z)) face(world, b, x, y, z, Face.TOP);
        if (shouldDraw(world, b, x, y - 1, z)) face(world, b, x, y, z, Face.BOTTOM);
        if (shouldDraw(world, b, x + 1, y, z)) face(world, b, x, y, z, Face.EAST);
        if (shouldDraw(world, b, x - 1, y, z)) face(world, b, x, y, z, Face.WEST);
        if (shouldDraw(world, b, x, y, z + 1)) face(world, b, x, y, z, Face.SOUTH);
        if (shouldDraw(world, b, x, y, z - 1)) face(world, b, x, y, z, Face.NORTH);
    }

    private boolean shouldDraw(World world, Block self, int nx, int ny, int nz) {
        Block neighbor = world.getBlock(nx, ny, nz);
        if (neighbor == Block.AIR) return true;
        if (neighbor.transparent) return neighbor != self; // no dibuja caras entre dos bloques transparentes iguales (ej. vidrio-vidrio)
        return false;
    }

    private enum Face { TOP, BOTTOM, NORTH, SOUTH, EAST, WEST }

    /** Componentes de la posición vecina (la celda "de aire" hacia la que mira la cara) por dirección. */
    private static int[] neighborOffset(Face f) {
        return switch (f) {
            case TOP -> new int[]{0, 1, 0};
            case BOTTOM -> new int[]{0, -1, 0};
            case EAST -> new int[]{1, 0, 0};
            case WEST -> new int[]{-1, 0, 0};
            case SOUTH -> new int[]{0, 0, 1};
            case NORTH -> new int[]{0, 0, -1};
        };
    }

    /** Vectores base (u, v) del plano de la cara, usados para ubicar las 4 esquinas. */
    private static int[][] faceBasis(Face f) {
        return switch (f) {
            case TOP, BOTTOM -> new int[][]{{1, 0, 0}, {0, 0, 1}};   // u=x, v=z
            case NORTH, SOUTH -> new int[][]{{1, 0, 0}, {0, 1, 0}};  // u=x, v=y
            case EAST, WEST -> new int[][]{{0, 0, 1}, {0, 1, 0}};    // u=z, v=y
        };
    }

    /** Para cada una de las 4 esquinas de la cara (en el mismo orden en que se emiten los vértices),
     *  el signo (su, sv) que indica hacia qué lado del vecino cae esa esquina dentro del plano u/v. */
    private static int[][] cornerSigns(Face f) {
        return switch (f) {
            case TOP -> new int[][]{{-1, -1}, {-1, 1}, {1, 1}, {1, -1}};
            case BOTTOM -> new int[][]{{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
            case NORTH -> new int[][]{{-1, -1}, {-1, 1}, {1, 1}, {1, -1}};
            case SOUTH -> new int[][]{{1, -1}, {1, 1}, {-1, 1}, {-1, -1}};
            case EAST -> new int[][]{{-1, -1}, {-1, 1}, {1, 1}, {1, -1}};
            case WEST -> new int[][]{{1, -1}, {1, 1}, {-1, 1}, {-1, -1}};
        };
    }

    /** Brillo (0..1) de una celda del mundo, con un piso ambiental para que nunca sea negro puro. */
    private float brightness(World world, int x, int y, int z) {
        int lvl = world.getLight(x, y, z);
        return MIN_BRIGHTNESS + (1f - MIN_BRIGHTNESS) * (lvl / 15f);
    }

    /** Luz suavizada en una esquina de la cara: promedia la celda vecina y las 3 celdas que comparten
     *  esa esquina en su mismo plano (vecino, lado u, lado v, diagonal). Si alguna de esas celdas está
     *  ocupada por un bloque opaco, su contribución de luz es baja, lo que oscurece la esquina — este es
     *  el mismo truco que usa Minecraft para lograr un efecto de oclusión ambiental sin calcularla aparte. */
    private float cornerBrightness(World world, int nx, int ny, int nz, int[] u, int[] v, int su, int sv) {
        float c0 = brightness(world, nx, ny, nz);
        float c1 = brightness(world, nx + su * u[0], ny + su * u[1], nz + su * u[2]);
        float c2 = brightness(world, nx + sv * v[0], ny + sv * v[1], nz + sv * v[2]);
        float c3 = brightness(world, nx + su * u[0] + sv * v[0], ny + su * u[1] + sv * v[1], nz + su * u[2] + sv * v[2]);
        return (c0 + c1 + c2 + c3) / 4f;
    }

    private void face(World world, Block b, int x, int y, int z, Face f) {
        // ligero tinte direccional (además de la luz calculada) para reforzar la sensación de volumen
        float dirTint = switch (f) {
            case TOP -> 1.0f;
            case BOTTOM -> 0.6f;
            case NORTH, SOUTH -> 0.85f;
            case EAST, WEST -> 0.75f;
        };

        int[] off = neighborOffset(f);
        int nx = x + off[0], ny = y + off[1], nz = z + off[2];
        int[][] basis = faceBasis(f);
        int[] u = basis[0], v = basis[1];
        int[][] signs = cornerSigns(f);

        float x0 = x, x1 = x + 1, y0 = y, y1 = y + 1, z0 = z, z1 = z + 1;
        float[][] verts = switch (f) {
            case TOP -> new float[][]{{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}};
            case BOTTOM -> new float[][]{{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}};
            case NORTH -> new float[][]{{x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}, {x1, y0, z0}};
            case SOUTH -> new float[][]{{x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}, {x0, y0, z1}};
            case EAST -> new float[][]{{x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}, {x1, y0, z1}};
            case WEST -> new float[][]{{x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}, {x0, y0, z0}};
        };

        for (int i = 0; i < 4; i++) {
            float corner = cornerBrightness(world, nx, ny, nz, u, v, signs[i][0], signs[i][1]);
            float lit = corner * dirTint;
            glColor3f(b.r * lit, b.g * lit, b.b * lit);
            glVertex3f(verts[i][0], verts[i][1], verts[i][2]);
        }
    }

    /** Dibuja la antorcha como un poste delgado en el centro de la celda, con una "llama" más brillante
     *  en la punta. Se ilumina siempre con un mínimo alto para que nunca se vea apagada. */
    private void renderTorch(World world, int x, int y, int z) {
        float lit = Math.max(brightness(world, x, y, z), 0.85f);
        glColor3f(Block.TORCH.r * lit, Block.TORCH.g * lit, Block.TORCH.b * lit);

        float cx = x + 0.5f, cz = z + 0.5f;
        float half = 0.08f;
        float bottom = y, top = y + 0.7f;

        quad(cx - half, bottom, cz - half, cx - half, top, cz - half, cx + half, top, cz - half, cx + half, bottom, cz - half);
        quad(cx + half, bottom, cz + half, cx + half, top, cz + half, cx - half, top, cz + half, cx - half, bottom, cz + half);
        quad(cx - half, bottom, cz + half, cx - half, top, cz + half, cx - half, top, cz - half, cx - half, bottom, cz - half);
        quad(cx + half, bottom, cz - half, cx + half, top, cz - half, cx + half, top, cz + half, cx + half, bottom, cz + half);

        glColor3f(1f, 0.85f, 0.3f); // llama, siempre brillante
        quad(cx - half, top, cz - half, cx - half, top, cz + half, cx + half, top, cz + half, cx + half, top, cz - half);
    }

    private void quad(float x1, float y1, float z1, float x2, float y2, float z2,
                       float x3, float y3, float z3, float x4, float y4, float z4) {
        glVertex3f(x1, y1, z1);
        glVertex3f(x2, y2, z2);
        glVertex3f(x3, y3, z3);
        glVertex3f(x4, y4, z4);
    }
}
