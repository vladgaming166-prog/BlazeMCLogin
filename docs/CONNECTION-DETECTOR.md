# BlazeMC Connection Detector integration

BlazeMCLogin does **not** decide that a player is Premium by looking at the client, IP address, UUID format, or any unsigned plugin message.

The network already runs **BlazeMC Connection Detector** on Velocity. That plugin:

1. Looks up `https://api.mojang.com/users/profiles/minecraft/{username}` off the event loop.
2. Calls `PreLoginEvent.forceOnlineMode()` only for real Mojang profiles.
3. Lets Velocity complete a real Mojang/Microsoft session.
4. Confirms `Player.isOnlineMode()`.
5. Restores the Mojang UUID at `GameProfileRequestEvent`.

The detector does not send a signed payload to Paper. BlazeMCLogin's Velocity bridge does.

## Zero-config HMAC

If `bridge.shared-secret` / Paper `premium.bridge.shared-secret` are left empty, both sides HMAC with the Velocity **modern forwarding secret** (`forwarding.secret` on the proxy, `config/paper-global.yml` `proxies.velocity.secret` on Paper). That secret is already required for the proxy to work.

## Protocol `blazemclogin:v1`

HMAC-SHA256 over a binary body:

| Field | Type |
| --- | --- |
| magic | `u32` `BML1` (`0x424D4C31`) |
| version | `u8` `1` |
| uuid | two `i64` (MSB, LSB) |
| username | UTF |
| premium | `u8` boolean (`1` only after `Player.isOnlineMode()`) |
| timestamp | `i64` epoch millis |
| nonce | 16 random bytes |
| detector version | UTF |
| hmac | 32 bytes HMAC-SHA256(secret, body) |

Rejected when the secret is missing, the MAC is wrong, the packet is expired, the nonce was already seen, or the UUID/username does not match the joining player.

A signed `premium=0` packet is a **cracked verdict**, not a Premium login. Packets that arrive before `PlayerJoinEvent` are queued.

The bridge retries plugin messages after `ServerPostConnectEvent` so the backend channel is registered.

## Reserved Premium names

On Velocity `PreLoginEvent` the bridge looks up the username at Mojang:

- HTTP 200 → `forceOnlineMode()` (cracked clients fail the encryption handshake)
- HTTP 204/404 → cracked login is allowed
- Transport / HTTP error → the connection is **rejected** (`reject-unverified-sessions`, default true)

Paper also refuses `/login` and `/register` when the username already belongs to a Premium account in the database, even after reconnect or IP change.

## LimboAPI

If Elytrium LimboAPI is loaded, verified Premium players (`Player.isOnlineMode()`) skip the login limbo via `LoginLimboRegisterEvent.clearCallback()`. LimboAPI is optional.

## Setup

1. Keep BlazeMC Connection Detector on Velocity (`online-mode = false`, modern forwarding).
2. Install `BlazeMCLogin-Velocity-Bridge.jar` in Velocity `plugins/`.
3. Install the matching Paper JAR in every backend `plugins/` folder.
4. Leave shared-secret empty **or** copy the same hex into both configs.
5. Restart both sides.

If `premium.auto-auth.enabled` is `false` on Paper, Premium packets do not auto-login. Password login for Premium names stays disabled while `disable-password-login` is true.
