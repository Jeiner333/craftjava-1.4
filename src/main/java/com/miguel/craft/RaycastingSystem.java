package com.miguel.craft;

import org.joml.Vector3f;

public class RaycastingSystem {

    private final World world;

    public RaycastingSystem(World world) {
        this.world = world;
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
            if (world.isTargetable(x, y, z)) {
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
