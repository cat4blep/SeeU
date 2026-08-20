# SeeU

Discuss SeeU on [Discord](https://discord.gg/fQqJsPmQrP).

SeeU renders players beyond vanilla's entity-tracking range. A `Paper`, `Fabric`, or `NeoForge` server sends player snapshots to a `Fabric` or `NeoForge` client. After vanilla stops rendering a player, the client replaces that entity with a remote-player proxy that retains:

- pose, yaw, pitch, and name
- held items and armor
- the ridden entity, such as a boat

Each player controls whether the server shares their proxy and the proxy's maximum viewing distance.

## Requirements

- Minecraft `1.21.1`
- Java `21`
- `Fabric` or `NeoForge` client
- `Paper` plugin, Fabric server mod, or NeoForge server mod

Install the matching client and server JARs from the same SeeU release. Join the server, then move beyond vanilla's player-rendering range. `Voxy` and `Distant Horizons` are optional terrain mods; SeeU renders the player proxies.

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

## Limits

- SeeU creates proxies for players and their ridden entities.
- Protocol version `3` requires matching client and server/plugin updates.
- A client without a server sender receives no player snapshots.

## Build

```powershell
./gradlew build
```

Gradle writes the JARs to:

- `fabric/build/libs/seeu-fabric-1.21.1-<version>.jar`
- `neoforge/build/libs/seeu-neoforge-1.21.1-<version>.jar`
- `paper/build/libs/seeu-paper-1.21.1-<version>.jar`
