# SeeU

`SeeU` is a mod that lets you see players far beyond vanilla entity render distance.

Discuss on Discord -> https://discord.gg/R5b6rdMNTQ

## What It Does

- renders distant players outside vanilla entity range
- keeps player pose, yaw, pitch and name
- renders held items and armor on distant player proxies
- renders ridden entities too, so a player in a boat appears in a boat instead of standing in air
- works with a `Fabric` or `NeoForge` client and a `Paper`, `Fabric`, or `NeoForge` server sender
- lets each player control whether other people can see their distant proxy and up to what distance

## How It Works

The server sends lightweight snapshots of players to clients with `SeeU`.

Each client renders those snapshots as remote player proxies only when the real vanilla player entity is no longer being rendered normally. This gives a smooth handoff from vanilla entity rendering to distant proxy rendering.

## Supported Setup

- Minecraft `1.21.11`
- Java `21`
- Fabric client or NeoForge client
- `Voxy` optional on the client
- Server side: `Paper` plugin, Fabric server mod, or NeoForge server mod

## How To Use

1. Install the matching `SeeU` Fabric or NeoForge jar on the client.
2. Install either the `Paper` plugin, Fabric server jar, or NeoForge server jar.
3. Start the server and client with matching current jars.
4. Join the server.
5. Fly or move far enough for vanilla player rendering to end.
6. `SeeU` will render the distant player proxy.
7. If `Voxy` is installed, those proxies can also be seen on top of distant Voxy terrain.

## In-Game Settings

Open the settings GUI with `F8`.

Available options:

- enable or disable distant player rendering on your client
- set your local maximum distant-player render distance
- set the handoff distance where proxy rendering starts
- enable or disable distant player name tags
- allow or forbid the server from broadcasting your own distant proxy to other players
- set the maximum distance at which other players are allowed to receive your proxy

When you change these settings in the GUI, the client immediately sends them to the server. Reconnect is not required.

## Config Files

Client:

- `config/seeu-client.json`

Fabric server:

- `config/seeu-server.json`

NeoForge client/server:

- `config/seeu-client.json`
- `config/seeu-server.json`

Paper:

- `plugins/SeeU/config.yml`

## Notes

- `SeeU` is for players only. It does not render all entities at long range.
- The client and server/plugin should be updated together. The current protocol version is `3`.
- If you only install the client mod without a matching server sender, there will be no distant player data to render.
- `Voxy` or `Distant Horizons` is optional. Without it, `SeeU` still works, but you only get distant player proxies, not distant terrain.

## Build

```powershell
./gradlew build
```

Built jars are written to:

- `fabric/build/libs/seeu-fabric-1.21.11-<version>.jar`
- `neoforge/build/libs/seeu-neoforge-1.21.11-<version>.jar`
- `paper/build/libs/seeu-paper-1.21.11-<version>.jar`
