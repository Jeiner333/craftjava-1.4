package com.miguel.craft;

import org.joml.Vector3f;
import org.joml.Matrix4f;
import org.joml.FrustumIntersection;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

import java.util.EnumMap;
import java.util.Map;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

public class Game {

    private long window;
    private int width = 1280, height = 720;
    private static final boolean VERBOSE_SAVE = Boolean.getBoolean("craft.save.verbose");

    private final World world = new World(12345L, 6); // seed, radio de renderizado en chunks
    private final Player player = new Player();
    private final PlayerController playerController = new PlayerController();
    private final MiningController miningController = new MiningController();
    private final BlockPlacementSystem blockPlacementSystem = new BlockPlacementSystem();
    private final WorldManager worldManager = new WorldManager(world);
    private final SaveLoadSystem saveLoadSystem = new SaveLoadSystem(world, worldManager);
    private final CameraSystem camera = new CameraSystem(player);
    private final WorldRenderSystem worldRenderSystem = new WorldRenderSystem();
    private final HudRenderSystem hudRenderSystem = new HudRenderSystem();
    private final DayNightSystem dayNightSystem = new DayNightSystem();
    private final InputState inputState = new InputState();

    // --- inventario y hotbar ---
    private final Block[] hotbar = {
            Block.GRASS, Block.DIRT, Block.STONE, Block.SAND,
            Block.WOOD, Block.LEAVES, Block.GLASS, Block.COAL_ORE, Block.IRON_ORE, Block.TORCH
    };
    private final Map<Block, Integer> inventory = new EnumMap<>(Block.class);
    private int hotbarIndex = 2;

    // --- persistencia ---

    private double lastFrame;

    public void run() {
        init();
        loop();
        worldManager.cleanup();
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
            inputState.setKey(key, action != GLFW_RELEASE);
            if (action != GLFW_PRESS) return;

            switch (key) {
                case GLFW_KEY_ESCAPE -> handleExitRequest(win);
                case GLFW_KEY_1, GLFW_KEY_2, GLFW_KEY_3, GLFW_KEY_4, GLFW_KEY_5,
                     GLFW_KEY_6, GLFW_KEY_7, GLFW_KEY_8, GLFW_KEY_9 -> handleHotbarSelection(key - GLFW_KEY_1);
                case GLFW_KEY_0 -> handleHotbarSelection(9);
                case GLFW_KEY_F -> handleToggleFlight();
                case GLFW_KEY_F5 -> handleSaveRequest();
                case GLFW_KEY_F9 -> handleLoadRequest();
            }
        });

        glfwSetCursorPosCallback(window, (win, xpos, ypos) -> {
            inputState.updateMousePosition(xpos, ypos);
            double dx = inputState.getMouseDeltaX();
            double dy = inputState.getMouseDeltaY();
            float sensitivity = 0.12f;
            player.yaw += dx * sensitivity;
            player.pitch += dy * sensitivity;
            player.pitch = Math.max(-89, Math.min(89, player.pitch));
        });

        // clic derecho: colocar bloque (evento único). Romper ahora se maneja por frame en handleInput
        // para poder acumular progreso de minado según la dureza del bloque.
        glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {
            boolean pressed = action == GLFW_PRESS;
            inputState.setMouseButton(button, pressed);
            if (!pressed) return;
            if (button == GLFW_MOUSE_BUTTON_RIGHT) {
                blockPlacementSystem.place(player, world, hotbar, hotbarIndex, inventory);
            }
        });

        glfwSetFramebufferSizeCallback(window, (win, w, h) -> {
            width = w; height = h;
            glViewport(0, 0, w, h);
        });

        glfwSetWindowCloseCallback(window, win -> {
            if (VERBOSE_SAVE) System.out.println("Autoguardado al cerrar...");
            try {
                saveLoadSystem.save(player, inventory);
                if (VERBOSE_SAVE) System.out.println("Autoguardado completado");
            } catch (Exception e) {
                System.err.println("Error en autoguardado: " + e.getMessage());
            }
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

        worldManager.initialize(player);
        player.pos.y = 45;
        player.spawnPoint.set(player.pos);

        lastFrame = glfwGetTime();
    }

    private void handleExitRequest(long win) {
        glfwSetWindowShouldClose(win, true);
    }

    private void handleHotbarSelection(int slot) {
        if (slot < hotbar.length) hotbarIndex = slot;
    }

    private void handleToggleFlight() {
        player.flying = !player.flying;
    }

    private void handleSaveRequest() {
        saveLoadSystem.save(player, inventory);
    }

    private void handleLoadRequest() {
        saveLoadSystem.load(player, inventory);
    }

    private void loop() {
        while (!glfwWindowShouldClose(window)) {
            double now = glfwGetTime();
            float dt = (float) Math.min(now - lastFrame, 0.05);
            lastFrame = now;

            dayNightSystem.update(dt);

            handleInput(dt);
            handleMining(dt);
            worldManager.update(player);

            // caer al vacío también hace respawn, como una muerte por caída fuera del mundo
            if (player.pos.y < -10) player.respawn();

            render();

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    private void handleInput(float dt) {
        playerController.update(player, world, inputState, dt);
    }

    /** Minado progresivo: mientras se mantenga presionado el clic izquierdo sobre el mismo bloque,
     *  acumula tiempo hasta superar su dureza; entonces lo rompe y añade el material al inventario. */
    private void handleMining(float dt) {
        miningController.update(inputState, player, world, inventory, dt);
    }

    private void render() {
        float[] skyColor = dayNightSystem.getSkyColor();
        glClearColor(skyColor[0], skyColor[1], skyColor[2], 1f);
        float[] fogColor = dayNightSystem.getFogColor();
        glFogfv(GL_FOG_COLOR, fogColor);

        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        camera.applyProjection(width, height);
        camera.applyView();

        Matrix4f proj = camera.getProjection(width, height);
        Matrix4f view = camera.getView();
        FrustumIntersection frustum = camera.getFrustum(width, height);

        worldRenderSystem.render(world, worldManager, frustum);

        hudRenderSystem.render(world, width, height, hotbar, hotbarIndex, inventory, player.health, miningController.getMiningState());
    }
}
