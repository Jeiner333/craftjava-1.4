package com.miguel.craft;

public class MiningState {
    public final int x;
    public final int y;
    public final int z;
    public float progress;

    public MiningState(int x, int y, int z, float progress) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.progress = progress;
    }
}
