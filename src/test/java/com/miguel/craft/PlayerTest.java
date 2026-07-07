package com.miguel.craft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class PlayerTest {
    @Test
    void frontVectorIsNormalizedAndUsesYawPitch() {
        Player player = new Player();
        player.yaw = 0f;
        player.pitch = 0f;

        Vector3f front = player.getFront();

        assertEquals(1f, front.x, 1e-6f);
        assertEquals(0f, front.y, 1e-6f);
        assertEquals(0f, front.z, 1e-6f);
    }

    @Test
    void eyePositionIsOffsetFromBasePosition() {
        Player player = new Player();
        player.pos.set(2f, 4f, 6f);

        Vector3f eyePos = player.getEyePos();

        assertEquals(2f, eyePos.x, 1e-6f);
        assertEquals(5.6f, eyePos.y, 1e-6f);
        assertEquals(6f, eyePos.z, 1e-6f);
    }

    @Test
    void damageAndRespawnResetPlayerState() {
        Player player = new Player();
        player.pos.set(10f, 20f, 30f);
        player.health = 4f;
        player.velocity.set(3f, 4f, 5f);

        player.applyDamage(10f);

        assertEquals(Player.MAX_HEALTH, player.health, 1e-6f);
        assertEquals(player.spawnPoint.x, player.pos.x, 1e-6f);
        assertEquals(player.spawnPoint.y, player.pos.y, 1e-6f);
        assertEquals(player.spawnPoint.z, player.pos.z, 1e-6f);
        assertTrue(player.velocity.x == 0f && player.velocity.y == 0f && player.velocity.z == 0f);
    }
}
