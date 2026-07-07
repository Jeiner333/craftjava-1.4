package com.miguel.craft;

import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import static org.lwjgl.opengl.GL11.*;

public class CameraSystem {
    private final Player player;

    private static final float FOV = 70f;
    private static final float NEAR = 0.1f;
    private static final float FAR = 300f;

    public CameraSystem(Player player) {
        this.player = player;
    }

    public Vector3f getEyePos() {
        return player.getEyePos();
    }

    public Vector3f getFront() {
        return player.getFront();
    }

    /** Wrapper para OpenGL legacy. getProjection() es la única fuente de verdad de la matriz de proyección. */
    public void applyProjection(int width, int height) {
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        float[] m = new float[16];
        getProjection(width, height).get(m);
        glMultMatrixf(m);
    }

    /** Wrapper para OpenGL legacy. getView() es la única fuente de verdad de la matriz de vista. */
    public void applyView() {
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        float[] m = new float[16];
        getView().get(m);
        glMultMatrixf(m);
    }

    public Matrix4f getProjection(int width, int height) {
        return new Matrix4f().setPerspective(
                (float) Math.toRadians(FOV),
                (float) width / height,
                NEAR,
                FAR
        );
    }

    public Matrix4f getView() {
        Vector3f eye = getEyePos();
        Vector3f front = getFront();
        Vector3f center = new Vector3f(eye).add(front);
        return new Matrix4f().lookAt(eye, center, new Vector3f(0, 1, 0));
    }

    public FrustumIntersection getFrustum(int width, int height) {
        Matrix4f proj = getProjection(width, height);
        Matrix4f view = getView();
        return new FrustumIntersection(new Matrix4f(proj).mul(view));
    }
}
