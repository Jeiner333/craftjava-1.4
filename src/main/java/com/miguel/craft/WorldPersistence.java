package com.miguel.craft;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class WorldPersistence {
    private final World world;

    public WorldPersistence(World world) {
        this.world = world;
    }

    /** Guarda todos los chunks cargados, el estado del jugador y el inventario en un archivo binario. */
    public void saveToFile(String path, Player player, Map<Block, Integer> inventory) {
        try (var out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(path)))) {
            out.writeInt(-1);

            out.writeFloat(player.pos.x);
            out.writeFloat(player.pos.y);
            out.writeFloat(player.pos.z);
            out.writeFloat(player.yaw);
            out.writeFloat(player.pitch);
            out.writeFloat(player.health);
            out.writeBoolean(player.flying);

            Map<Block, Integer> activeInv = new HashMap<>();
            for (var entry : inventory.entrySet()) {
                if (entry.getValue() != null && entry.getValue() > 0) {
                    activeInv.put(entry.getKey(), entry.getValue());
                }
            }
            out.writeInt(activeInv.size());
            for (var entry : activeInv.entrySet()) {
                out.writeByte(entry.getKey().ordinal());
                out.writeInt(entry.getValue());
            }

            out.writeInt(world.chunks.size());
            for (Chunk c : world.chunks.values()) {
                out.writeInt(c.chunkX);
                out.writeInt(c.chunkZ);
                for (int x = 0; x < Chunk.SIZE; x++)
                    for (int y = 0; y < Chunk.HEIGHT; y++)
                        for (int z = 0; z < Chunk.SIZE; z++)
                            out.writeByte(c.get(x, y, z).ordinal());
            }
            System.out.println("Mundo guardado en " + path + " (" + world.chunks.size() + " chunks, jugador e inventario guardados)");
        } catch (IOException e) {
            System.err.println("Error guardando el mundo: " + e.getMessage());
        }
    }

    /** Carga el mundo, el jugador y el inventario desde un archivo previamente guardado. */
    public void loadFromFile(String path, Player player, Map<Block, Integer> inventory) {
        File f = new File(path);
        if (!f.exists()) {
            System.out.println("No hay partida guardada en " + path);
            return;
        }
        try (var in = new DataInputStream(new BufferedInputStream(new FileInputStream(f)))) {
            world.chunks.clear();
            int firstInt = in.readInt();
            int count;
            Block[] values = Block.VALUES;

            if (firstInt < 0) {
                int version = -firstInt;
                if (version == 1) {
                    player.pos.x = in.readFloat();
                    player.pos.y = in.readFloat();
                    player.pos.z = in.readFloat();
                    player.yaw = in.readFloat();
                    player.pitch = in.readFloat();
                    player.health = in.readFloat();
                    player.flying = in.readBoolean();

                    inventory.clear();
                    int invSize = in.readInt();
                    for (int i = 0; i < invSize; i++) {
                        Block b = values[in.readByte()];
                        int amt = in.readInt();
                        inventory.put(b, amt);
                    }

                    count = in.readInt();
                } else {
                    throw new IOException("Versión de guardado desconocida: " + version);
                }
            } else {
                count = firstInt;
            }

            for (int i = 0; i < count; i++) {
                int cx = in.readInt();
                int cz = in.readInt();
                Chunk c = new Chunk(cx, cz);
                for (int x = 0; x < Chunk.SIZE; x++)
                    for (int y = 0; y < Chunk.HEIGHT; y++)
                        for (int z = 0; z < Chunk.SIZE; z++)
                            c.set(x, y, z, values[in.readByte()]);
                world.chunks.put(World.key(cx, cz), c);
            }
            world.relightAll();
            System.out.println("Mundo cargado desde " + path + " (" + count + " chunks, versión " + (firstInt < 0 ? -firstInt : "antigua") + ")");
        } catch (IOException e) {
            System.err.println("Error cargando el mundo: " + e.getMessage());
        }
    }
}
