package com.miguel.craft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NoiseGenTest {
    @Test
    void sameSeedProducesSameNoiseValues() {
        NoiseGen first = new NoiseGen(12345L);
        NoiseGen second = new NoiseGen(12345L);

        float firstValue = first.noise2D(3.25f, -1.5f);
        float secondValue = second.noise2D(3.25f, -1.5f);

        assertEquals(firstValue, secondValue, 1e-6f);
    }

    @Test
    void differentSeedsProduceDifferentNoiseValues() {
        NoiseGen first = new NoiseGen(111L);
        NoiseGen second = new NoiseGen(222L);

        float firstValue = first.noise3D(1.75f, 2.25f, 3.5f);
        float secondValue = second.noise3D(1.75f, 2.25f, 3.5f);

        assertFalse(Float.isNaN(firstValue));
        assertFalse(Float.isNaN(secondValue));
        assertTrue(Math.abs(firstValue - secondValue) <= 1.0f);
    }

    @Test
    void fbmRetainsFiniteValues() {
        NoiseGen noise = new NoiseGen(7L);
        float value = noise.fbm(1.5f, -2.5f, 4, 0.5f);

        assertFalse(Float.isNaN(value));
        assertFalse(Float.isInfinite(value));
    }
}
