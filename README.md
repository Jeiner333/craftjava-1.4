# CraftJava — Minecraft-like en Java 3D

Juego voxel simple hecho con **LWJGL 3** (bindings de OpenGL/GLFW) y **JOML** (matemáticas 3D). Sin assets externos: todo el terreno y los bloques se generan por código.

## Mecánicas incluidas

- **Mundo por chunks** (16×64×16) generado con ruido fractal (fbm) para la altura del terreno, con capas de piedra/tierra/pasto, agua y árboles.
- **Cuevas subterráneas** talladas con ruido 3D suavizado (interpolación trilineal) para formar túneles coherentes.
- **Vetas de mena** (carbón e hierro) distribuidas dentro de la piedra según la profundidad.
- **Bedrock** en la capa más profunda: irrompible, delimita el mundo por abajo.
- **Carga dinámica de chunks** alrededor del jugador (radio configurable en `Main.world`).
- **Cámara en primera persona** con mouse-look (yaw/pitch) y el ratón capturado.
- **Movimiento WASD** relativo a la vista, con física: gravedad, salto, sprint (Ctrl), y modo vuelo (`F`).
- **Colisiones AABB** eje por eje contra los bloques del mundo.
- **Daño por caída**: si caes más de ~3.5 bloques sin volar, pierdes vida al aterrizar.
- **Barra de vida** (20 puntos, como en Minecraft) visible en el HUD; al llegar a 0, respawn automático en el punto de aparición.
- **Minado con tiempo real**: mantén clic izquierdo sobre un bloque; el tiempo para romperlo depende de su dureza (`Block.hardness`), con una barra de progreso visible.
- **Inventario con cantidades**: romper un bloque lo añade a tu inventario (hasta 64 por tipo); solo puedes colocar bloques que ya tengas.
- **Colocar bloques**: clic derecho, coloca el bloque seleccionado en la cara golpeada, consumiendo una unidad del inventario.
- **Iluminación suave (skylight + antorchas)**: cada chunk calcula su propia luz con flood-fill (BFS): la luz solar se siembra en las columnas expuestas al cielo y las antorchas emiten su propia luz (nivel 15), ambas se propagan atenuándose 1 nivel por bloque. Cada vértice de cada cara se ilumina promediando la luz de las 4 celdas que tocan esa esquina, logrando un degradado suave entre luz y sombra (y un efecto de oclusión ambiental "gratis" en las esquinas interiores).
- **Bloque de antorcha (`TORCH`)**: emite luz cálida (nivel 15), se dibuja como un poste delgado con una "llama" brillante en la punta en vez de un cubo completo, y no es sólida (se puede caminar a través de ella).
- **Hotbar de 10 bloques** con contador visual: teclas `1`-`9` y `0` (pasto, tierra, piedra, arena, madera, hojas, vidrio, carbón, hierro, antorcha).
- **Ciclo día/noche**: el color del cielo y la niebla cambian gradualmente en un ciclo de 4 minutos.
- **Niebla** que oculta el "pop-in" de chunks lejanos y refuerza la atmósfera día/noche.
- **Guardado y carga del mundo**: `F5` guarda, `F9` carga (archivo `world.save` en el directorio de ejecución).
- **Renderizado eficiente por caras**: solo se dibujan las caras de bloque expuestas a aire/agua/vidrio (culling de caras internas), usando display lists de OpenGL que se regeneran solo cuando el chunk cambia.

## Controles

| Tecla / Ratón      | Acción                          |
|--------------------|----------------------------------|
| W A S D            | Moverse                          |
| Ctrl (mientras W)  | Sprint                           |
| Ratón               | Mirar alrededor                  |
| Espacio            | Saltar (o subir en modo vuelo)   |
| Shift izq.         | Bajar en modo vuelo              |
| F                  | Alternar modo vuelo              |
| Clic izquierdo (mantener) | Minar el bloque señalado  |
| Clic derecho       | Colocar bloque seleccionado      |
| 1-9, 0             | Elegir bloque del hotbar         |
| F5                 | Guardar mundo                    |
| F9                 | Cargar mundo                     |
| Esc                | Salir                            |

## Cómo compilar y ejecutar

Requiere **Java 17+** y **Maven**. LWJGL descarga binarios nativos según tu sistema operativo: edita en `pom.xml` la propiedad `lwjgl.natives` según corresponda:

- Windows → `natives-windows`
- Linux → `natives-linux` (valor por defecto)
- macOS → `natives-macos` (en Apple Silicon puede requerir `natives-macos-arm64` según la versión de LWJGL)

Luego:

```bash
mvn clean package
java -jar target/craftjava-1.0.jar
```

En **macOS** es necesario ejecutar con `-XstartOnFirstThread` porque GLFW requiere el hilo principal:

```bash
java -XstartOnFirstThread -jar target/craftjava-1.0.jar
```

## Estructura del proyecto

```
src/main/java/com/miguel/craft/
├── Main.java          # ventana, input, bucle de juego, cámara
├── Player.java        # física del jugador y colisiones
├── World.java         # gestión de chunks, generación, raycasting
├── Chunk.java         # almacenamiento de bloques de una sección 16x64x16
├── ChunkRenderer.java # generación de malla visible (culling de caras) y dibujo
├── Block.java         # tipos de bloque y color
└── NoiseGen.java       # ruido fractal para el terreno (sin dependencias externas)
```

## Ideas para extender

- Texturas reales en lugar de color plano por bloque (usar un atlas de texturas y coordenadas UV en `ChunkRenderer`).
- Inventario tipo pantalla completa con arrastrar/soltar (hoy es solo hotbar + conteo).
- Crafteo (mesa de crafteo, recetas) y fundición (horno para convertir mena en lingotes).
- La eliminación de una fuente de luz (romper una antorcha, tapar una entrada de cueva) se recalcula correctamente en el chunk editado y sus 4 vecinos directos; en casos raros donde la luz se había extendido más allá de esos vecinos, puede quedar una zona ligeramente sobre-iluminada hasta la siguiente edición cercana.
- Mobs simples (animales pasivos, hostiles con IA básica de persecución).
- Sonido (romper/colocar bloques, pasos, ambiente).
- Multijugador con sockets.
- Migrar de display lists (deprecadas) a VBOs/shaders modernos para mejor rendimiento con distancias de render grandes.
- Guardado incremental (autoguardado periódico, o guardar solo chunks modificados en vez de todos).
