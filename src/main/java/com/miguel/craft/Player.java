package com.miguel.craft;

import org.joml.Vector3f;

public class Player {
    public Vector3f pos = new Vector3f(0, 40, 0);
    public Vector3f velocity = new Vector3f(0, 0, 0);
    public float yaw = -90, pitch = 0;
    public boolean onGround = false;
    public boolean flying = false;

    // dimensiones del "hitbox" del jugador
    private static final float WIDTH = 0.3f;
    private static final float HEIGHT = 1.8f;
    private static final float EYE_HEIGHT = 1.6f;

    public static final float GRAVITY = -25f;
    public static final float JUMP_SPEED = 8f;
    public static final float MOVE_SPEED = 5.2f;
    public static final float SPRINT_SPEED = 8.0f;
    public static final float FLY_SPEED = 9f;

    public float health = 20f;
    public static final float MAX_HEALTH = 20f;
    private float fallStartY;
    private boolean wasOnGround = true;
    public Vector3f spawnPoint = new Vector3f(0, 45, 0);

    public Vector3f getFront() {
        float yawR = (float) Math.toRadians(yaw);
        float pitchR = (float) Math.toRadians(pitch);
        return new Vector3f(
                (float) (Math.cos(yawR) * Math.cos(pitchR)),
                (float) Math.sin(pitchR),
                (float) (Math.sin(yawR) * Math.cos(pitchR))
        ).normalize();
    }

    public Vector3f getEyePos() {
        return new Vector3f(pos.x, pos.y + EYE_HEIGHT, pos.z);
    }

    /** Actualiza física: gravedad, movimiento deseado y resolución de colisiones eje por eje. */
    public void update(World world, Vector3f wishDir, boolean jump, float dt) {
        if (flying) {
            velocity.x = wishDir.x;
            velocity.y = wishDir.y;
            velocity.z = wishDir.z;
        } else {
            velocity.x = wishDir.x;
            velocity.z = wishDir.z;
            velocity.y += GRAVITY * dt;
            if (jump && onGround) {
                velocity.y = JUMP_SPEED;
            }
        }

        moveAndCollide(world, velocity.x * dt, 0, 0, false);
        onGround = false;
        moveAndCollide(world, 0, velocity.y * dt, 0, true);
        moveAndCollide(world, 0, 0, velocity.z * dt, false);

        if (!flying) {
            if (onGround && !wasOnGround) {
                float fallDistance = fallStartY - pos.y;
                if (fallDistance > 3.5f) {
                    applyDamage((fallDistance - 3.5f) * 2.2f);
                }
            }
            if (!onGround && wasOnGround) {
                fallStartY = pos.y;
            }
            wasOnGround = onGround;
        }
    }

    public void applyDamage(float amount) {
        health = Math.max(0, health - amount);
        if (health <= 0) respawn();
    }

    public void respawn() {
        pos.set(spawnPoint);
        velocity.set(0, 0, 0);
        health = MAX_HEALTH;
    }

    private void moveAndCollide(World world, float dx, float dy, float dz, boolean verticalAxis) {
        pos.x += dx; pos.y += dy; pos.z += dz;

        if (collides(world)) {
            // deshacer el movimiento en el eje que causó la colisión
            pos.x -= dx; pos.y -= dy; pos.z -= dz;
            if (verticalAxis) {
                if (dy < 0) onGround = true;
                velocity.y = 0;
            } else {
                if (dx != 0) velocity.x = 0;
                if (dz != 0) velocity.z = 0;
            }
        }
    }

    private boolean collides(World world) {
        float minX = pos.x - WIDTH, maxX = pos.x + WIDTH;
        float minY = pos.y, maxY = pos.y + HEIGHT;
        float minZ = pos.z - WIDTH, maxZ = pos.z + WIDTH;
        for (int x = (int) Math.floor(minX); x <= (int) Math.floor(maxX); x++)
            for (int y = (int) Math.floor(minY); y <= (int) Math.floor(maxY); y++)
                for (int z = (int) Math.floor(minZ); z <= (int) Math.floor(maxZ); z++)
                    if (world.isSolid(x, y, z)) return true;
        return false;
    }
}
