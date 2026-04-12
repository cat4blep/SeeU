# Voxy SeeU

`Voxy SeeU` is a cross-platform addon for Voxy that makes distant players visible far beyond vanilla entity tracking.

It contains:

- a `Fabric` mod for `26.1.2` with client rendering and Fabric server support
- a `Paper` plugin for `26.1.2` that streams the same player snapshots to the Fabric client mod
- a shared binary protocol used by both server implementations

The client renders distant player proxies only. Terrain LOD remains handled by Voxy itself.

## Modules

- `common` - shared packet models and codecs
- `fabric` - Fabric mod (client + optional Fabric server sender)
- `paper` - Paper plugin sender

## Build

The project targets Java 25 and `26.1.2`.

```powershell
./gradlew build
```
