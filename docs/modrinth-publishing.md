# Publishing to Modrinth

Use `scripts/publish_modrinth.py` from the repository root. The script publishes the current branch build output to Modrinth through the v2 API.

## Required environment

```powershell
$env:MODRINTH_TOKEN = "your-token"
$env:MODRINTH_PROJECT_ID = "your-project-id-or-slug"
```

The token needs permission to create versions. If the project or versions are private/draft, also give the token read access so the duplicate checks can see existing versions.

## Publish current branch

```powershell
python scripts/publish_modrinth.py --build --status draft
```

`--build` runs `./gradlew clean build` first. `--status draft` is recommended for the first run so files can be checked on Modrinth before listing.

By default the script uploads three separate Modrinth versions:

- `fabric/build/libs/*.jar` with loader `fabric`
- `neoforge/build/libs/*.jar` with loader `neoforge`
- `paper/build/libs/*.jar` with loader `paper`

Sources, dev, and javadoc jars are ignored.

## Publish all maintained branches

Run the script once per branch:

```powershell
git switch backport-1.21.1
python scripts/publish_modrinth.py --build --status draft

git switch backport-1.21.11
python scripts/publish_modrinth.py --build --status draft

git switch 26.1.2
python scripts/publish_modrinth.py --build --status draft

git switch main
python scripts/publish_modrinth.py --build --status draft
```

Use Java 21 for `1.21.x` branches and Java 25 for `26.x` branches.

## Version numbers

Generated Modrinth version numbers use:

```text
{mod_version}+mc{minecraft_version}-{loader}
```

Example: `0.6+mc26.2-fabric`.

If Modrinth already has the same file hash, the script skips it. If the same `version_number` already exists with a different file, the script stops; bump `mod_version` or add a suffix:

```powershell
python scripts/publish_modrinth.py --build --version-suffix ".1"
```

## Useful options

```powershell
# Show planned uploads without calling Modrinth.
python scripts/publish_modrinth.py --dry-run --project-id seeu

# Publish only Fabric and NeoForge.
python scripts/publish_modrinth.py --only fabric neoforge

# Publish as beta or alpha.
python scripts/publish_modrinth.py --version-type beta

# Use a changelog file.
python scripts/publish_modrinth.py --changelog-file CHANGELOG.md

# Add Fabric API as a required Fabric dependency if needed.
python scripts/publish_modrinth.py --fabric-api-project "<fabric-api-project-id>"
```
