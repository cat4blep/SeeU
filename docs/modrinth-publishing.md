# Publishing to Modrinth

`scripts/publish_modrinth.py` publishes the current branch build output through the Modrinth v2 API.

From the repository root:

```powershell
python scripts/publish_modrinth.py ...
```

From the `scripts` directory:

```powershell
python .\publish_modrinth.py ...
```

## Required environment

```powershell
$env:MODRINTH_TOKEN = "your-token"
$env:MODRINTH_PROJECT_ID = "your-project-id-or-slug"
```

The public SeeU project uses slug `seeu` and project id `coyNPDey`. The script accepts either value and submits the resolved id when it creates a version.

The token needs `VERSION_CREATE`. Private projects and draft versions also require read access for duplicate checks.

HTTP 401 means the token lacks the required scope or its user cannot upload to the selected project. Check `MODRINTH_PROJECT_ID` and use a token owned by a project owner or team member with upload permission.

If Fabric and Paper upload but NeoForge fails, enable the `neoforge` loader in the Modrinth project settings.

## Publish current branch

```powershell
python scripts/publish_modrinth.py --build --status unlisted
```

`--build` runs `./gradlew clean build`. Use `--status unlisted` for a reviewable first upload: Modrinth hides it from the public list but exposes it through its direct URL and project versions page. Drafts can be harder to find in the UI.

The script uploads three versions:

- `fabric/build/libs/*.jar` with loader `fabric`
- `neoforge/build/libs/*.jar` with loader `neoforge`
- `paper/build/libs/*.jar` with loader `paper`

It ignores sources, dev, and javadoc jars.

## Publish all maintained branches

From the repository root:

```powershell
python scripts/publish_modrinth.py --all-branches --build --status unlisted
```

From the `scripts` directory:

```powershell
python .\publish_modrinth.py --all-branches --build --status unlisted
```

The command publishes these maintained branches:

- `backport-1.21.1`
- `backport-1.21.11`
- `26.1.2`
- `main`

`--all-branches` requires a clean working tree because the script switches among existing branches. It restores the original branch when it finishes.

For `--build`, the script reads `java_version` from `gradle.properties` and selects a matching installed JDK. Pass the JDK path if detection fails:

```powershell
python scripts/publish_modrinth.py --build --java-home "C:\Program Files\Java\jdk-25"
```

## Version numbers

Modrinth version numbers use:

```text
{mod_version}+mc{minecraft_version}-{loader}
```

Example: `0.7+mc26.2-fabric`.

The displayed version name defaults to `mod_version`, such as `0.7`.

The script skips an existing file hash or `version_number`, which lets a rerun continue after a partial upload. For a distinct build, increment `mod_version` or add a suffix:

```powershell
python scripts/publish_modrinth.py --build --version-suffix ".1"
```

To fail when `version_number` exists:

```powershell
python scripts/publish_modrinth.py --build --fail-on-existing-version
```

## Useful options

```powershell
# Show planned uploads without calling Modrinth.
python scripts/publish_modrinth.py --dry-run --project-id seeu

# Publish Fabric and NeoForge.
python scripts/publish_modrinth.py --only fabric neoforge

# Publish as beta or alpha.
python scripts/publish_modrinth.py --version-type beta

# Use a changelog file.
python scripts/publish_modrinth.py --changelog-file CHANGELOG.md

# Add Fabric API as a required Fabric dependency if needed.
python scripts/publish_modrinth.py --fabric-api-project "<fabric-api-project-id>"
```
