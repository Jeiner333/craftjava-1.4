package com.miguel.craft;

import org.joml.Vector3f;

import java.util.Map;

public class BlockPlacementSystem {
    public void place(Player player, World world, Block[] hotbar, int hotbarIndex, Map<Block, Integer> inventory) {
        Vector3f eye = player.getEyePos();
        Vector3f dir = player.getFront();
        World.RayHit hit = world.raycast(eye, dir, 6f);
        if (!hit.hit) return;
        Block toPlace = hotbar[hotbarIndex];
        if (inventory.getOrDefault(toPlace, 0) <= 0) return;
        world.setBlock(hit.x + hit.nx, hit.y + hit.ny, hit.z + hit.nz, toPlace);
        inventory.put(toPlace, inventory.get(toPlace) - 1);
    }
}
