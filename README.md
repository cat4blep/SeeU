# SeeU

Discuss SeeU on [Discord](https://discord.gg/fQqJsPmQrP).

SeeU renders players beyond vanilla's entity-tracking range. A Paper plugin or a Fabric/NeoForge server mod sends player snapshots to the matching Fabric or NeoForge client. The client creates a remote-player proxy after vanilla stops rendering the real entity. The proxy keeps the player's pose, rotation, name, equipment, and ridden entity.

Each player controls whether the server may share their proxy and how far it may be sent. The default update interval is two server ticks. The client adjusts interpolation to the observed packet cadence and snaps movements of 32 blocks or more instead of easing through a teleport.

Fabric and NeoForge builds also expose the SeeU addon bus. Addons negotiate their own protocol and use SeeU-owned, bounded transport channels. A failed addon session closes without taking down SeeU or another addon.

## Requirements

- Minecraft `1.21.11`
- Java `21`
- SeeU `0.9.0` on Fabric or NeoForge clients
- SeeU `0.9.0` on Fabric or NeoForge servers, or SeeU Paper `0.9`

Install client and server files from the same release. Voxy and Distant Horizons are optional; SeeU does not depend on either terrain renderer.

## SeeU Extra

SeeU Extra `0.1.0` is a separate addon for non-player entities. It runs only on Fabric and NeoForge. Install SeeU and SeeU Extra on the modded server and on every client that should render these entities. Paper remains player-only and does not load SeeU Extra.

SeeU Extra does not register a separate network channel. It sends its handshake and snapshots through the addon API provided by SeeU `0.9.0`.

The server writes `config/seeu-extra-server.json`. Its default mode is `DISABLED`, which prevents entity scans until an administrator opts in.

- `DISABLED` sends no non-player entities.
- `SELECTED` matches exact entity IDs in `types` and exact registry namespaces in `namespaces`.
- `ALL` matches every eligible loaded non-player entity.
- `excludedTypes` and `excludedNamespaces` override both active modes.
- `maximumDistanceBlocks`, `minimumDistanceBlocks`, `entityCap`, and `updateIntervalTicks` limit work and traffic.

Example server config:

```json
{
  "configVersion": 1,
  "mode": "SELECTED",
  "types": ["minecraft:zombie"],
  "namespaces": ["iceandfire"],
  "excludedTypes": [],
  "excludedNamespaces": [],
  "maximumDistanceBlocks": 8192,
  "minimumDistanceBlocks": 0,
  "entityCap": 128,
  "updateIntervalTicks": 4
}
```

The client writes `config/seeu-extra-client.json` with an enable switch and its distance limits. The server uses the lower maximum distance and the higher minimum distance from the two configs. Restart the client or server after editing either SeeU Extra config; both files are loaded during initialization.

The addon scans only entities already loaded by the server. It does not load chunks, never proxies players, and skips an entity if it carries a player. Each client must have the mod that registers a selected entity type. Common position, rotation, velocity, pose, flags, and equipment are copied. A renderer that relies on custom tracked data may still look different from the server entity.

## Settings

`F8` opens the SeeU client settings by default. Remap it under **Options > Controls > Key Binds**. Fabric users can also open the screen through Mod Menu; NeoForge users can use **Mods > SeeU > Config**.

The screen controls distant-player rendering, render and animation distance, the vanilla-to-proxy handoff distance, name tags, and player sharing. Changes to the main SeeU client settings are sent without reconnecting.

## Config files

- Fabric client: `config/seeu-client.json`
- Fabric server: `config/seeu-server.json`
- NeoForge client and server: `config/seeu-client.json`, `config/seeu-server.json`
- Paper: `plugins/SeeU/config.yml`
- SeeU Extra: `config/seeu-extra-client.json`, `config/seeu-extra-server.json`

## Compatibility limits

Player protocol version `5` requires matching client and server/plugin builds. A client without a server sender receives no snapshots. The addon bus accepts at most 32 addons, 32 KiB handshakes, and 1 MiB messages; an addon may declare a smaller limit.

## Build

```powershell
./gradlew build
```

Gradle writes the distributable files to:

- `fabric/build/libs/seeu-fabric-1.21.11-0.9.0.jar`
- `neoforge/build/libs/seeu-neoforge-1.21.11-0.9.0.jar`
- `paper/build/libs/seeu-paper-1.21.11-0.9.jar`
- `addons/seeu-extra/fabric/build/libs/seeu-extra-fabric-1.21.11-0.1.0.jar`
- `addons/seeu-extra/neoforge/build/libs/seeu-extra-neoforge-1.21.11-0.1.0.jar`
