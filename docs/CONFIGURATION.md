# BlazeMCLogin configuration

All files live in `plugins/BlazeMCLogin/`.

| File | Purpose |
| --- | --- |
| `config.yml` | Language (`ro` / `en` / `ro-ascii`), debug, proxy address, worker threads |
| `messages.yml` | Prefix and optional per-key overrides |
| `lang/messages-*.yml` | Full translations (edit these to customise a language) |
| `security.yml` | Brute-force, password policy, Argon2id, HMAC secrets |
| `database.yml` | SQLITE (default), MYSQL, MARIADB |
| `premium.yml` | Auto-login and HMAC bridge |
| `sessions.yml` | Reconnect sessions |
| `account-limits.yml` | Max accounts per IP, whitelist/blacklist |
| `login-restrictions.yml` | Pre-auth freeze and allowed commands |
| `login-spawn.yml` | Optional spawn used before login |
| `database/` | SQLite file |
| `backups/` | Automatic and `/bmlogin backup` copies |

## Language

```yaml
# config.yml
language: ro
```

Values:

- `ro` — Romanian with diacritics (default)
- `en` — English
- `ro-ascii` — Romanian without ă, â, î, ș, ț

`/bmlogin language <ro|en|ro-ascii>` writes the setting and reloads every player-facing string.

## Premium auto-login defaults

```yaml
premium:
  auto-login:
    enabled: true
    success-message: true
    require-verified-identity: true
    bypass-password: true
```

Success message (exact gradient):

```
&#00FB5C&lB&#17FB6B&ll&#2EFC7A&la&#46FC88&lz&#5DFC97&le&#74FDA6&lM&#8BFDB5&lC &#A2FEC4&lL&#B9FED3&lo&#D1FEE1&lg&#E8FFF0&li&#FFFFFF&ln &r&7∙ Logat automat cu premium!
```

## Secrets

`security.yml` `ip-hmac` / `session-hmac` and `premium.yml` `shared-secret` are generated on first start if empty. Keep the IP HMAC stable across backups so account limits still match after a restore.
