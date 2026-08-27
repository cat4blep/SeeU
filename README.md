![SeeU banner](docs/banner.png)

Discuss SeeU on [Discord](https://discord.gg/fQqJsPmQrP).

## SeeU

SeeU renders players beyond vanilla's entity-tracking range. A `Paper`, `Fabric`, or `NeoForge` server sends player snapshots to a `Fabric` or `NeoForge` client. After vanilla stops rendering a player, the client replaces that entity with a remote-player proxy that retains:

- pose, yaw, pitch, and name
- held items and armor
- the ridden entity, such as a boat

Each player controls whether the server shares their proxy and the proxy's maximum viewing distance.

SeeU provides an addon bus on Fabric and NeoForge. Addons negotiate their own protocol through SeeU and send bounded payloads over SeeU-owned channels. One failing addon closes its own session without closing SeeU or another addon.

## Requirements

- Minecraft `26.2`
- Java `25`
- `Fabric` or `NeoForge` client
- `Paper` plugin, Fabric server mod, or NeoForge server mod

Install the matching client and server JARs from the same SeeU release. Join the server, then move beyond vanilla's player-rendering range. `Voxy` and `Distant Horizons` are optional terrain mods; SeeU renders the player proxies.

The default schedule sends player movement every two server ticks. The client adjusts interpolation to the received cadence and snaps teleports of 32 blocks or more. Servers that kept the old ten-tick value migrate to two ticks; custom intervals stay unchanged.

## SeeU Extra

SeeU Extra is a separate Fabric and NeoForge addon for non-player entities. Install SeeU and SeeU Extra on both the modded server and each client. Paper stays player-only and cannot run SeeU Extra.

The server writes `config/seeu-extra-server.json`. It starts with `mode` set to `DISABLED`, so the addon performs no entity scan until an administrator enables it.

- `SELECTED` accepts entity IDs from `types` and registry namespaces from `namespaces`.
- `ALL` accepts eligible loaded non-player entities.
- `excludedTypes` and `excludedNamespaces` take precedence in both modes.
- `maximumDistanceBlocks`, `minimumDistanceBlocks`, `entityCap`, and `updateIntervalTicks` bound server work and traffic.

Example:

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

The client writes `config/seeu-extra-client.json` with its enable switch and distance limits. The server chooses the lower maximum distance and the higher minimum distance from both configurations. Restart the client or server after editing either file.

SeeU Extra reads loaded entities and does not load chunks. It skips players and entities carrying a player. The client must have the mod that registers each selected entity type. SeeU Extra copies common state such as position, rotation, velocity, pose, flags, and equipment; renderers that depend on custom tracked data can differ from the server entity.

## Settings

`F8` is the default settings key. Remap it under **Options > Controls > Key Binds**. On Fabric, Mod Menu adds a **Configure** button to the SeeU entry. On NeoForge, open **Mods > SeeU > Config**.

- enable distant-player rendering
- set local render and animation distances
- set the handoff distance from vanilla entities to proxies
- show or hide distant-player name tags
- allow or block sharing your proxy
- limit how far other players can receive your proxy

The client sends changes to the server without a reconnect.

## Config files

- Fabric client: `config/seeu-client.json`
- Fabric server: `config/seeu-server.json`
- NeoForge client and server: `config/seeu-client.json`, `config/seeu-server.json`
- Paper: `plugins/SeeU/config.yml`
- SeeU Extra client and server: `config/seeu-extra-client.json`, `config/seeu-extra-server.json`

## Limits

- SeeU creates proxies for players and their ridden entities.
- Player protocol version `5` requires matching client and server/plugin updates.
- A client without a server sender receives no player snapshots.
- The addon bus accepts up to 32 addons, 32 KiB handshakes, and 1 MiB messages. Each addon can declare a smaller message limit.

## Build

```powershell
./gradlew build
```

Gradle writes the JARs to:

- `fabric/build/libs/seeu-fabric-<version>.jar`
- `neoforge/build/libs/seeu-neoforge-<version>.jar`
- `paper/build/libs/seeu-paper-<version>.jar`
- `addons/seeu-extra/fabric/build/libs/seeu-extra-fabric-<version>.jar`
- `addons/seeu-extra/neoforge/build/libs/seeu-extra-neoforge-<version>.jar`

## License

See [LICENSE](LICENSE). The project uses `LicenseRef-SeeU-Restricted-1.0`.
