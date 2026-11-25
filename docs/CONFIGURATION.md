# BlazeMCLogin configuration

All files live in `plugins/BlazeMCLogin/`. `config.yml` is generated on first start with every feature default. Split files still work and are overlaid; keys in `config.yml` win when both exist.

| File | Purpose |
| --- | --- |
| `config.yml` | Language, Premium auto-auth, cracked, movement, sounds master switch |
| `messages.yml` | Prefix and optional per-key overrides |
| `lang/messages-*.yml` | Full translations |
| `security.yml` | Brute-force, password policy, Argon2id, IP/session HMAC secrets |
| `database.yml` | SQLITE (default), MYSQL, MARIADB |
| `premium.yml` | Auto-login, reserved-name protection, HMAC bridge |
| `sounds.yml` | Per-event sound, volume, pitch |
| `sessions.yml` | Reconnect sessions (not used to authenticate Premium) |
| `account-limits.yml` | Max accounts per IP, whitelist/blacklist |
| `login-restrictions.yml` | Pre-auth freeze and allowed commands |
| `login-spawn.yml` | Optional spawn used before login |
| `database/` | SQLite file |
| `backups/` | Automatic and `/bmlogin backup` copies |

## Language

```yaml
language: ro   # ro | en | ro-ascii
```

`/bmlogin language <ro|en|ro-ascii>` writes the setting and reloads every player-facing string.

## Premium (enabled by default)

```yaml
premium:
  auto-auth:
    enabled: true
    default: true
  disable-password-login: true
  disable-registration: true
  protect-reserved-names: true
  reject-unverified-sessions: true
```

Leave `premium.yml` `bridge.shared-secret` empty. The Velocity bridge and Paper both use the existing Velocity modern-forwarding secret. No extra copy-paste is required after a normal Velocity install.

Success message (English pack):

```
&#00FB5C&lB&#17FB6B&ll&#2EFC7A&la&#46FC88&lz&#5DFC97&le&#74FDA6&lM&#8BFDB5&lC &#A2FEC4&lL&#B9FED3&lo&#D1FEE1&lg&#E8FFF0&li&#FFFFFF&ln &r&7∙ &aYou have been automatically authenticated because you are using a Minecraft Premium account!
```

## Movement

Unauthenticated walking is **cancelled**. The plugin does not teleport the player every tick. Look (yaw/pitch) is preserved. A single emergency teleport is allowed only if the player is more than `emergency-correction-blocks` from the freeze point (anti-cheat bypass), at most once every two seconds.

## Sounds

Master switch in `config.yml` (`sounds.enabled`). Every event in `sounds.yml` has `enabled`, `sound`, `volume`, `pitch`.

## Secrets

`security.yml` `ip-hmac` / `session-hmac` are generated on first start if empty. Keep the IP HMAC stable across backups so account limits still match after a restore. Premium HMAC is the Velocity forwarding secret unless you set `shared-secret` on both sides.
