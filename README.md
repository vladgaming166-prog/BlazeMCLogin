# BlazeMCLogin

Production authentication plugin for **BlazeMC Network**. Paper backends handle cracked `/register` + `/login`, while genuine Minecraft Java Premium players can be auto-logged after **BlazeMC Connection Detector** verifies a real Mojang/Microsoft session on Velocity.

BlazeMCLogin does **not** require LimboAuth or LimboAPI.

| | |
| --- | --- |
| Plugin | BlazeMCLogin `1.1.1` |
| Commands | `/login` `/register` `/logout` `/changepassword` `/bmlogin` (`/blazemclogin`) |
| Languages | `ro` (default), `en`, `ro-ascii` |
| Hashing | Argon2id |
| Databases | SQLite (default), MySQL, MariaDB |

## Download

Compiled JARs (branch `cursor/premium-auth-movement-2200`):

- Paper 1.21.x (Java 21 runtime): https://github.com/vladgaming166-prog/BlazeMCLogin/raw/cursor/premium-auth-movement-2200/build-output/BlazeMCLogin-Paper-1.21.x.jar
- Paper 26.x (Java 25 runtime): https://github.com/vladgaming166-prog/BlazeMCLogin/raw/cursor/premium-auth-movement-2200/build-output/BlazeMCLogin-Paper-26.x.jar
- Paper 1.20.4 family (Java 17+): https://github.com/vladgaming166-prog/BlazeMCLogin/raw/cursor/premium-auth-movement-2200/build-output/BlazeMCLogin-Paper-Legacy.jar
- Velocity bridge: https://github.com/vladgaming166-prog/BlazeMCLogin/raw/cursor/premium-auth-movement-2200/build-output/BlazeMCLogin-Velocity-Bridge.jar

Use **one** Paper JAR that matches the backend. Install the Velocity bridge on the proxy together with BlazeMC Connection Detector.

## Compatibility (verified artifacts)

Paper API artifacts that this project actually compiled against:

| Family | Paper API | Bytecode | JAR |
| --- | --- | --- | --- |
| 1.20.4 | `1.20.4-R0.1-SNAPSHOT` | Java 17 | `BlazeMCLogin-Paper-Legacy.jar` |
| 1.21.11 | `1.21.11-R0.1-SNAPSHOT` | Java 21 | `BlazeMCLogin-Paper-1.21.x.jar` |
| 26.1.2 / 26.2 / 26.3 | `26.1.2.build.74-stable` | Java 21 bytecode, run on Java 25 | `BlazeMCLogin-Paper-26.x.jar` |

There is **no** public `paper-api` artifact named exactly `26.1` (only `26.1.1` alphas and `26.1.2` stables). The 26.x JAR is compiled against 26.1.2, which is the stable 26.1 line.

Runtime Java is whatever **Paper itself** requires. Do not start Paper 26 on Java 21.

Older Paper APIs exist from 1.17 onward. The shared adapter uses only stable Bukkit/Paper APIs (commands, events, plugin messaging, Adventure). It is compiled as a Legacy JAR against 1.20.4.

## Build

Needs JDK 21 (and JDK 25 for the Paper 26 module).

```bash
./gradlew test paper-1.21:test shadowJar copyBuildOutput
```

JARs are copied to `build-output/`.

## Install

1. Put the Paper JAR in each backend `plugins/` folder.
2. Put `BlazeMCLogin-Velocity-Bridge.jar` in Velocity `plugins/` (optional but required for Premium auto-login).
3. Keep [BlazeMC Connection Detector](https://github.com/vladgaming166-prog/BlazeMC-Connection-Detector) on the proxy.
4. Use Velocity **modern** forwarding. Paper must **not** be in online-mode; the proxy decides online vs offline per connection.
5. Leave `shared-secret` empty unless you want an override. Both sides use the Velocity modern-forwarding secret by default.
6. Set `language` in `config.yml` to `ro`, `en`, or `ro-ascii`.
7. Premium auto-auth, reserved-name protection, and sounds are on by default.

See [docs/CONNECTION-DETECTOR.md](docs/CONNECTION-DETECTOR.md) and [docs/CONFIGURATION.md](docs/CONFIGURATION.md).

## Player commands

| Command | Action |
| --- | --- |
| `/register <password> <confirmPassword>` | Create a cracked (or password) account |
| `/login <password>` | Authenticate |
| `/logout` | End the session |
| `/changepassword <old> <new>` | Change password |

Passwords are never tab-completed or logged.

## Admin commands

`/bmlogin` or `/blazemclogin`:

`reload` `status` `info <player>` `accounts <ip>` `unregister <player>` `resetpassword <player>` `lock <player>` `unlock <player>` `session <player>` `invalidate-session <player>` `backup` `migrate [path] [overwrite]` `language <ro|en|ro-ascii>`

Permissions: `blazemclogin.admin`, `.reload`, `.info`, `.accounts`, `.unregister`, `.resetpassword`, `.lock`, `.session`, `.bypass.iplimit`, `.bypass.login`.

## Security properties

- Argon2id hashes, never plaintext passwords
- Hashing and SQL run off the Minecraft main thread
- HMAC-SHA256 IP association (no raw IPs stored in the account table)
- Brute-force lockout + command rate limit
- Unauthenticated players cannot move, chat, fight, drop, pick up, or run other commands
- Sessions are server-side and expire; password changes invalidate them
- Premium auto-login only after a signed proxy packet bound to UUID **and** username

## LimboAuth migration

`/bmlogin migrate [path] [overwrite]` inspects the LimboAuth SQLite schema, imports Argon2/BCrypt/SHA256 hashes, skips unknown formats, never overwrites BlazeMCLogin accounts unless `overwrite` is passed, and never modifies the original LimboAuth file. A backup is created first.

## Tests

`./gradlew test` covers registration, login, wrong passwords, hashing, IP limits, IPv4/IPv6, HMAC verification, replay, expiry, sessions, database reconnect, duplicate registration, permissions nodes, hex gradient parsing, and config reload.
