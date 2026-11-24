# BlazeMCLogin compatibility

This file records what was actually compiled and executed. Versions that were not tested are not claimed as tested.

## Compiled Paper API artifacts

| Module | Paper API Maven coordinate | Bytecode (`--release`) | Toolchain used to compile | Output JAR |
| --- | --- | --- | --- | --- |
| `paper-legacy` | `io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT` | 17 | JDK 21 with `--release 17` | `BlazeMCLogin-Paper-Legacy.jar` |
| `paper-1.21` | `io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT` | 21 | JDK 21 | `BlazeMCLogin-Paper-1.21.x.jar` |
| `paper-26` | `io.papermc.paper:paper-api:26.1.2.build.74-stable` | 21 | JDK 25 (required to *read* the 26 API) | `BlazeMCLogin-Paper-26.x.jar` |
| `paper-26` extra compile | `26.2.build.135-stable` | 21 | JDK 25 | same adapter sources |
| `paper-26` extra compile | `26.3.build.262-stable` | 21 | JDK 25 | same adapter sources |
| `velocity-bridge` | Velocity API 3.4 | 21 | JDK 21 | `BlazeMCLogin-Velocity-Bridge.jar` |

There is **no** public `paper-api` artifact named exactly `26.1` (only `26.1.1` alphas and `26.1.2` stables). Paper 26.1 is therefore represented by 26.1.2.

The Paper 26 JAR uses Java 21 bytecode because Gradle Shadow 8 cannot relocate classfile 69 (Java 25). Paper 26 servers still require a Java 25 runtime; they load Java 21 bytecode normally.

## Automated tests

`./gradlew test` runs the core suite (auth, hashing, IP limits, HMAC/replay, sessions, config, language packs, color parser) plus `paper-1.21:test` (`PaperApiPresenceTest`).

## Server smoke tests

| Server | Java | Result |
| --- | --- | --- |
| Paper 1.21.11 (Fill v3 stable, build 132) | Java 21 | Plugin enabled, SQLite migrated, clean disable (`scripts/smoke-paper.sh`) |
| Paper 26.3-262 | Java 25 | Plugin enabled, SQLite migrated, clean disable |
| Paper 1.20.4 | — | Not smoke-booted here (API compile + unit tests only) |

A real Minecraft client join was not performed in this environment. Unauthenticated restrictions, `/login`, and `/register` are covered by unit tests against `AuthService`.

## Runtime Java vs Paper

Paper itself decides the minimum Java version. Do not start:

- Paper 26.x on Java 21
- Paper 1.21.11 on Java 16/17

Use **one** Paper JAR that matches the backend generation.
