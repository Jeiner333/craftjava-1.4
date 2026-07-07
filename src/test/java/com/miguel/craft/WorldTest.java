package com.miguel.craft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class WorldTest {
    @Test
    void setBlockAndGetBlockWorkForLoadedChunks() {
        World world = new World(1L, 1);

        world.setBlock(0, 10, 0, Block.STONE);

        assertSame(Block.STONE, world.getBlock(0, 10, 0));
        assertSame(Block.AIR, world.getBlock(0, -1, 0));
    }

    @Test
    void raycastFindsTheNearestTargetableBlock() {
        World world = new World(2L, 1);
        world.setBlock(0, 13, 0, Block.STONE);

        World.RayHit hit = world.raycast(new Vector3f(0.5f, 14f, 0.5f), new Vector3f(0f, -1f, 0f), 5f);

        assertTrue(hit.hit);
        assertEquals(0, hit.x);
        assertEquals(14, hit.y);
        assertEquals(0, hit.z);
    }
}
