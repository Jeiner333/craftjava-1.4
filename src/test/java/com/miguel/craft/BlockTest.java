package com.miguel.craft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BlockTest {
    @Test
    void unbreakableBlocksAreDetected() {
        assertTrue(Block.BEDROCK.isUnbreakable());
        assertFalse(Block.GRASS.isUnbreakable());
    }

    @Test
    void blockPropertiesRemainAccessible() {
        Block block = Block.TORCH;
        assertEquals(1.0f, block.r, 1e-6f);
        assertEquals(0.65f, block.g, 1e-6f);
        assertEquals(0.15f, block.b, 1e-6f);
        assertTrue(block.transparent);
        assertTrue(block.collectible);
        assertEquals(15, block.lightEmission);
    }
}
