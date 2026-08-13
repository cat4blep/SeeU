![SeeU banner](docs/banner.png)

Discuss on Discord -> https://discord.gg/fQqJsPmQrP

## SeeU

SeeU renders players beyond vanilla's entity range. A `Paper`, `Fabric`, or `NeoForge` server sends lightweight snapshots to a `Fabric` or `NeoForge` client. Once vanilla stops rendering a player entity, the client replaces it with a remote-player proxy that retains:

- pose, yaw, pitch, and name
- held items and armor
- the ridden entity, such as a boat

Each player controls whether the server shares their proxy and how far it can be seen.

## Requirements

- Minecraft `26.2`
- Java `25`
- `Fabric` or `NeoForge` client
- `Paper` plugin, Fabric server mod, or NeoForge server mod

Install matching current SeeU jars on the client and server, join the server, then move beyond vanilla's player-rendering range. `Voxy` and `Distant Horizons` are optional; they add distant terrain, while SeeU supplies the player proxies.

## Settings

Open the settings GUI with `F8`.

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

- SeeU renders players and their ridden entities, not every entity type.
- Protocol version `4` requires matching client and server/plugin updates.
- A client without a server sender receives no player snapshots.

## Build

```powershell
./gradlew build
```

Gradle writes the jars to:

- `fabric/build/libs/seeu-fabric-<version>.jar`
- `neoforge/build/libs/seeu-neoforge-<version>.jar`
- `paper/build/libs/seeu-paper-<version>.jar`

## License

See [LICENSE](LICENSE). The project uses `LicenseRef-SeeU-Restricted-1.0`.
