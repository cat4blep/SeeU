# SeeU

`SeeU` is a mod that lets you see players far beyond vanilla entity render distance.

Discuss on Discord -> https://discord.gg/fQqJsPmQrP

## What It Does

- renders distant players outside vanilla entity range
- keeps player pose, yaw, pitch and name
- renders held items and armor on distant player proxies
- renders ridden entities too, so a player in a boat appears in a boat instead of standing in air
- works with a `Fabric` client and either a `Paper` server plugin or a `Fabric` server sender
- lets each player control whether other people can see their distant proxy and up to what distance

## How It Works

The server sends lightweight snapshots of players to clients with `SeeU`.

Each client renders those snapshots as remote player proxies only when the real vanilla player entity is no longer being rendered normally. This gives a smooth handoff from vanilla entity rendering to distant proxy rendering.

## Supported Setup

- Minecraft `26.1.2`
- Java `25`
- Fabric client
- `Voxy` optional on the client
- Server side: either `Paper` plugin or `Fabric` server with the same sender built into the mod

## How To Use

1. Install `SeeU` on the client.
2. Install either the `Paper` plugin or the `Fabric` server sender.
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
