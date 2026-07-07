package com.miguel.craft;

import org.joml.Vector3f;

import static org.lwjgl.glfw.GLFW.*;

public class PlayerController {
    public void update(Player player, World world, InputState inputState, float dt) {
        Vector3f front = player.getFront();
        Vector3f flatFront = new Vector3f(front.x, 0, front.z).normalize();
        Vector3f right = new Vector3f(-flatFront.z, 0, flatFront.x);

        Vector3f wish = new Vector3f();
        boolean sprinting = inputState.isKeyDown(GLFW_KEY_LEFT_CONTROL) && inputState.isKeyDown(GLFW_KEY_W) && !player.flying;
        float speed = player.flying ? Player.FLY_SPEED : (sprinting ? Player.SPRINT_SPEED : Player.MOVE_SPEED);

        if (inputState.isKeyDown(GLFW_KEY_W)) wish.add(flatFront);
        if (inputState.isKeyDown(GLFW_KEY_S)) wish.sub(flatFront);
        if (inputState.isKeyDown(GLFW_KEY_D)) wish.add(right);
        if (inputState.isKeyDown(GLFW_KEY_A)) wish.sub(right);
        if (wish.lengthSquared() > 0) wish.normalize().mul(speed);

        if (player.flying) {
            if (inputState.isKeyDown(GLFW_KEY_SPACE)) wish.y = speed;
            if (inputState.isKeyDown(GLFW_KEY_LEFT_SHIFT)) wish.y = -speed;
        }

        boolean jump = inputState.isKeyDown(GLFW_KEY_SPACE);
        player.update(world, wish, jump, dt);
    }
}
