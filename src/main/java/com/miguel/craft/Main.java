package com.miguel.craft;

import org.joml.Vector3f;
import org.joml.Matrix4f;
import org.joml.FrustumIntersection;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

public class Main {

    private long window;
    private int width = 1280, height = 720;

    private final World world = new World(12345L, 6); // seed, radio de renderizado en chunks
    private final Player player = new Player();
    private final Map<Long, ChunkRenderer> renderers = new HashMap<>();

    private double lastMouseX, lastMouseY;
    private boolean firstMouse = true;
    private final boolean[] keys = new boolean[512];

    // --- inventario y hotbar ---
    private final Block[] hotbar = {
            Block.GRASS, Block.DIRT, Block.STONE, Block.SAND,
            Block.WOOD, Block.LEAVES, Block.GLASS, Block.COAL_ORE, Block.IRON_ORE, Block.TORCH
    };
    private final Map<Block, Integer> inventory = new EnumMap<>(Block.class);
    private int hotbarIndex = 2;
    private static final int MAX_STACK = 64;

    // --- minado con progreso ---
    private Integer breakX, breakY, breakZ;
    private float breakProgress = 0f;

    // --- ciclo día/noche ---
    private float dayTime = 60f; // segundos transcurridos del ciclo; arranca cerca del mediodía
    private static final float DAY_LENGTH = 240f; // segundos para un día completo

    // --- persistencia ---
    private static final String SAVE_PATH = "world.save";

    private double lastFrame;

    public static void main(String[] args) {
        new Main().run();
    }

    public void run() {
        init();
        loop();
        for (ChunkRenderer r : renderers.values()) {
            r.cleanup();
        }
        renderers.clear();
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        glfwTerminate();
    }

    private void init() {
        if (!glfwInit()) throw new IllegalStateException("No se pudo inicializar GLFW");

        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        window = glfwCreateWindow(width, height, "CraftJava", MemoryUtil.NULL, MemoryUtil.NULL);
        if (window == MemoryUtil.NULL) throw new RuntimeException("No se pudo crear la ventana");

        glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
            if (key >= 0 && key < keys.length) keys[key] = action != GLFW_RELEASE;
            if (key == GLFW_KEY_ESCAPE && action == GLFW_PRESS) glfwSetWindowShouldClose(win, true);
            if (action == GLFW_PRESS && key >= GLFW_KEY_1 && key <= GLFW_KEY_9) {
                int idx = key - GLFW_KEY_1;
                if (idx < hotbar.length) hotbarIndex = idx;
            }
            if (action == GLFW_PRESS && key == GLFW_KEY_0 && hotbar.length > 9) {
                hotbarIndex = 9;
            }
            if (key == GLFW_KEY_F && action == GLFW_PRESS) player.flying = !player.flying;
            if (key == GLFW_KEY_F5 && action == GLFW_PRESS) world.saveToFile(SAVE_PATH, player, inventory);
            if (key == GLFW_KEY_F9 && action == GLFW_PRESS) {
                world.loadFromFile(SAVE_PATH, player, inventory);
                renderers.clear(); // fuerza reconstrucción de todas las mallas tras cargar
            }
        });

        glfwSetCursorPosCallback(window, (win, xpos, ypos) -> {
            if (firstMouse) { lastMouseX = xpos; lastMouseY = ypos; firstMouse = false; }
            double dx = xpos - lastMouseX;
            double dy = lastMouseY - ypos;
            lastMouseX = xpos; lastMouseY = ypos;
            float sensitivity = 0.12f;
            player.yaw += dx * sensitivity;
            player.pitch += dy * sensitivity;
            player.pitch = Math.max(-89, Math.min(89, player.pitch));
        });

        // clic derecho: colocar bloque (evento único). Romper ahora se maneja por frame en handleInput
        // para poder acumular progreso de minado según la dureza del bloque.
        glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {
            if (action != GLFW_PRESS) return;
            if (button == GLFW_MOUSE_BUTTON_RIGHT) {
                Vector3f eye = player.getEyePos();
                Vector3f dir = player.getFront();
                World.RayHit hit = world.raycast(eye, dir, 6f);
                if (!hit.hit) return;
                Block toPlace = hotbar[hotbarIndex];
                if (inventory.getOrDefault(toPlace, 0) <= 0) return; // sin material, no se puede colocar
                world.setBlock(hit.x + hit.nx, hit.y + hit.ny, hit.z + hit.nz, toPlace);
                inventory.put(toPlace, inventory.get(toPlace) - 1);
            }
        });

        glfwSetFramebufferSizeCallback(window, (win, w, h) -> {
            width = w; height = h;
            glViewport(0, 0, w, h);
        });

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
        glfwShowWindow(window);

        GL.createCapabilities();
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);

        // niebla: oculta el "pop-in" de chunks lejanos y refuerza el ambiente día/noche
        glEnable(GL_FOG);
        glFogi(GL_FOG_MODE, GL_LINEAR);
        float renderDist = world.renderDistance * Chunk.SIZE;
        glFogf(GL_FOG_START, renderDist * 0.55f);
        glFogf(GL_FOG_END, renderDist * 0.95f);

        world.ensureChunksAround((int) player.pos.x, (int) player.pos.z);
        player.pos.y = 45;
        player.spawnPoint.set(player.pos);

        lastFrame = glfwGetTime();
    }

    private void loop() {
        while (!glfwWindowShouldClose(window)) {
            double now = glfwGetTime();
            float dt = (float) Math.min(now - lastFrame, 0.05);
            lastFrame = now;

            dayTime = (dayTime + dt) % DAY_LENGTH;

            handleInput(dt);
            handleMining(dt);
            world.ensureChunksAround((int) player.pos.x, (int) player.pos.z);
            unloadFarChunks();

            // caer al vacío también hace respawn, como una muerte por caída fuera del mundo
            if (player.pos.y < -10) player.respawn();

            render();

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    private void handleInput(float dt) {
        Vector3f front = player.getFront();
        Vector3f flatFront = new Vector3f(front.x, 0, front.z).normalize();
        Vector3f right = new Vector3f(-flatFront.z, 0, flatFront.x);

        Vector3f wish = new Vector3f();
        boolean sprinting = keys[GLFW_KEY_LEFT_CONTROL] && keys[GLFW_KEY_W] && !player.flying;
        float speed = player.flying ? Player.FLY_SPEED : (sprinting ? Player.SPRINT_SPEED : Player.MOVE_SPEED);

        if (keys[GLFW_KEY_W]) wish.add(flatFront);
        if (keys[GLFW_KEY_S]) wish.sub(flatFront);
        if (keys[GLFW_KEY_D]) wish.add(right);
        if (keys[GLFW_KEY_A]) wish.sub(right);
        if (wish.lengthSquared() > 0) wish.normalize().mul(speed);

        if (player.flying) {
            if (keys[GLFW_KEY_SPACE]) wish.y = speed;
            if (keys[GLFW_KEY_LEFT_SHIFT]) wish.y = -speed;
        }

        boolean jump = keys[GLFW_KEY_SPACE];
        player.update(world, wish, jump, dt);
    }

    /** Minado progresivo: mientras se mantenga presionado el clic izquierdo sobre el mismo bloque,
     *  acumula tiempo hasta superar su dureza; entonces lo rompe y añade el material al inventario. */
    private void handleMining(float dt) {
        boolean mining = glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_LEFT) == GLFW_PRESS;
        if (!mining) {
            breakX = null;
            breakProgress = 0;
            return;
        }

        Vector3f eye = player.getEyePos();
        Vector3f dir = player.getFront();
        World.RayHit hit = world.raycast(eye, dir, 6f);
        if (!hit.hit) {
            breakX = null;
            breakProgress = 0;
            return;
        }

        Block target = world.getBlock(hit.x, hit.y, hit.z);
        if (target.isUnbreakable() || target == Block.AIR || target == Block.WATER) {
            breakX = null;
            breakProgress = 0;
            return;
        }

        boolean sameTarget = breakX != null && breakX == hit.x && breakY == hit.y && breakZ == hit.z;
        if (!sameTarget) {
            breakX = hit.x; breakY = hit.y; breakZ = hit.z;
            breakProgress = 0;
        }
        breakProgress += dt;

        if (breakProgress >= Math.max(target.hardness, 0.05f)) {
            world.setBlock(hit.x, hit.y, hit.z, Block.AIR);
            if (target.collectible) {
                inventory.merge(target, 1, (a, b) -> Math.min(MAX_STACK, a + b));
            }
            breakX = null;
            breakProgress = 0;
        }
    }

    private void unloadFarChunks() {
        int cx = Math.floorDiv((int) player.pos.x, Chunk.SIZE);
        int cz = Math.floorDiv((int) player.pos.z, Chunk.SIZE);
        int limit = world.renderDistance + 2;

        java.util.List<Long> toRemove = new java.util.ArrayList<>();
        for (Chunk c : world.loadedChunks()) {
            if (Math.abs(c.chunkX - cx) > limit || Math.abs(c.chunkZ - cz) > limit) {
                toRemove.add((((long) c.chunkX) << 32) ^ (c.chunkZ & 0xffffffffL));
            }
        }

        for (long key : toRemove) {
            ChunkRenderer r = renderers.remove(key);
            if (r != null) {
                r.cleanup();
            }
            world.removeChunk(key);
        }
    }

    private void render() {
        // color de cielo según la hora del día (curva suave entre noche y día)
        float skyFactor = (float) (Math.sin((dayTime / DAY_LENGTH) * Math.PI * 2 - Math.PI / 2) + 1) / 2f;
        float nr = 0.02f, ng = 0.02f, nb = 0.10f; // color de noche
        float dr = 0.55f, dg = 0.75f, db = 0.95f; // color de día
        float skyR = nr + (dr - nr) * skyFactor;
        float skyG = ng + (dg - ng) * skyFactor;
        float skyB = nb + (db - nb) * skyFactor;
        glClearColor(skyR, skyG, skyB, 1f);
        float[] fogColor = {skyR, skyG, skyB, 1f};
        glFogfv(GL_FOG_COLOR, fogColor);

        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        float aspect = (float) width / height;
        perspective(70f, aspect, 0.1f, 300f);

        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        Vector3f eye = player.getEyePos();
        Vector3f front = player.getFront();
        Vector3f center = new Vector3f(eye).add(front);
        lookAt(eye, center, new Vector3f(0, 1, 0));

        Matrix4f proj = new Matrix4f().setPerspective((float) Math.toRadians(70f), aspect, 0.1f, 300f);
        Matrix4f view = new Matrix4f().lookAt(eye, center, new Vector3f(0, 1, 0));
        Matrix4f projView = new Matrix4f(proj).mul(view);
        FrustumIntersection frustum = new FrustumIntersection(projView);

        for (Chunk c : world.loadedChunks()) {
            long key = (((long) c.chunkX) << 32) ^ (c.chunkZ & 0xffffffffL);
            float minX = c.worldX();
            float minZ = c.worldZ();
            float maxX = minX + Chunk.SIZE;
            float maxZ = minZ + Chunk.SIZE;
            
            if (frustum.testAab(minX, 0, minZ, maxX, Chunk.HEIGHT, maxZ)) {
                ChunkRenderer r = renderers.computeIfAbsent(key, k -> new ChunkRenderer());
                r.render(world, c);
            }
        }

        renderHud();
    }

    /** Overlay 2D: hotbar con cantidades, barra de vida, indicador de progreso de minado y mira. */
    private void renderHud() {
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glDisable(GL_FOG);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, width, height, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();

        int slotSize = 48;
        int padding = 6;
        int totalWidth = hotbar.length * (slotSize + padding) - padding;
        int startX = (width - totalWidth) / 2;
        int hotbarY = height - slotSize - 20;

        // barra de vida arriba de la hotbar
        int barWidth = 200, barHeight = 14;
        int barX = (width - barWidth) / 2;
        int barY = hotbarY - barHeight - 10;
        glColor4f(0.15f, 0.15f, 0.15f, 0.6f);
        drawQuad(barX, barY, barWidth, barHeight);
        float healthFrac = player.health / Player.MAX_HEALTH;
        glColor3f(0.85f - 0.4f * healthFrac, 0.15f + 0.6f * healthFrac, 0.15f);
        drawQuad(barX + 2, barY + 2, (barWidth - 4) * healthFrac, barHeight - 4);

        for (int i = 0; i < hotbar.length; i++) {
            int x = startX + i * (slotSize + padding);
            boolean selected = i == hotbarIndex;

            glColor4f(0.1f, 0.1f, 0.1f, 0.55f);
            drawQuad(x, hotbarY, slotSize, slotSize);

            if (selected) glColor3f(1f, 1f, 1f); else glColor3f(0.4f, 0.4f, 0.4f);
            drawQuadOutline(x, hotbarY, slotSize, slotSize, selected ? 3 : 1);

            Block b = hotbar[i];
            int count = inventory.getOrDefault(b, 0);
            float alpha = count > 0 ? 1f : 0.25f; // se ve "apagado" si no tienes ninguno
            glColor4f(b.r, b.g, b.b, alpha);
            int m = 8;
            drawQuad(x + m, hotbarY + m, slotSize - 2 * m, slotSize - 2 * m);

            // mini barra de cantidad (relativa a un stack de 64) en la base de la casilla
            if (count > 0) {
                float frac = Math.min(1f, count / (float) MAX_STACK);
                glColor3f(1f, 1f, 1f);
                drawQuad(x + 4, hotbarY + slotSize - 6, (slotSize - 8) * frac, 3);
            }
        }

        // progreso de minado sobre la casilla seleccionada (barra encima de la hotbar central)
        if (breakX != null) {
            Block target = world.getBlock(breakX, breakY, breakZ);
            float frac = Math.min(1f, breakProgress / Math.max(target.hardness, 0.05f));
            int mw = 120, mh = 8;
            int mx = (width - mw) / 2, my = hotbarY - barHeight - 30;
            glColor4f(0.1f, 0.1f, 0.1f, 0.6f);
            drawQuad(mx, my, mw, mh);
            glColor3f(0.9f, 0.9f, 0.2f);
            drawQuad(mx + 1, my + 1, (mw - 2) * frac, mh - 2);
        }

        // mira central
        glColor3f(1f, 1f, 1f);
        int cx = width / 2, cy = height / 2, crossSize = 8;
        drawQuad(cx - crossSize / 2f, cy - 1, crossSize, 2);
        drawQuad(cx - 1, cy - crossSize / 2f, 2, crossSize);

        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glEnable(GL_FOG);
        glDisable(GL_BLEND);
    }

    private void drawQuad(float x, float y, float w, float h) {
        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x + w, y);
        glVertex2f(x + w, y + h);
        glVertex2f(x, y + h);
        glEnd();
    }

    private void drawQuadOutline(float x, float y, float w, float h, float thickness) {
        glLineWidth(thickness);
        glBegin(GL_LINE_LOOP);
        glVertex2f(x, y);
        glVertex2f(x + w, y);
        glVertex2f(x + w, y + h);
        glVertex2f(x, y + h);
        glEnd();
    }

    // --- utilidades matemáticas mínimas para reemplazar gluPerspective/gluLookAt (deprecados) ---

    private void perspective(float fovYdeg, float aspect, float near, float far) {
        float fovRad = (float) Math.toRadians(fovYdeg);
        float f = (float) (1.0 / Math.tan(fovRad / 2.0));
        float[] m = new float[16];
        m[0] = f / aspect;
        m[5] = f;
        m[10] = (far + near) / (near - far);
        m[11] = -1;
        m[14] = (2 * far * near) / (near - far);
        glMultMatrixf(m);
    }

    private void lookAt(Vector3f eye, Vector3f center, Vector3f up) {
        Vector3f f = new Vector3f(center).sub(eye).normalize();
        Vector3f s = new Vector3f(f).cross(up).normalize();
        Vector3f u = new Vector3f(s).cross(f);
        float[] m = new float[]{
                s.x, u.x, -f.x, 0,
                s.y, u.y, -f.y, 0,
                s.z, u.z, -f.z, 0,
                0, 0, 0, 1
        };
        glMultMatrixf(m);
        glTranslatef(-eye.x, -eye.y, -eye.z);
    }
}
