package com.miguel.craft;

public class InputState {
    private final boolean[] keys = new boolean[512];
    private final boolean[] mouseButtons = new boolean[8];
    private double lastMouseX = Double.NaN;
    private double lastMouseY = Double.NaN;
    private double deltaMouseX, deltaMouseY;

    public void setKey(int key, boolean pressed) {
        if (key >= 0 && key < keys.length) {
            keys[key] = pressed;
        }
    }

    public boolean isKeyDown(int key) {
        if (key >= 0 && key < keys.length) {
            return keys[key];
        }
        return false;
    }

    public void setMouseButton(int button, boolean pressed) {
        if (button >= 0 && button < mouseButtons.length) {
            mouseButtons[button] = pressed;
        }
    }

    public boolean isMouseButtonDown(int button) {
        if (button >= 0 && button < mouseButtons.length) {
            return mouseButtons[button];
        }
        return false;
    }

    public void updateMousePosition(double x, double y) {
        if (Double.isNaN(lastMouseX)) {
            lastMouseX = x;
            lastMouseY = y;
            deltaMouseX = 0;
            deltaMouseY = 0;
        } else {
            deltaMouseX = x - lastMouseX;
            deltaMouseY = lastMouseY - y;
            lastMouseX = x;
            lastMouseY = y;
        }
    }

    public double getMouseDeltaX() {
        return deltaMouseX;
    }

    public double getMouseDeltaY() {
        return deltaMouseY;
    }
}

