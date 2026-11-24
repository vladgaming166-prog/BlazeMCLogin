# BlazeMC Connection Detector integration

BlazeMCLogin does **not** decide that a player is Premium by looking at the client, IP address, UUID format, or any unsigned plugin message.

The network already runs **BlazeMC Connection Detector** on Velocity (`blazemc-connection-detector` 1.1.0). That plugin:

1. Looks up `https://api.mojang.com/users/profiles/minecraft/{username}` off the event loop.
2. Calls `PreLoginEvent.forceOnlineMode()` only for real Mojang profiles.
3. Lets Velocity complete a real Mojang/Microsoft session.
4. Confirms `Player.isOnlineMode()` (and `LoginEvent.getServerIdHash()` on Velocity 3.5+).
5. Restores the Mojang UUID at `GameProfileRequestEvent`.
6. Optionally bypasses LimboAuth after that verification.

Inspected source (PR branch `cursor/blazemc-connection-detector-72e8`): the detector **does not** currently send a signed verification payload to Paper. It only forwards the Mojang UUID through Velocity modern forwarding and talks to LimboAuth on the proxy.

BlazeMCLogin therefore ships an **optional Velocity bridge** that is compatible with the detector instead of pretending Paper can see the Mojang handshake.

## Protocol `blazemclogin:v1`

HMAC-SHA256 over a binary body:

| Field | Type |
| --- | --- |
| magic | `u32` `BML1` (`0x424D4C31`) |
| version | `u8` `1` |
| uuid | two `i64` (MSB, LSB) |
| username | UTF |
| premium | `u8` boolean |
| timestamp | `i64` epoch millis |
| nonce | 16 random bytes |
| detector version | UTF |
| hmac | 32 bytes HMAC-SHA256(secret, body) |

Rejected when the secret is missing, the MAC is wrong, the packet is expired, the nonce was already seen, the UUID/username does not match the joining player, or `premium=false`.

Paper never authenticates silently if the packet is missing or invalid. Cracked players still use `/register` and `/login`.

## Setup

1. Keep BlazeMC Connection Detector on Velocity (`online-mode = false`, modern forwarding).
2. Install `BlazeMCLogin-Velocity-Bridge.jar` in Velocity `plugins/`.
3. Install the matching Paper JAR in every backend `plugins/` folder.
4. Copy the same `shared-secret` into:
   - Velocity `plugins/blazemclogin-bridge/config.yml` → `bridge.shared-secret`
   - Paper `plugins/BlazeMCLogin/premium.yml` → `premium.bridge.shared-secret`
5. Restart both sides. Do not put the secret in chat, tickets, or logs.

If `premium.auto-login.enabled` is `false` on Paper, even a valid Premium packet is ignored and the player must use a password.

## What the bridge uses as proof

`Player.isOnlineMode()` after Connection Detector has already forced a Mojang session. The bridge does not call Mojang itself and does not trust the connecting client.
