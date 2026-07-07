package com.miguel.craft;

import java.util.Random;

/** Ruido tipo "value noise" suavizado, suficiente para generar terreno creíble sin dependencias externas. */
public class NoiseGen {
    private final int[] perm = new int[512];

    public NoiseGen(long seed) {
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) p[i] = i;
        Random rnd = new Random(seed);
        for (int i = 255; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int tmp = p[i]; p[i] = p[j]; p[j] = tmp;
        }
        for (int i = 0; i < 512; i++) perm[i] = p[i & 255];
    }

    private static float fade(float t) { return t * t * t * (t * (t * 6 - 15) + 10); }
    private static float lerp(float t, float a, float b) { return a + t * (b - a); }

    private static float grad(int hash, float x, float y) {
        int h = hash & 3;
        float u = h < 2 ? x : y;
        float v = h < 2 ? y : x;
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }

    public float noise2D(float x, float y) {
        int X = (int) Math.floor(x) & 255;
        int Y = (int) Math.floor(y) & 255;
        x -= Math.floor(x);
        y -= Math.floor(y);
        float u = fade(x);
        float v = fade(y);
        int aa = perm[perm[X] + Y];
        int ab = perm[perm[X] + Y + 1];
        int ba = perm[perm[X + 1] + Y];
        int bb = perm[perm[X + 1] + Y + 1];
        float res = lerp(v,
                lerp(u, grad(aa, x, y), grad(ba, x - 1, y)),
                lerp(u, grad(ab, x, y - 1), grad(bb, x - 1, y - 1)));
        return res;
    }

    /** Ruido con varias octavas para terreno más natural. */
    public float fbm(float x, float y, int octaves, float persistence) {
        float total = 0, freq = 1, amp = 1, maxVal = 0;
        for (int i = 0; i < octaves; i++) {
            total += noise2D(x * freq, y * freq) * amp;
            maxVal += amp;
            amp *= persistence;
            freq *= 2;
        }
        return total / maxVal;
    }

    /** Ruido 3D tipo "value noise" con interpolación trilineal, usado para tallar cuevas y menas.
     *  Es más barato que Perlin 3D pero produce túneles coherentes en vez de ruido disperso bloque a bloque. */
    private float hash3(int x, int y, int z) {
        long h = x * 374761393L + y * 668265263L + z * 2147483647L;
        h = (h ^ (h >> 13)) * 1274126177L;
        h ^= (h >> 16);
        return (Math.abs(h % 20000) / 20000f) * 2f - 1f; // rango [-1, 1]
    }

    public float noise3D(float x, float y, float z) {
        int X = (int) Math.floor(x), Y = (int) Math.floor(y), Z = (int) Math.floor(z);
        float fx = x - X, fy = y - Y, fz = z - Z;
        float u = fade(fx), v = fade(fy), w = fade(fz);

        float c000 = hash3(X, Y, Z),     c100 = hash3(X + 1, Y, Z);
        float c010 = hash3(X, Y + 1, Z), c110 = hash3(X + 1, Y + 1, Z);
        float c001 = hash3(X, Y, Z + 1), c101 = hash3(X + 1, Y, Z + 1);
        float c011 = hash3(X, Y + 1, Z + 1), c111 = hash3(X + 1, Y + 1, Z + 1);

        float x00 = lerp(u, c000, c100), x10 = lerp(u, c010, c110);
        float x01 = lerp(u, c001, c101), x11 = lerp(u, c011, c111);
        float y0 = lerp(v, x00, x10), y1 = lerp(v, x01, x11);
        return lerp(w, y0, y1);
    }
}
