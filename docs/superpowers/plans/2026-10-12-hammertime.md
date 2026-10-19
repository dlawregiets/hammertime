# HammerTime Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a server-side Fabric mod for Minecraft 26.2 that lets a sneaking player mine a configurable cube (3×3×3 or 5×5×5) of identical blocks with one break.

**Architecture:** A Fabric API `PlayerBlockBreakEvents.AFTER` listener triggers on the server when a block is broken. If the player is sneaking and the held tool can correctly harvest the target, a pure-integer `CubeGeometry` computes the cube offsets, and each matching block is broken through the vanilla `ServerPlayerGameMode#destroyBlock` path so drops, enchantments, XP, durability, and statistics stay vanilla-correct. Per-player size/enabled settings and global settings persist as JSON via Minecraft's bundled Gson.

**Tech Stack:** Java 25, Fabric Loader 0.19.5, Fabric API 0.161.0+26.2, Fabric Loom 1.18-SNAPSHOT, Gradle 9.7.1, Mojang official mappings (unobfuscated since 26.1), JUnit 5 for unit tests.

**Spec:** `docs/superpowers/specs/2026-10-12-hammertime-design.md`

## Global Constraints

- Minecraft version: **26.2** (exact).
- Java: **25** (`options.release = 25`, `sourceCompatibility`/`targetCompatibility = VERSION_25`).
- Fabric Loader **0.19.5**, Fabric API **0.161.0+26.2**, Loom **1.18-SNAPSHOT**, Gradle wrapper **9.7.1**.
- Use **Mojang official mappings** — do NOT add a `mappings` dependency or use Yarn. MC is unobfuscated in 26.2.
- Loom plugin id is `net.fabricmc.fabric-loom`; use plain `implementation` (NOT `modImplementation`) and plain `jar` (NOT `remapJar`).
- Only runtime dependency: **Fabric API**. JSON via bundled Gson (`com.google.gson`). Test-only dependency: JUnit 5.
- `fabric.mod.json` MUST set `"environment": "server"` and declare no client entrypoint and no mixins.
- Mod id: `hammertime`. Package root: `dev.hammertime`.
- Mod id / archive name: `hammertime`.
- The cube's target block sits at the **center of the front face**; the cube extends **into the wall** (`depth` offsets `0..size-1`). Match rule is **exact same `Block`** (state ignored).

## Review Focus

Inputs/conditions the spec implies but unit tests cannot fully exercise — verify each manually (Task 4/5 steps) or via the guard code that owns it:

1. **Vertical aim (looking straight up/down):** the cube must still be a full `size³` volume (covered by `CubeGeometryTest.verticalForward_down_stillProducesFullCube` and `everyDirectionProducesSizeCubedUniqueOffsets`).
2. **Empty hand or a tool the block does not accept:** must NOT trigger cube mining (`CubeMiner.onBlockBroken` returns early when `DataComponents.TOOL` is absent or `Tool.isCorrectForDrops` is false).
3. **Other block types inside the cube:** left untouched (exact-`Block` match in `CubeMiner.mine`).
4. **Cube partly outside the world / build height:** must not crash (bounds check via `Level.isOutsideBuildHeight`).
5. **Geyser/Bedrock sneak + break:** behaves like a Java client (manual end-to-end test in Task 5).

---

## File Structure

| File | Responsibility |
|---|---|
| `build.gradle` | Loom build script |
| `settings.gradle` | Plugin repos + root project name |
| `gradle.properties` | Versions and mod metadata |
| `gradle/wrapper/*`, `gradlew`, `gradlew.bat` | Gradle wrapper (copied from Fabric example mod) |
| `LICENSE`, `.gitignore`, `README.md` | Project metadata |
| `src/main/resources/fabric.mod.json` | Mod metadata (`environment: server`) |
| `src/main/java/dev/hammertime/HammerTime.java` | `ModInitializer`: load config, register event + command |
| `src/main/java/dev/hammertime/config/HammerConfig.java` | Global settings; Gson load/save |
| `src/main/java/dev/hammertime/config/PlayerSettings.java` | Per-player record (size, enabled) |
| `src/main/java/dev/hammertime/config/PlayerSettingsStore.java` | Per-player map; Gson load/save |
| `src/main/java/dev/hammertime/mining/CubeGeometry.java` | Pure integer cube-offset math |
| `src/main/java/dev/hammertime/mining/CubeMiner.java` | Trigger checks + break loop + re-entrancy guard |
| `src/main/java/dev/hammertime/command/HammerCommand.java` | Brigadier command tree |
| `src/test/java/dev/hammertime/mining/CubeGeometryTest.java` | Geometry unit tests |
| `src/test/java/dev/hammertime/config/HammerConfigTest.java` | Config/store round-trip unit tests |

---

### Task 1: Scaffold the Fabric 26.2 project and prove it builds

**Files:**
- Create: `build.gradle`, `settings.gradle`, `gradle.properties`, `.gitignore`, `LICENSE`, `README.md`
- Create: `src/main/resources/fabric.mod.json`
- Create: `src/main/java/dev/hammertime/HammerTime.java`
- Copy: `gradlew`, `gradlew.bat`, `gradle/` (wrapper only)

**Interfaces:**
- Consumes: nothing.
- Produces: a compiling Fabric mod with entrypoint class `dev.hammertime.HammerTime` implementing `net.fabricmc.api.ModInitializer`.

- [ ] **Step 1: Fetch the Gradle wrapper from the Fabric example mod**

```bash
git clone --depth 1 --branch 26.2 https://github.com/FabricMC/fabric-example-mod /tmp/fabric-example-mod
cp -r /tmp/fabric-example-mod/gradle .
cp /tmp/fabric-example-mod/gradlew /tmp/fabric-example-mod/gradlew.bat .
chmod +x gradlew
```

- [ ] **Step 2: Write `settings.gradle`**

```groovy
pluginManagement {
    repositories {
        maven {
            name = 'Fabric'
            url = 'https://maven.fabricmc.net/'
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = 'hammertime'
```

- [ ] **Step 3: Write `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx1G
org.gradle.parallel=true
org.gradle.configuration-cache=true

# Fabric properties
minecraft_version=26.2
loader_version=0.19.5
loom_version=1.18-SNAPSHOT

# Mod properties
mod_version=1.0.0
maven_group=dev.hammertime
archives_base_name=hammertime

# Dependencies
fabric_api_version=0.161.0+26.2
```

- [ ] **Step 4: Write `build.gradle`**

```groovy
plugins {
    id 'net.fabricmc.fabric-loom' version "${loom_version}"
}

version = project.mod_version
group = project.maven_group

base {
    archivesName = project.archives_base_name
}

repositories {
    mavenCentral()
}

dependencies {
    minecraft "com.mojang:minecraft:${project.minecraft_version}"
    implementation "net.fabricmc:fabric-loader:${project.loader_version}"
    implementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_api_version}"

    testImplementation platform('org.junit:junit-bom:5.11.4')
    testImplementation 'org.junit.jupiter:junit-jupiter'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

processResources {
    inputs.property "version", project.version
    filesMatching("fabric.mod.json") {
        expand "version": project.version
    }
}

tasks.withType(JavaCompile).configureEach {
    it.options.release = 25
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

test {
    useJUnitPlatform()
}

jar {
    from("LICENSE") {
        rename { "${it}_${project.base.archivesName.get()}" }
    }
}
```

- [ ] **Step 5: Write `src/main/resources/fabric.mod.json`**

```json
{
    "schemaVersion": 1,
    "id": "hammertime",
    "version": "${version}",
    "name": "HammerTime",
    "description": "Sneak-break a block to mine a configurable cube of identical blocks. Server-side only.",
    "authors": [
        "HammerTime"
    ],
    "license": "MIT",
    "environment": "server",
    "entrypoints": {
        "main": [
            "dev.hammertime.HammerTime"
        ]
    },
    "depends": {
        "fabricloader": ">=0.19.5",
        "minecraft": "~26.2",
        "java": ">=25",
        "fabric-api": "*"
    }
}
```

- [ ] **Step 6: Write the minimal `src/main/java/dev/hammertime/HammerTime.java`**

```java
package dev.hammertime;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HammerTime implements ModInitializer {
    public static final String MOD_ID = "hammertime";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("HammerTime initializing");
    }
}
```

- [ ] **Step 7: Write `.gitignore`, `LICENSE`, and `README.md`**

`.gitignore`:

```
.gradle/
build/
run/
*.iml
.idea/
```

`LICENSE`: standard MIT license text (Copyright (c) 2026 HammerTime).

`README.md`:

```markdown
# HammerTime

Server-side Fabric mod for Minecraft 26.2. While sneaking, break a block with a tool
that can correctly harvest it to mine a cube of identical blocks.

- No client mod, resource pack, or datapack required (works from Bedrock via Geyser).
- Per-player size: `/hammer size 3` or `/hammer size 5`, `/hammer on|off`, `/hammer status`.
- Global config: `config/hammertime.json`.

Requires Fabric Loader and Fabric API on the server only.
```

- [ ] **Step 8: Build to verify the scaffold compiles**

Run: `./gradlew build --no-daemon`
Expected: `BUILD SUCCESSFUL`; a jar appears under `build/libs/`. (First run downloads Gradle 9.7.1 and Minecraft — allow several minutes; use a generous timeout.)

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "Scaffold Fabric 26.2 HammerTime project"
```

---

### Task 2: Cube geometry (pure integer math)

**Files:**
- Create: `src/main/java/dev/hammertime/mining/CubeGeometry.java`
- Test: `src/test/java/dev/hammertime/mining/CubeGeometryTest.java`

**Interfaces:**
- Consumes: nothing (plain Java only — no Minecraft types).
- Produces:
  - `CubeGeometry.Offset` — record `Offset(int x, int y, int z)`.
  - `static List<Offset> CubeGeometry.offsets(int size, int fx, int fy, int fz)` — throws `IllegalArgumentException` if `size` is not a positive odd number; returns exactly `size³` offsets; `(0,0,0)` is the target block at the centre of the front face; depth extends along the forward vector for offsets `0..size-1`.

- [ ] **Step 1: Write the failing test**

`src/test/java/dev/hammertime/mining/CubeGeometryTest.java`:

```java
package dev.hammertime.mining;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CubeGeometryTest {
    @Test
    void size3NorthProduces27UniqueOffsetsExtendingIntoWall() {
        List<CubeGeometry.Offset> offsets = CubeGeometry.offsets(3, 0, 0, -1);
        assertEquals(27, offsets.size());
        assertEquals(27, new HashSet<>(offsets).size());
        assertTrue(offsets.contains(new CubeGeometry.Offset(0, 0, 0)));
        for (CubeGeometry.Offset o : offsets) {
            assertTrue(o.z() <= 0 && o.z() >= -2, "z in [-2,0]: " + o);
            assertTrue(o.x() >= -1 && o.x() <= 1, "x in [-1,1]: " + o);
            assertTrue(o.y() >= -1 && o.y() <= 1, "y in [-1,1]: " + o);
        }
    }

    @Test
    void size5NorthSpansFiveInEachAxis() {
        List<CubeGeometry.Offset> offsets = CubeGeometry.offsets(5, 0, 0, -1);
        assertEquals(125, offsets.size());
        for (CubeGeometry.Offset o : offsets) {
            assertTrue(o.z() <= 0 && o.z() >= -4, "z in [-4,0]: " + o);
            assertTrue(o.x() >= -2 && o.x() <= 2, "x in [-2,2]: " + o);
            assertTrue(o.y() >= -2 && o.y() <= 2, "y in [-2,2]: " + o);
        }
    }

    @Test
    void verticalForwardDownStillProducesFullCube() {
        List<CubeGeometry.Offset> offsets = CubeGeometry.offsets(3, 0, -1, 0);
        assertEquals(27, offsets.size());
        assertEquals(27, new HashSet<>(offsets).size());
        for (CubeGeometry.Offset o : offsets) {
            assertTrue(o.y() <= 0 && o.y() >= -2, "y in [-2,0]: " + o);
        }
    }

    @Test
    void everyDirectionProducesSizeCubedUniqueOffsets() {
        int[][] directions = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int[] d : directions) {
            for (int size : new int[]{3, 5}) {
                List<CubeGeometry.Offset> offsets = CubeGeometry.offsets(size, d[0], d[1], d[2]);
                assertEquals(size * size * size, offsets.size(),
                        "count for size " + size + " dir " + Arrays.toString(d));
                assertEquals(size * size * size, new HashSet<>(offsets).size(),
                        "uniqueness for size " + size + " dir " + Arrays.toString(d));
            }
        }
    }

    @Test
    void rejectsEvenSize() {
        assertThrows(IllegalArgumentException.class, () -> CubeGeometry.offsets(4, 0, 0, -1));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests 'dev.hammertime.mining.CubeGeometryTest' --no-daemon`
Expected: FAIL — compilation error, `CubeGeometry` does not exist.

- [ ] **Step 3: Write the minimal implementation**

`src/main/java/dev/hammertime/mining/CubeGeometry.java`:

```java
package dev.hammertime.mining;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure integer geometry for the cube-mining region. Uses no Minecraft types so it is
 * trivially unit-testable.
 */
public final class CubeGeometry {
    private CubeGeometry() {
    }

    /** An integer offset relative to the target block. */
    public record Offset(int x, int y, int z) {
    }

    /**
     * Computes the offsets of a {@code size x size x size} cube.
     *
     * <p>The target block is the centre of the front face at {@code (0,0,0)}; the cube
     * extends into the wall along the forward vector for depth {@code 0..size-1}.
     *
     * @param size odd cube size (3 or 5)
     * @param fx   forward unit vector x (-1, 0, or 1)
     * @param fy   forward unit vector y
     * @param fz   forward unit vector z
     */
    public static List<Offset> offsets(int size, int fx, int fy, int fz) {
        if (size < 1 || size % 2 == 0) {
            throw new IllegalArgumentException("size must be a positive odd number: " + size);
        }
        int half = size / 2;

        // Perpendicular basis (right, up). The exact orientation is irrelevant because
        // the cube is symmetric on both perpendicular axes.
        int rx;
        int ry;
        int rz;
        int ux;
        int uy;
        int uz;
        if (fy == 0) {
            // Horizontal forward: up is world up, right is forward rotated 90 degrees.
            rx = -fz;
            ry = 0;
            rz = fx;
            ux = 0;
            uy = 1;
            uz = 0;
        } else {
            // Vertical forward: use world axes for the two perpendicular directions.
            rx = 1;
            ry = 0;
            rz = 0;
            ux = 0;
            uy = 0;
            uz = 1;
        }

        List<Offset> result = new ArrayList<>(size * size * size);
        for (int depth = 0; depth < size; depth++) {
            for (int lateral = -half; lateral <= half; lateral++) {
                for (int vertical = -half; vertical <= half; vertical++) {
                    result.add(new Offset(
                            fx * depth + rx * lateral + ux * vertical,
                            fy * depth + ry * lateral + uy * vertical,
                            fz * depth + rz * lateral + uz * vertical));
                }
            }
        }
        return result;
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests 'dev.hammertime.mining.CubeGeometryTest' --no-daemon`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/hammertime/mining/CubeGeometry.java src/test/java/dev/hammertime/mining/CubeGeometryTest.java
git commit -m "Add cube geometry with unit tests"
```

---

### Task 3: Config and per-player settings

**Files:**
- Create: `src/main/java/dev/hammertime/config/HammerConfig.java`
- Create: `src/main/java/dev/hammertime/config/PlayerSettings.java`
- Create: `src/main/java/dev/hammertime/config/PlayerSettingsStore.java`
- Test: `src/test/java/dev/hammertime/config/HammerConfigTest.java`

**Interfaces:**
- Consumes: nothing (plain Java + Gson + SLF4J).
- Produces:
  - `HammerConfig` public mutable fields: `int defaultSize`, `int[] allowedSizes`, `boolean requireSneak`, `boolean durabilityPerBlock`, `int maxBlocksPerBreak`, `boolean enabledByDefault`.
  - `boolean HammerConfig.isAllowedSize(int size)`
  - `static HammerConfig HammerConfig.load(Path path)` — writes defaults if the file is missing or unreadable.
  - `void HammerConfig.save(Path path)`
  - `void HammerConfig.copyFrom(HammerConfig other)`
  - `record PlayerSettings(int size, boolean enabled)` with `static PlayerSettings PlayerSettings.fromConfig(HammerConfig config)`.
  - `PlayerSettingsStore(Path path, HammerConfig config)`, `PlayerSettings get(UUID)`, `void set(UUID, PlayerSettings)`, `void save()`.

- [ ] **Step 1: Write the failing test**

`src/test/java/dev/hammertime/config/HammerConfigTest.java`:

```java
package dev.hammertime.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HammerConfigTest {
    @Test
    void writesDefaultsWhenMissingAndReadsBack() throws Exception {
        Path dir = Files.createTempDirectory("hammertime-test");
        Path file = dir.resolve("hammertime.json");

        HammerConfig config = HammerConfig.load(file);
        assertEquals(3, config.defaultSize);
        assertTrue(Files.exists(file));
        assertTrue(config.isAllowedSize(5));
        assertFalse(config.isAllowedSize(7));

        config.defaultSize = 5;
        config.save(file);

        HammerConfig reloaded = HammerConfig.load(file);
        assertEquals(5, reloaded.defaultSize);
    }

    @Test
    void malformedFileFallsBackToDefaults() throws Exception {
        Path dir = Files.createTempDirectory("hammertime-test");
        Path file = dir.resolve("hammertime.json");
        Files.writeString(file, "{ not valid json ");

        HammerConfig config = HammerConfig.load(file);
        assertEquals(3, config.defaultSize);
    }

    @Test
    void storeRoundTripsPerPlayerSettings() throws Exception {
        Path dir = Files.createTempDirectory("hammertime-test");
        Path file = dir.resolve("hammertime-players.json");
        HammerConfig config = new HammerConfig();

        PlayerSettingsStore store = new PlayerSettingsStore(file, config);
        UUID id = UUID.randomUUID();
        assertEquals(3, store.get(id).size());
        assertTrue(store.get(id).enabled());

        store.set(id, new PlayerSettings(5, false));

        PlayerSettingsStore reloaded = new PlayerSettingsStore(file, config);
        assertEquals(5, reloaded.get(id).size());
        assertFalse(reloaded.get(id).enabled());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests 'dev.hammertime.config.HammerConfigTest' --no-daemon`
Expected: FAIL — compilation error, `HammerConfig` / `PlayerSettingsStore` do not exist.

- [ ] **Step 3: Write `PlayerSettings.java`**

```java
package dev.hammertime.config;

/** Per-player HammerTime settings. */
public record PlayerSettings(int size, boolean enabled) {
    public static PlayerSettings fromConfig(HammerConfig config) {
        return new PlayerSettings(config.defaultSize, config.enabledByDefault);
    }
}
```

- [ ] **Step 4: Write `HammerConfig.java`**

```java
package dev.hammertime.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Global HammerTime settings, persisted as JSON. */
public final class HammerConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("hammertime");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public int defaultSize = 3;
    public int[] allowedSizes = {3, 5};
    public boolean requireSneak = true;
    public boolean durabilityPerBlock = true;
    public int maxBlocksPerBreak = 128;
    public boolean enabledByDefault = true;

    public boolean isAllowedSize(int size) {
        for (int allowed : allowedSizes) {
            if (allowed == size) {
                return true;
            }
        }
        return false;
    }

    /** Loads the config, writing defaults if the file is missing or unreadable. */
    public static HammerConfig load(Path path) {
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                HammerConfig config = GSON.fromJson(reader, HammerConfig.class);
                if (config != null) {
                    return config;
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to read {}; using defaults", path, e);
            }
        }
        HammerConfig config = new HammerConfig();
        config.save(path);
        return config;
    }

    public void save(Path path) {
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to write {}", path, e);
        }
    }

    /** Copies every field from {@code other} into this instance (used by /hammer reload). */
    public void copyFrom(HammerConfig other) {
        this.defaultSize = other.defaultSize;
        this.allowedSizes = other.allowedSizes;
        this.requireSneak = other.requireSneak;
        this.durabilityPerBlock = other.durabilityPerBlock;
        this.maxBlocksPerBreak = other.maxBlocksPerBreak;
        this.enabledByDefault = other.enabledByDefault;
    }

    @Override
    public String toString() {
        return "HammerConfig{defaultSize=" + defaultSize
                + ", allowedSizes=" + Arrays.toString(allowedSizes)
                + ", requireSneak=" + requireSneak
                + ", durabilityPerBlock=" + durabilityPerBlock
                + ", maxBlocksPerBreak=" + maxBlocksPerBreak
                + ", enabledByDefault=" + enabledByDefault + "}";
    }
}
```

- [ ] **Step 5: Write `PlayerSettingsStore.java`**

```java
package dev.hammertime.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Loads and saves per-player settings, keyed by UUID. */
public final class PlayerSettingsStore {
    private static final Logger LOGGER = LoggerFactory.getLogger("hammertime");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<Map<String, PlayerSettings>>() {
    }.getType();

    private final Path path;
    private final HammerConfig config;
    private final Map<UUID, PlayerSettings> settings = new HashMap<>();

    public PlayerSettingsStore(Path path, HammerConfig config) {
        this.path = path;
        this.config = config;
        load();
    }

    public PlayerSettings get(UUID uuid) {
        return settings.getOrDefault(uuid, PlayerSettings.fromConfig(config));
    }

    public void set(UUID uuid, PlayerSettings value) {
        settings.put(uuid, value);
        save();
    }

    private void load() {
        if (!Files.exists(path)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Map<String, PlayerSettings> raw = GSON.fromJson(reader, TYPE);
            if (raw != null) {
                raw.forEach((key, value) -> {
                    try {
                        settings.put(UUID.fromString(key), value);
                    } catch (IllegalArgumentException e) {
                        LOGGER.warn("Ignoring malformed UUID '{}' in {}", key, path);
                    }
                });
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to read {}; starting empty", path, e);
        }
    }

    public void save() {
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Map<String, PlayerSettings> raw = new HashMap<>();
            settings.forEach((uuid, value) -> raw.put(uuid.toString(), value));
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(raw, writer);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to write {}", path, e);
        }
    }
}
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew test --tests 'dev.hammertime.config.HammerConfigTest' --no-daemon`
Expected: PASS (3 tests).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/dev/hammertime/config src/test/java/dev/hammertime/config
git commit -m "Add global config and per-player settings store"
```

---

### Task 4: Cube miner and event wiring

**Files:**
- Create: `src/main/java/dev/hammertime/mining/CubeMiner.java`
- Modify: `src/main/java/dev/hammertime/HammerTime.java`

**Interfaces:**
- Consumes: `HammerConfig`, `PlayerSettingsStore`, `CubeGeometry.offsets(...)` and `CubeGeometry.Offset`.
- Produces:
  - `CubeMiner(HammerConfig config, PlayerSettingsStore store)`
  - `void CubeMiner.onBlockBroken(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state)`
  - `HammerTime` holds a static `HammerConfig config` and `PlayerSettingsStore store`, plus `static void reloadConfig()`.

- [ ] **Step 1: Write `CubeMiner.java`**

`src/main/java/dev/hammertime/mining/CubeMiner.java`:

```java
package dev.hammertime.mining;

import dev.hammertime.config.HammerConfig;
import dev.hammertime.config.PlayerSettings;
import dev.hammertime.config.PlayerSettingsStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Breaks a cube of identical blocks when a sneaking player breaks one. */
public final class CubeMiner {
    /** Guards against the extra breaks re-entering this handler. */
    private static final Set<UUID> IN_PROGRESS = ConcurrentHashMap.newKeySet();

    private final HammerConfig config;
    private final PlayerSettingsStore store;

    public CubeMiner(HammerConfig config, PlayerSettingsStore store) {
        this.config = config;
        this.store = store;
    }

    public void onBlockBroken(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
        if (config.requireSneak && !player.isShiftKeyDown()) {
            return;
        }
        if (player.isSpectator()) {
            return;
        }
        if (!canHarvest(player.getMainHandItem(), state)) {
            return;
        }

        PlayerSettings settings = store.get(player.getUUID());
        if (!settings.enabled()) {
            return;
        }

        if (!IN_PROGRESS.add(player.getUUID())) {
            return;
        }
        try {
            mine(level, player, pos, state, settings.size());
        } finally {
            IN_PROGRESS.remove(player.getUUID());
        }
    }

    /** True only if the stack is a tool whose rules say it is correct for this block. */
    private static boolean canHarvest(ItemStack stack, BlockState state) {
        Tool tool = stack.get(DataComponents.TOOL);
        return tool != null && tool.isCorrectForDrops(state);
    }

    private void mine(ServerLevel level, ServerPlayer player, BlockPos target, BlockState targetState, int size) {
        Vec3 look = player.getViewVector(1.0F);
        Direction forward = Direction.getNearest(look.x, look.y, look.z);
        List<CubeGeometry.Offset> offsets = CubeGeometry.offsets(
                size, forward.getStepX(), forward.getStepY(), forward.getStepZ());

        ItemStack toolSnapshot = player.getMainHandItem().copy();
        int broken = 0;
        for (CubeGeometry.Offset offset : offsets) {
            if (broken >= config.maxBlocksPerBreak) {
                break;
            }
            BlockPos candidate = target.offset(offset.x(), offset.y(), offset.z());
            if (candidate.equals(target) || level.isOutsideBuildHeight(candidate)) {
                continue;
            }
            BlockState candidateState = level.getBlockState(candidate);
            if (candidateState.isAir() || candidateState.getBlock() != targetState.getBlock()) {
                continue;
            }
            if (!canHarvest(player.getMainHandItem(), candidateState)) {
                break;
            }
            if (player.gameMode.destroyBlock(candidate)) {
                broken++;
            }
        }

        if (!config.durabilityPerBlock) {
            player.setItemInHand(InteractionHand.MAIN_HAND, toolSnapshot);
        }
    }
}
```

- [ ] **Step 2: Rewrite `HammerTime.java` to wire the event**

`src/main/java/dev/hammertime/HammerTime.java`:

```java
package dev.hammertime;

import dev.hammertime.config.HammerConfig;
import dev.hammertime.config.PlayerSettingsStore;
import dev.hammertime.mining.CubeMiner;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public final class HammerTime implements ModInitializer {
    public static final String MOD_ID = "hammertime";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static HammerConfig config;
    private static PlayerSettingsStore store;
    private static CubeMiner miner;

    @Override
    public void onInitialize() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        config = HammerConfig.load(configDir.resolve("hammertime.json"));
        store = new PlayerSettingsStore(configDir.resolve("hammertime-players.json"), config);
        miner = new CubeMiner(config, store);

        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
                miner.onBlockBroken(serverLevel, serverPlayer, pos, state);
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> store.save());

        LOGGER.info("HammerTime initialized");
    }

    public static HammerConfig config() {
        return config;
    }

    public static PlayerSettingsStore store() {
        return store;
    }

    public static void reloadConfig() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("hammertime.json");
        config.copyFrom(HammerConfig.load(path));
        LOGGER.info("HammerTime config reloaded: {}", config);
    }
}
```

- [ ] **Step 3: Build and run unit tests**

Run: `./gradlew build --no-daemon`
Expected: `BUILD SUCCESSFUL`. If the compiler reports an unresolved symbol, fix the exact official name (likely candidates: `Direction.getNearest` overload, `Tool`/`DataComponents` packages, `Level.isOutsideBuildHeight(BlockPos)`, `ServerPlayer.gameMode`) and rebuild.

- [ ] **Step 4: Smoke-test that the mod loads on a dedicated server**

```bash
mkdir -p run
echo "eula=true" > run/eula.txt
./gradlew runServer --no-daemon
```

Expected: the server reaches `Done` and the log contains `HammerTime initialized`. Stop the server (type `stop` or Ctrl-C).

- [ ] **Step 5: MANUAL in-game verification (performed by the user)**

Connect (Java client, or Bedrock via Geyser) and confirm:
- Sneak + break a stone block with a pickaxe → a 3×3×3 region of stone is gone.
- Break the same without sneaking → only one block.
- Break stone with an empty hand or a sword → only one block.
- Break dirt with a pickaxe → only one block (tool rules reject it); with a shovel → cube.
- Break a stone block in a mixed wall (stone + dirt) → only the stone is removed.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/dev/hammertime/mining/CubeMiner.java src/main/java/dev/hammertime/HammerTime.java
git commit -m "Wire cube miner to the block-break event"
```

---

### Task 5: Commands

**Files:**
- Create: `src/main/java/dev/hammertime/command/HammerCommand.java`
- Modify: `src/main/java/dev/hammertime/HammerTime.java`

**Interfaces:**
- Consumes: `HammerConfig`, `PlayerSettings`, `PlayerSettingsStore`, `HammerTime.reloadConfig()`.
- Produces: `static void HammerCommand.register(CommandDispatcher<CommandSourceStack> dispatcher, HammerConfig config, PlayerSettingsStore store, Runnable reloadAction)`.

- [ ] **Step 1: Write `HammerCommand.java`**

`src/main/java/dev/hammertime/command/HammerCommand.java`:

```java
package dev.hammertime.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.hammertime.config.HammerConfig;
import dev.hammertime.config.PlayerSettings;
import dev.hammertime.config.PlayerSettingsStore;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;

/** Registers the {@code /hammer} command tree. */
public final class HammerCommand {
    private HammerCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                HammerConfig config,
                                PlayerSettingsStore store,
                                Runnable reloadAction) {
        dispatcher.register(Commands.literal("hammer")
                .then(Commands.literal("size")
                        .then(Commands.argument("size", IntegerArgumentType.integer(1))
                                .executes(ctx -> setSize(ctx.getSource(),
                                        ctx.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(ctx, "size"), config, store))))
                .then(Commands.literal("on")
                        .executes(ctx -> setEnabled(ctx.getSource(),
                                ctx.getSource().getPlayerOrException(), true, store)))
                .then(Commands.literal("off")
                        .executes(ctx -> setEnabled(ctx.getSource(),
                                ctx.getSource().getPlayerOrException(), false, store)))
                .then(Commands.literal("status")
                        .executes(ctx -> status(ctx.getSource(),
                                ctx.getSource().getPlayerOrException(), store)))
                .then(Commands.literal("reload")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> {
                            reloadAction.run();
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("HammerTime config reloaded"), true);
                            return 1;
                        }))
                .then(Commands.argument("player", EntityArgument.player())
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("size")
                                .then(Commands.argument("size", IntegerArgumentType.integer(1))
                                        .executes(ctx -> setSize(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"),
                                                IntegerArgumentType.getInteger(ctx, "size"),
                                                config, store))))));
    }

    private static int setSize(CommandSourceStack source, ServerPlayer target, int size,
                               HammerConfig config, PlayerSettingsStore store) {
        if (!config.isAllowedSize(size)) {
            source.sendFailure(Component.literal(
                    "Size " + size + " is not allowed. Allowed sizes: " + Arrays.toString(config.allowedSizes)));
            return 0;
        }
        PlayerSettings current = store.get(target.getUUID());
        store.set(target.getUUID(), new PlayerSettings(size, current.enabled()));
        source.sendSuccess(() -> Component.literal(
                "Hammer size set to " + size + "x" + size + "x" + size
                        + " for " + target.getName().getString()), true);
        return 1;
    }

    private static int setEnabled(CommandSourceStack source, ServerPlayer target, boolean enabled,
                                  PlayerSettingsStore store) {
        PlayerSettings current = store.get(target.getUUID());
        store.set(target.getUUID(), new PlayerSettings(current.size(), enabled));
        source.sendSuccess(() -> Component.literal(
                "Hammer " + (enabled ? "enabled" : "disabled")
                        + " for " + target.getName().getString()), true);
        return 1;
    }

    private static int status(CommandSourceStack source, ServerPlayer target, PlayerSettingsStore store) {
        PlayerSettings settings = store.get(target.getUUID());
        source.sendSuccess(() -> Component.literal(
                "Hammer: " + (settings.enabled() ? "enabled" : "disabled")
                        + ", size " + settings.size() + "x" + settings.size() + "x" + settings.size()), false);
        return 1;
    }
}
```

- [ ] **Step 2: Register the command in `HammerTime.java`**

Add these imports:

```java
import dev.hammertime.command.HammerCommand;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
```

Add this inside `onInitialize()` (after the `PlayerBlockBreakEvents.AFTER` registration):

```java
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                HammerCommand.register(dispatcher, config, store, HammerTime::reloadConfig));
```

- [ ] **Step 3: Build**

Run: `./gradlew build --no-daemon`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: MANUAL command verification (performed by the user)**

Run `./gradlew runServer --no-daemon`, then from the client/console:
- `/hammer status` → shows enabled + size 3.
- `/hammer size 5` → "Hammer size set to 5x5x5".
- Sneak + break stone with a pickaxe → a 5×5×5 region is gone.
- `/hammer size 7` → failure message listing allowed sizes.
- `/hammer off` then sneak-break → only one block.
- `/hammer reload` as a non-op → permission error; as op → reloads.
- **Geyser end-to-end:** connect from the Bedrock client and confirm sneak-break cube mining works identically.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/hammertime/command/HammerCommand.java src/main/java/dev/hammertime/HammerTime.java
git commit -m "Add /hammer command tree"
```

---

### Task 6: Final packaging and verification

**Files:**
- Modify: `README.md` (document config keys and commands precisely)

**Interfaces:**
- Consumes: everything above.
- Produces: a distributable `build/libs/hammertime-1.0.0.jar`.

- [ ] **Step 1: Confirm the release jar is server-only and self-contained**

Run: `./gradlew clean build --no-daemon`
Then: `unzip -l build/libs/hammertime-1.0.0.jar | grep -E 'fabric.mod.json|dev/hammertime'`
Expected: `fabric.mod.json` and the `dev/hammertime/**` classes are present; no client-only classes.

- [ ] **Step 2: Verify `fabric.mod.json` inside the jar declares server environment**

Run: `unzip -p build/libs/hammertime-1.0.0.jar fabric.mod.json`
Expected: contains `"environment": "server"` and `"version": "1.0.0"`.

- [ ] **Step 3: Document config and commands in `README.md`**

Add a `## Configuration` section listing the `config/hammertime.json` keys (`defaultSize`, `allowedSizes`, `requireSneak`, `durabilityPerBlock`, `maxBlocksPerBreak`, `enabledByDefault`) and a `## Commands` section listing `/hammer size <n>`, `/hammer on`, `/hammer off`, `/hammer status`, `/hammer <player> size <n>` (op), `/hammer reload` (op).

- [ ] **Step 4: Commit**

```bash
git add README.md
git commit -m "Document configuration and commands"
```

---

## Self-Review

**Spec coverage:**
- Server-side only, no client mods/packs → `environment: "server"`, no client entrypoint, no mixins (Tasks 1, 6).
- Sneak + break trigger → `CubeMiner.onBlockBroken` (Task 4).
- Tool-gated, exact-block match → `canHarvest` + `getBlock() !=` check (Task 4).
- Drill-into-wall, target centered on front face, size³ → `CubeGeometry` (Task 2).
- 3×3 or 5×5 per player via command → `PlayerSettingsStore` + `HammerCommand` (Tasks 3, 5).
- Config file via bundled Gson → `HammerConfig` (Task 3).
- Drops/enchantments/XP/durability/statistics vanilla-correct → breaks via `gameMode.destroyBlock` (Task 4).
- `durabilityPerBlock` option → snapshot/restore (Task 4).
- `maxBlocksPerBreak` cap → loop bound (Task 4).
- `requireSneak` option → early return (Task 4).
- Safety: tool re-check per iteration, air/bounds skip, spectator skip (Task 4).
- Testing: geometry + config unit tests; manual in-game matrix; Geyser test (Tasks 2–5).

**Placeholder scan:** No "TBD"/"TODO"; every code step contains complete code. Task 4 Step 3 lists likely API-name fixes as concrete alternatives, not a vague "handle errors".

**Type consistency:** `HammerConfig` field names, `PlayerSettings(size, enabled)`, `CubeGeometry.offsets(int,int,int,int)`, and `CubeMiner(HammerConfig, PlayerSettingsStore)` are used identically across Tasks 2–5.

**Review Focus coverage:** Vertical aim → geometry tests; empty-hand/weak-tool → `canHarvest`; other block types → exact-`Block` match; out-of-world → `isOutsideBuildHeight`; Geyser → Task 5 Step 4.
