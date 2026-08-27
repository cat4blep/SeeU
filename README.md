# SeeU

Discuss SeeU on [Discord](https://discord.gg/fQqJsPmQrP).

SeeU renders players beyond vanilla's entity-tracking range. A Paper plugin or a Fabric/NeoForge server mod sends player snapshots to the matching Fabric or NeoForge client. The client creates a remote-player proxy after vanilla stops rendering the real entity. The proxy keeps the player's pose, rotation, name, equipment, and ridden entity.

Each player controls whether the server may share their proxy and how far it may be sent. The default update interval is two server ticks. The client adjusts interpolation to the observed packet cadence and snaps movements of 32 blocks or more instead of easing through a teleport.

Fabric and NeoForge builds also expose the SeeU addon bus. Addons negotiate their own protocol and use SeeU-owned, bounded transport channels. A failed addon session closes without taking down SeeU or another addon.

## Requirements

- Minecraft `1.21.11`
- Java `21`
- SeeU `0.9.1` on Fabric or NeoForge clients
- SeeU `0.9.1` on Fabric or NeoForge servers, or SeeU Paper `0.9.1`

Install client and server files from the same release. Voxy and Distant Horizons are optional; SeeU does not depend on either terrain renderer.

## SeeU Extra

[SeeU Extra](https://github.com/cat4blep/SeeU-Extra) is maintained in its own repository. It adds long-range non-player entity rendering to Fabric and NeoForge through SeeU's addon API and transport stack. Install matching SeeU and SeeU Extra versions on both the modded server and its clients.

Paper remains player-only; it does not package the addon API or support SeeU Extra.

## Settings

`F8` opens the SeeU client settings by default. Remap it under **Options > Controls > Key Binds**. Fabric users can also open the screen through Mod Menu; NeoForge users can use **Mods > SeeU > Config**.

The screen controls distant-player rendering, render and animation distance, the vanilla-to-proxy handoff distance, name tags, and player sharing. Changes to the main SeeU client settings are sent without reconnecting.

## Config files

- Fabric client: `config/seeu-client.json`
- Fabric server: `config/seeu-server.json`
- NeoForge client and server: `config/seeu-client.json`, `config/seeu-server.json`
- Paper: `plugins/SeeU/config.yml`

## Compatibility limits

Player protocol version `5` requires matching client and server/plugin builds. A client without a server sender receives no snapshots. The addon bus accepts at most 32 addons, 32 KiB handshakes, and 1 MiB messages; an addon may declare a smaller limit.

## Build

```powershell
./gradlew build
```

Gradle writes the distributable files to:

- `fabric/build/libs/seeu-fabric-1.21.11-0.9.1.jar`
- `neoforge/build/libs/seeu-neoforge-1.21.11-0.9.1.jar`
- `paper/build/libs/seeu-paper-1.21.11-0.9.1.jar`
- `addon-api/build/libs/seeu-addon-api-1.21.11-0.9.1.jar`
