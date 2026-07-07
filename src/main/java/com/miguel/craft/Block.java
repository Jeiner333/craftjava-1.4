package com.miguel.craft;

public enum Block {
    AIR(0, 0, 0, true, 0f, false, 0),
    GRASS(0.30f, 0.65f, 0.25f, false, 0.6f, true, 0),
    DIRT(0.45f, 0.30f, 0.15f, false, 0.5f, true, 0),
    STONE(0.5f, 0.5f, 0.5f, false, 1.2f, true, 0),
    COBBLESTONE(0.42f, 0.42f, 0.42f, false, 1.4f, true, 0),
    SAND(0.85f, 0.80f, 0.55f, false, 0.5f, true, 0),
    WOOD(0.40f, 0.25f, 0.10f, false, 0.8f, true, 0),
    LEAVES(0.15f, 0.45f, 0.10f, true, 0.2f, true, 0),
    WATER(0.15f, 0.35f, 0.85f, true, 0f, false, 0),
    GLASS(0.75f, 0.90f, 0.92f, true, 0.3f, true, 0),
    COAL_ORE(0.20f, 0.20f, 0.20f, false, 1.5f, true, 0),
    IRON_ORE(0.70f, 0.55f, 0.45f, false, 1.8f, true, 0),
    TORCH(1.0f, 0.65f, 0.15f, true, 0.2f, true, 15),
    BEDROCK(0.08f, 0.08f, 0.08f, false, -1f, false, 0);

    public final float r, g, b;
    /** Si el bloque deja pasar la luz (y por lo tanto no oculta las caras vecinas). */
    public final boolean transparent;
    /** Segundos que toma romper el bloque a mano. -1 significa irrompible (bedrock). */
    public final float hardness;
    /** Si al romperlo se puede recoger y luego colocar. */
    public final boolean collectible;
    /** Nivel de luz que emite (0-15). Solo TORCH emite luz por ahora. */
    public final int lightEmission;
    /** Tamaño máximo de pila para cualquier bloque en el inventario. */
    public static final int MAX_STACK = 64;

    Block(float r, float g, float b, boolean transparent, float hardness, boolean collectible, int lightEmission) {
        this.r = r;
        this.g = g;
        this.b = b;
        this.transparent = transparent;
        this.hardness = hardness;
        this.collectible = collectible;
        this.lightEmission = lightEmission;
    }

    public boolean isUnbreakable() {
        return hardness < 0f;
    }

    public static Block[] VALUES = values();
}
