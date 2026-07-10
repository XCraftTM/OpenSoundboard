# Stonecutter Development Guide

This project uses [Stonecutter](https://stonecutter.kikugie.dev/) to maintain one shared Java source tree for multiple Minecraft versions.

Currently supported build targets are listed in [`versions/supported.txt`](versions/supported.txt):

- Minecraft 1.21.1
- Minecraft 1.21.11
- Minecraft 26.1.2

## Requirements

- Use the included Gradle wrapper (`gradlew` or `gradlew.bat`).
- Use Java 25 to build every supported version.
- The 1.21.x targets compile for Java 21 through Gradle toolchains.
- The 26.x target requires the Gradle process itself to run on Java 25.

Check the Java version used by the wrapper:

```powershell
.\gradlew.bat --version
```

On Linux or macOS:

```bash
./gradlew --version
```

## The Active Version

Stonecutter keeps one version active at a time. The current version is stored in:

```text
versions/active.txt
```

Do not normally edit this file by hand. Use a Stonecutter switch task so that Stonecutter also updates the version-dependent source view.

To display the active version in PowerShell:

```powershell
Get-Content versions/active.txt
```

On Linux or macOS:

```bash
cat versions/active.txt
```

## Switching Minecraft Versions

Run the matching switch task from the project root.

PowerShell or Command Prompt:

```powershell
.\gradlew.bat stonecutterSwitchTo1.21.1
.\gradlew.bat stonecutterSwitchTo1.21.11
.\gradlew.bat stonecutterSwitchTo26.1.2
```

Linux or macOS:

```bash
./gradlew stonecutterSwitchTo1.21.1
./gradlew stonecutterSwitchTo1.21.11
./gradlew stonecutterSwitchTo26.1.2
```

After switching, IDE imports, Minecraft dependencies, mappings, and Stonecutter conditions correspond to that active version.

## Building One Version

First switch to the version, then invoke its fully qualified Gradle task.

Example for Minecraft 1.21.11 on Windows:

```powershell
.\gradlew.bat stonecutterSwitchTo1.21.11
.\gradlew.bat :1.21.11:build
```

Example for Minecraft 26.1.2 on Linux or macOS:

```bash
export JAVA_HOME=/path/to/jdk-25
./gradlew stonecutterSwitchTo26.1.2
./gradlew :26.1.2:build
```

The resulting files are written to:

```text
versions/<minecraft-version>/build/libs/
```

For a completely clean single-version build:

```powershell
.\gradlew.bat :1.21.11:clean :1.21.11:build
```

The selected project path must match the active version. For example, do not switch to `1.21.11` and then run `:26.1.2:build`.

## Building Every Version

Run the root build with Java 25. The root `build` command is routed to the isolated Stonecutter multi-version build.

PowerShell:

```powershell
$env:JAVA_HOME = "C:\Path\To\jdk-25"
.\gradlew.bat build
```

Command Prompt:

```bat
set JAVA_HOME=C:\Path\To\jdk-25
gradlew.bat build
```

Linux or macOS:

```bash
JAVA_HOME=/path/to/jdk-25 ./gradlew build
```

The explicit task name is equivalent:

```bash
JAVA_HOME=/path/to/jdk-25 ./gradlew buildAllJars
```

The multi-version build performs the following steps:

1. Remembers the currently active version.
2. Switches to each supported version in turn.
3. Cleans and builds that version in a separate Gradle invocation.
4. Verifies that exactly one release JAR was produced.
5. Collects the successful JARs in a staging directory.
6. Restores the version that was active before the build.
7. Replaces `build/jars/` only after every version succeeds.

Collected release files are written to:

```text
build/jars/
```

Detailed output from the nested builds is available in:

```text
build/nested-gradle.log
```

## Running a Development Client

Convenience tasks switch to the requested version and launch its Minecraft client:

```powershell
.\gradlew.bat client_1_21_1
.\gradlew.bat client_1_21_11
.\gradlew.bat client_26_1_2
```

Java 25 is required when launching the 26.1.2 client:

```powershell
$env:JAVA_HOME = "C:\Path\To\jdk-25"
.\gradlew.bat client_26_1_2
```

You can also switch manually and use the generated project task:

```powershell
.\gradlew.bat stonecutterSwitchTo1.21.11
.\gradlew.bat :1.21.11:runClient
```

## Editing Version-Dependent Code

Shared code lives in `src/main/java`. Stonecutter directives select different code for different Minecraft versions.

A simplified example:

```java
//? if >=26 {
/*newApiCall();
*///?} else {
oldApiCall();
//?}
```

Important rules:

- Edit the shared files under `src/`, not files under `versions/*/build/generated/stonecutter/`.
- Keep both sides of a Stonecutter condition syntactically valid for their target versions.
- Switch to each affected version when checking IDE errors.
- Run the full multi-version build before publishing or merging cross-version changes.

## Adding Another Supported Version

When adding a Minecraft version:

1. Create `versions/<version>/gradle.properties` with the Minecraft, Java, Loom, Fabric, Voice Chat, Mod Menu, and publishing values.
2. Add the version to `versions/supported.txt` in the desired build order.
3. Add or adjust Stonecutter conditions in the shared source tree.
4. Switch to the new version and run its fully qualified build task.
5. Run `buildAllJars` to verify every supported target.
6. Update the supported-version table in `README.md`.

The `minecraft.obfuscated` property must explicitly describe which Loom variant the target uses:

```properties
minecraft.obfuscated=true
```

Use `false` for unobfuscated Minecraft releases.

## Troubleshooting

### Compiler errors reference APIs from another Minecraft version

The selected project probably does not match the active version. Switch first and retry:

```powershell
.\gradlew.bat stonecutterSwitchTo1.21.11
.\gradlew.bat :1.21.11:clean :1.21.11:build
```

### The 26.x build reports an unsupported Java version

Make sure Gradle itself runs on Java 25:

```powershell
$env:JAVA_HOME = "C:\Path\To\jdk-25"
.\gradlew.bat --version
```

The JVM shown under `Launcher JVM` or `Daemon JVM` should be Java 25.

### `buildAllJars` fails without enough console detail

Inspect the nested build log:

```powershell
Get-Content build/nested-gradle.log -Tail 200
```

On Linux or macOS:

```bash
tail -n 200 build/nested-gradle.log
```

### Gradle appears to use stale generated classes

Use a clean, fully qualified build after switching:

```powershell
.\gradlew.bat :1.21.11:clean :1.21.11:build
```

The full `buildAllJars` task already cleans every version automatically.

## Command Summary

| Task | Purpose |
|---|---|
| `stonecutterSwitchTo1.21.11` | Make Minecraft 1.21.11 active |
| `:1.21.11:build` | Build only the active 1.21.11 project |
| `:1.21.11:clean :1.21.11:build` | Clean and rebuild 1.21.11 |
| `client_1_21_11` | Switch to 1.21.11 and launch its client |
| `build` | Build and collect every supported version |
| `buildAllJars` | Explicit name of the multi-version release build |

