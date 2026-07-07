package com.miguel.craft;

import org.joml.Vector3f;

import java.util.Map;

import static org.lwjgl.glfw.GLFW.*;

public class MiningController {

    private MiningState miningState;

    public void update(InputState inputState, Player player, World world, Map<Block, Integer> inventory, float dt) {
        boolean mining = inputState.isMouseButtonDown(GLFW_MOUSE_BUTTON_LEFT);
        if (!mining) {
            miningState = null;
            return;
        }

        Vector3f eye = player.getEyePos();
        Vector3f dir = player.getFront();
        World.RayHit hit = world.raycast(eye, dir, 6f);
        if (!hit.hit) {
            miningState = null;
            return;
        }

        Block target = world.getBlock(hit.x, hit.y, hit.z);
        if (target.isUnbreakable() || target == Block.AIR || target == Block.WATER) {
            miningState = null;
            return;
        }

        if (miningState != null && miningState.x == hit.x && miningState.y == hit.y && miningState.z == hit.z) {
            miningState.progress += dt;
        } else {
            miningState = new MiningState(hit.x, hit.y, hit.z, 0f);
        }

        if (miningState.progress >= Math.max(target.hardness, 0.05f)) {
            world.setBlock(hit.x, hit.y, hit.z, Block.AIR);
            if (target.collectible) {
                inventory.merge(target, 1, (a, b) -> Math.min(Block.MAX_STACK, a + b));
            }
            miningState = null;
        }
    }

    public MiningState getMiningState() {
        return miningState;
    }
}
