package com.miguel.craft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class ChunkTest {
    @Test
    void setAndGetRoundTripInsideBounds() {
        Chunk chunk = new Chunk(1, 2);

        chunk.set(3, 4, 5, Block.STONE);

        assertSame(Block.STONE, chunk.get(3, 4, 5));
        assertEquals(16, chunk.worldX());
        assertEquals(32, chunk.worldZ());
    }

    @Test
    void outOfBoundsAccessDoesNotMutateChunk() {
        Chunk chunk = new Chunk(0, 0);

        chunk.set(-1, 0, 0, Block.DIRT);
        chunk.set(0, -1, 0, Block.DIRT);
        chunk.set(0, 0, Chunk.SIZE, Block.DIRT);

        assertSame(Block.AIR, chunk.get(-1, 0, 0));
        assertSame(Block.AIR, chunk.get(0, -1, 0));
        assertSame(Block.AIR, chunk.get(0, 0, Chunk.SIZE));
    }

    @Test
    void lightValuesClampBetweenZeroAndFifteen() {
        Chunk chunk = new Chunk(0, 0);

        chunk.setLight(0, 0, 0, 100);
        chunk.setLight(1, 0, 0, -5);

        assertEquals(15, chunk.getLight(0, 0, 0));
        assertEquals(0, chunk.getLight(1, 0, 0));
    }
}
