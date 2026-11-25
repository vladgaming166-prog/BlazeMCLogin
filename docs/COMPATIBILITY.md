# BlazeMCLogin compatibility

This file records what was actually compiled and executed. Versions that were not tested are not claimed as tested.

## Compiled API artifacts (1.1.1)

| Module | API Maven coordinate | Bytecode (`--release`) | Toolchain used to compile | Output JAR |
| --- | --- | --- | --- | --- |
| `paper-legacy` | `io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT` | 17 | JDK 21 with `--release 17` | `BlazeMCLogin-Paper-Legacy.jar` |
| `paper-1.21` | `io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT` | 21 | JDK 21 | `BlazeMCLogin-Paper-1.21.x.jar` |
| `paper-26` | `io.papermc.paper:paper-api:26.1.2.build.74-stable` | 21 | JDK 25 (required to *read* the 26 API) | `BlazeMCLogin-Paper-26.x.jar` |
| `paper-26` extra compile | `26.2.build.135-stable` | 21 | JDK 25 | same adapter sources |
| `paper-26` extra compile | `26.3.build.262-stable` | 21 | JDK 25 | same adapter sources |
| `velocity-bridge` | `com.velocitypowered:velocity-api:3.5.0-SNAPSHOT` | 21 | JDK 21 | `BlazeMCLogin-Velocity-Bridge.jar` |

There is **no** public `paper-api` artifact named exactly `26.1` (only `26.1.1` alphas and `26.1.2` stables). Paper 26.1 is therefore represented by 26.1.2.

The Paper 26 JAR uses Java 21 bytecode because Gradle Shadow 8 cannot relocate classfile 69 (Java 25). Paper 26 servers still require a Java 25 runtime; they load Java 21 bytecode normally.

This project does not vendor LimboAPI. If Elytrium LimboAPI is installed on Velocity, the bridge skips limbo for `Player.isOnlineMode()` via `LoginLimboRegisterEvent.clearCallback()`.

## Automated tests

`./gradlew test` (1.1.1): core suite including movement guard, Premium name protection (verified Premium is not kicked as cracked), concurrent login, HMAC/replay, forwarding-secret locator, config overlay, language packs, plus `paper-1.21:test` (`PaperApiPresenceTest` and PremiumOrCracked status parsing).

## Server smoke tests

| Server | Java | Plugin | Result |
| --- | --- | --- | --- |
| Paper 1.21.11-132 | Java 21 | 1.1.1 | Enabled, SQLite migrated, clean disable |
| Paper 26.3-262 | Java 25 | 1.0.0 | Enabled (not re-smoked after 1.1.1) |
| Paper 1.20.4 | — | — | API compile + unit tests only |
| Velocity 3.5 + live Premium/cracked clients | — | — | Not run in this environment |
| LimboAPI live transfer | — | — | Not run (optional hook compiled) |

A real Minecraft client join was not performed. Movement freeze, Premium auto-auth, reserved-name kicks, and sounds are covered by unit tests plus server enable smoke.

## Runtime Java vs Paper

Paper itself decides the minimum Java version. Do not start Paper 26.x on Java 21.
