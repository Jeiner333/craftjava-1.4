package com.miguel.craft;

public class DayNightSystem {
    private float dayTime = 60f;
    private static final float DAY_LENGTH = 240f;

    public void update(float dt) {
        dayTime = (dayTime + dt) % DAY_LENGTH;
    }

    public float getDayLightFactor() {
        return (float) (Math.sin((dayTime / DAY_LENGTH) * Math.PI * 2 - Math.PI / 2) + 1) / 2f;
    }

    public float[] getSkyColor() {
        float skyFactor = getDayLightFactor();
        float nr = 0.02f, ng = 0.02f, nb = 0.10f;
        float dr = 0.55f, dg = 0.75f, db = 0.95f;
        return new float[]{
                nr + (dr - nr) * skyFactor,
                ng + (dg - ng) * skyFactor,
                nb + (db - nb) * skyFactor
        };
    }

    public float[] getFogColor() {
        float[] sky = getSkyColor();
        return new float[]{sky[0], sky[1], sky[2], 1f};
    }
}
