package com.miguel.craft;

import java.util.Map;

public class SaveLoadSystem {
    private static final String SAVE_PATH = "world.save";
    private final World world;
    private final WorldManager worldManager;

    public SaveLoadSystem(World world, WorldManager worldManager) {
        this.world = world;
        this.worldManager = worldManager;
    }

    public void save(Player player, Map<Block, Integer> inventory) {
        world.saveToFile(SAVE_PATH, player, inventory);
    }

    public void load(Player player, Map<Block, Integer> inventory) {
        world.loadFromFile(SAVE_PATH, player, inventory);
        worldManager.invalidateAll();
    }
}
