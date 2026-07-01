#!/usr/bin/env python3
"""Publish built SeeU artifacts to Modrinth.

This script intentionally uses only Python's standard library so it can run
locally and in GitHub Actions without installing dependencies.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import mimetypes
import os
import re
import shutil
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid
from dataclasses import dataclass
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_API_BASE = "https://api.modrinth.com/v2"
DEFAULT_USER_AGENT = "cat4blep/SeeU publish_modrinth.py"
DEFAULT_BRANCHES = ("backport-1.21.1", "backport-1.21.11", "26.1.2", "main")
PUBLISHABLE_MODULES = {
    "fabric": {
        "loader": "fabric",
        "display": "Fabric",
        "environment": "client_and_server",
        "libs": ROOT / "fabric" / "build" / "libs",
    },
    "neoforge": {
        "loader": "neoforge",
        "display": "NeoForge",
        "environment": "client_and_server",
        "libs": ROOT / "neoforge" / "build" / "libs",
    },
    "paper": {
        "loader": "paper",
        "display": "Paper",
        "environment": "server_only",
        "libs": ROOT / "paper" / "build" / "libs",
    },
}


@dataclass(frozen=True)
class Artifact:
    module: str
    loader: str
    loader_display: str
    environment: str
    path: Path
    sha512: str


class ModrinthError(RuntimeError):
    pass


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Build and publish SeeU Fabric, NeoForge, and Paper artifacts to Modrinth."
    )
    parser.add_argument(
        "--project-id",
        default=os.environ.get("MODRINTH_PROJECT_ID"),
        help="Modrinth project id or slug. Defaults to MODRINTH_PROJECT_ID.",
    )
    parser.add_argument(
        "--token",
        default=os.environ.get("MODRINTH_TOKEN"),
        help="Modrinth token with VERSION_CREATE scope. Defaults to MODRINTH_TOKEN.",
    )
    parser.add_argument(
        "--api-base",
        default=os.environ.get("MODRINTH_API_BASE", DEFAULT_API_BASE),
        help=f"Modrinth API base URL. Defaults to {DEFAULT_API_BASE}.",
    )
    parser.add_argument(
        "--user-agent",
        default=os.environ.get("MODRINTH_USER_AGENT", DEFAULT_USER_AGENT),
        help="User-Agent header sent to Modrinth.",
    )
    parser.add_argument(
        "--only",
        choices=sorted(PUBLISHABLE_MODULES),
        nargs="+",
        default=sorted(PUBLISHABLE_MODULES),
        help="Limit publishing to selected modules.",
    )
    parser.add_argument(
        "--all-branches",
        action="store_true",
        help="Publish every maintained SeeU branch in one run.",
    )
    parser.add_argument(
        "--branches",
        nargs="+",
        default=None,
        help=(
            "Branches used by --all-branches. Defaults to: "
            + ", ".join(DEFAULT_BRANCHES)
        ),
    )
    parser.add_argument(
        "--keep-branch",
        action="store_true",
        help="With --all-branches, stay on the last processed branch instead of switching back.",
    )
    parser.add_argument(
        "--build",
        action="store_true",
        help="Run './gradlew clean build' before publishing.",
    )
    parser.add_argument(
        "--java-home",
        default=os.environ.get("MODRINTH_JAVA_HOME"),
        help=(
            "JDK home used for --build. Defaults to MODRINTH_JAVA_HOME, "
            "then JAVA<version>_HOME/JDK<version>_HOME/JAVA_HOME, then common install locations."
        ),
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Print planned uploads without calling Modrinth.",
    )
    parser.add_argument(
        "--skip-existing-check",
        action="store_true",
        help="Do not check Modrinth for already uploaded file hashes/version numbers.",
    )
    parser.add_argument(
        "--fail-on-existing-version",
        action="store_true",
        help="Fail instead of skipping when a generated version_number already exists on Modrinth.",
    )
    parser.add_argument(
        "--version-type",
        choices=("release", "beta", "alpha"),
        default=os.environ.get("MODRINTH_VERSION_TYPE", "release"),
        help="Modrinth release channel.",
    )
    parser.add_argument(
        "--status",
        choices=("listed", "unlisted", "draft"),
        default=os.environ.get("MODRINTH_STATUS", "listed"),
        help="Initial Modrinth version status.",
    )
    parser.add_argument(
        "--featured",
        action="store_true",
        help="Mark created versions as featured.",
    )
    parser.add_argument(
        "--game-version",
        action="append",
        help="Minecraft game version. Defaults to minecraft_version from gradle.properties.",
    )
    parser.add_argument(
        "--version-suffix",
        default=os.environ.get("MODRINTH_VERSION_SUFFIX", ""),
        help="Extra suffix appended to generated version_number.",
    )
    parser.add_argument(
        "--name-template",
        default=os.environ.get(
            "MODRINTH_NAME_TEMPLATE",
            "SeeU {mod_version} for Minecraft {minecraft_version} ({loader_display})",
        ),
        help="Python format string for Modrinth version name.",
    )
    parser.add_argument(
        "--number-template",
        default=os.environ.get(
            "MODRINTH_NUMBER_TEMPLATE",
            "{mod_version}+mc{minecraft_version}-{loader}",
        ),
        help="Python format string for Modrinth version_number.",
    )
    parser.add_argument(
        "--changelog",
        default=os.environ.get("MODRINTH_CHANGELOG"),
        help="Inline changelog text.",
    )
    parser.add_argument(
        "--changelog-file",
        type=Path,
        help="Path to a Markdown changelog file.",
    )
    parser.add_argument(
        "--dependency",
        action="append",
        default=[],
        metavar="PROJECT_ID:TYPE[:LOADER]",
        help=(
            "Add a Modrinth dependency. TYPE is required/optional/incompatible/embedded. "
            "Optional LOADER limits it to one loader. Example: P7dR8mSH:required:fabric"
        ),
    )
    parser.add_argument(
        "--fabric-api-project",
        default=os.environ.get("MODRINTH_FABRIC_API_PROJECT_ID"),
        help="Optional Fabric API project id to add as a required dependency for Fabric uploads.",
    )
    return parser.parse_args()


def read_gradle_properties() -> dict[str, str]:
    properties_path = ROOT / "gradle.properties"
    props: dict[str, str] = {}
    for raw_line in properties_path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        props[key.strip()] = value.strip()
    return props


def run_build(args: argparse.Namespace, props: dict[str, str]) -> None:
    gradlew = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
    command = [str(gradlew), "clean", "build"]
    env = build_environment(args, props)
    print("+ " + " ".join(command), flush=True)
    subprocess.run(command, cwd=ROOT, check=True, env=env)


def build_environment(args: argparse.Namespace, props: dict[str, str]) -> dict[str, str]:
    env = os.environ.copy()
    required = parse_required_java_version(props)
    if required <= 0:
        return env

    current = java_feature_version(None, env)
    if current is not None and current >= required:
        return env

    java_home = select_java_home(required, args.java_home, env)
    if java_home is None:
        current_text = f"current Java is {current}" if current is not None else "java is not on PATH"
        raise ModrinthError(
            f"Gradle needs Java {required}+ from gradle.properties, but {current_text}. "
            f"Install JDK {required}+ or pass --java-home/ set MODRINTH_JAVA_HOME."
        )

    bin_dir = java_home / "bin"
    path_key = "Path" if os.name == "nt" else "PATH"
    env["JAVA_HOME"] = str(java_home)
    env[path_key] = str(bin_dir) + os.pathsep + env.get(path_key, "")
    selected = java_feature_version(java_home, env)
    selected_text = str(selected) if selected is not None else "unknown"
    print(f"Using JAVA_HOME={java_home} for Gradle (Java {selected_text}).")
    return env


def parse_required_java_version(props: dict[str, str]) -> int:
    raw = props.get("java_version", "").strip()
    if not raw:
        return 0
    try:
        return int(raw)
    except ValueError as error:
        raise ModrinthError(f"Invalid java_version in gradle.properties: {raw}") from error


def java_feature_version(java_home: Path | None, env: dict[str, str]) -> int | None:
    java_executable = java_binary(java_home)
    try:
        completed = subprocess.run(
            [str(java_executable), "-version"],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            env=env,
            check=False,
        )
    except OSError:
        return None
    output = completed.stdout + completed.stderr
    match = re.search(r'version "(?P<major>\d+)(?:\.(?P<minor>\d+))?', output)
    if not match:
        return None
    major = int(match.group("major"))
    minor = match.group("minor")
    if major == 1 and minor is not None:
        return int(minor)
    return major


def java_binary(java_home: Path | None) -> Path | str:
    executable = "java.exe" if os.name == "nt" else "java"
    if java_home is None:
        return executable
    return java_home / "bin" / executable


def select_java_home(required: int, override: str | None, env: dict[str, str]) -> Path | None:
    candidates = java_home_candidates(required, override, env)
    usable: list[tuple[int, Path]] = []
    for candidate in candidates:
        version = java_feature_version(candidate, env)
        if version is not None and version >= required:
            usable.append((version, candidate))
    if not usable:
        return None
    usable.sort(key=lambda item: (item[0] != required, item[0]))
    return usable[0][1]


def java_home_candidates(required: int, override: str | None, env: dict[str, str]) -> list[Path]:
    raw_candidates: list[str] = []
    if override:
        raw_candidates.append(override)
    for name in (f"JAVA{required}_HOME", f"JDK{required}_HOME", "JAVA_HOME"):
        value = env.get(name)
        if value:
            raw_candidates.append(value)

    if os.name == "nt":
        for root in (
            Path(os.environ.get("ProgramFiles", r"C:\Program Files")) / "Java",
            Path(os.environ.get("ProgramFiles", r"C:\Program Files")) / "Eclipse Adoptium",
            Path(os.environ.get("ProgramFiles", r"C:\Program Files")) / "Microsoft",
        ):
            if root.exists():
                raw_candidates.extend(str(path) for path in root.iterdir() if path.is_dir())
    else:
        for root in (Path("/usr/lib/jvm"), Path("/opt/homebrew/opt"), Path("/Library/Java/JavaVirtualMachines")):
            if not root.exists():
                continue
            for path in root.iterdir():
                if not path.is_dir():
                    continue
                home = path / "Contents" / "Home"
                raw_candidates.append(str(home if home.exists() else path))

    system_java = shutil.which("java")
    if system_java:
        java_path = Path(system_java).resolve()
        if java_path.parent.name == "bin":
            raw_candidates.append(str(java_path.parent.parent))

    seen: set[Path] = set()
    candidates: list[Path] = []
    for raw in raw_candidates:
        path = Path(raw).expanduser()
        if not path.exists() or path in seen:
            continue
        seen.add(path)
        candidates.append(path)
    return candidates


def sha512(path: Path) -> str:
    digest = hashlib.sha512()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def is_publishable_jar(path: Path) -> bool:
    if path.suffix.lower() != ".jar":
        return False
    name = path.name.lower()
    return not any(
        marker in name
        for marker in ("-sources.jar", "-dev.jar", "-javadoc.jar")
    )


def discover_artifacts(modules: list[str]) -> list[Artifact]:
    artifacts: list[Artifact] = []
    for module in modules:
        config = PUBLISHABLE_MODULES[module]
        libs_dir = config["libs"]
        jars = sorted(path for path in libs_dir.glob("*.jar") if is_publishable_jar(path))
        if not jars:
            raise ModrinthError(f"No publishable jar found in {libs_dir}. Run with --build first.")
        if len(jars) > 1:
            jar_list = "\n  ".join(str(jar) for jar in jars)
            raise ModrinthError(
                f"Multiple publishable jars found for {module}; run with --build/clean or remove old jars:\n  {jar_list}"
            )
        path = jars[0]
        artifacts.append(
            Artifact(
                module=module,
                loader=str(config["loader"]),
                loader_display=str(config["display"]),
                environment=str(config["environment"]),
                path=path,
                sha512=sha512(path),
            )
        )
    return artifacts


def read_changelog(args: argparse.Namespace, props: dict[str, str]) -> str:
    if args.changelog_file:
        return args.changelog_file.read_text(encoding="utf-8").strip()
    if args.changelog:
        return str(args.changelog).strip()

    commit = run_git(["rev-parse", "--short", "HEAD"], fallback="unknown")
    branch = run_git(["branch", "--show-current"], fallback="unknown")
    return (
        f"Automated SeeU build for Minecraft {props['minecraft_version']}.\n\n"
        f"- Branch: `{branch}`\n"
        f"- Commit: `{commit}`"
    )


def run_git(args: list[str], fallback: str) -> str:
    try:
        completed = subprocess.run(
            ["git", *args],
            cwd=ROOT,
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            text=True,
        )
    except (FileNotFoundError, subprocess.CalledProcessError):
        return fallback
    value = completed.stdout.strip()
    return value if value else fallback


def ensure_clean_worktree() -> None:
    completed = subprocess.run(
        ["git", "status", "--porcelain"],
        cwd=ROOT,
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    status = completed.stdout.strip()
    if status:
        raise ModrinthError(
            "--all-branches needs a clean working tree before it can switch branches:\n"
            + status
        )


def switch_branch(branch: str) -> None:
    print(f"\n=== Switching to {branch} ===", flush=True)
    subprocess.run(["git", "switch", branch], cwd=ROOT, check=True)


def resolve_project_reference(args: argparse.Namespace) -> None:
    encoded_project = urllib.parse.quote(args.project_id)
    url = f"{args.api_base.rstrip('/')}/project/{encoded_project}"
    status, payload = request_json(
        "GET",
        url,
        modrinth_headers(args, authenticated=bool(args.token)),
        ok_statuses={200},
    )
    if status != 200 or not isinstance(payload, dict):
        raise ModrinthError(f"Could not resolve Modrinth project {args.project_id}.")

    project_id = payload.get("id")
    slug = payload.get("slug")
    title = payload.get("title")
    args._project_loaders = set(payload.get("loaders", []))
    if not project_id:
        raise ModrinthError(f"Modrinth project response for {args.project_id} did not include an id.")

    if args.project_id != project_id:
        label = title or slug or args.project_id
        print(f"Resolved Modrinth project {args.project_id} -> {project_id} ({label}).")
        args.project_id = project_id


def warn_if_loader_is_not_enabled(args: argparse.Namespace, artifact: Artifact) -> None:
    project_loaders = getattr(args, "_project_loaders", set())
    if not project_loaders or artifact.loader in project_loaders:
        return

    warned = getattr(args, "_warned_missing_loaders", set())
    if artifact.loader in warned:
        return

    print(
        f"Warning: Modrinth project loaders do not currently include '{artifact.loader}'. "
        "If this upload fails, add that loader in the project settings and rerun."
    )
    warned.add(artifact.loader)
    args._warned_missing_loaders = warned


def dependency_list(args: argparse.Namespace, loader: str) -> list[dict[str, str]]:
    dependencies: list[dict[str, str]] = []
    if loader == "fabric" and args.fabric_api_project:
        dependencies.append(
            {
                "project_id": args.fabric_api_project,
                "dependency_type": "required",
            }
        )

    for raw_dependency in args.dependency:
        parts = raw_dependency.split(":")
        if len(parts) not in (2, 3):
            raise ModrinthError(f"Invalid dependency format: {raw_dependency}")
        project_id, dependency_type = parts[0], parts[1]
        dependency_loader = parts[2] if len(parts) == 3 else None
        if dependency_loader and dependency_loader != loader:
            continue
        if dependency_type not in {"required", "optional", "incompatible", "embedded"}:
            raise ModrinthError(f"Invalid dependency type in {raw_dependency}")
        dependencies.append(
            {
                "project_id": project_id,
                "dependency_type": dependency_type,
            }
        )
    return dependencies


def format_metadata(
    args: argparse.Namespace,
    props: dict[str, str],
    artifact: Artifact,
    changelog: str,
) -> dict[str, Any]:
    minecraft_version = props["minecraft_version"]
    context = {
        "mod_version": props["mod_version"],
        "minecraft_version": minecraft_version,
        "loader": artifact.loader,
        "loader_display": artifact.loader_display,
        "module": artifact.module,
        "file_name": artifact.path.name,
    }
    version_number = args.number_template.format(**context) + args.version_suffix
    return {
        "name": args.name_template.format(**context),
        "version_number": version_number,
        "changelog": changelog,
        "dependencies": dependency_list(args, artifact.loader),
        "game_versions": args.game_version or [minecraft_version],
        "version_type": args.version_type,
        "loaders": [artifact.loader],
        "featured": bool(args.featured),
        "status": args.status,
        "project_id": args.project_id,
        "file_parts": ["file"],
        "primary_file": "file",
        "environment": artifact.environment,
    }


def request_json(
    method: str,
    url: str,
    headers: dict[str, str],
    body: bytes | None = None,
    ok_statuses: set[int] | None = None,
) -> tuple[int, Any]:
    if ok_statuses is None:
        ok_statuses = {200, 201, 204}
    request = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request) as response:
            raw = response.read()
            payload = json.loads(raw.decode("utf-8")) if raw else None
            return response.status, payload
    except urllib.error.HTTPError as error:
        raw = error.read()
        try:
            payload = json.loads(raw.decode("utf-8")) if raw else None
        except json.JSONDecodeError:
            payload = raw.decode("utf-8", errors="replace")
        if error.code in ok_statuses:
            return error.code, payload
        if error.code == 401:
            detail = payload.get("description", payload) if isinstance(payload, dict) else payload
            raise ModrinthError(
                "Modrinth rejected authorization (HTTP 401): "
                f"{detail}\n"
                "Check that MODRINTH_TOKEN is a Modrinth personal access token, "
                "has the VERSION_CREATE scope, and belongs to a user with permission "
                "to upload versions to MODRINTH_PROJECT_ID."
            ) from error
        raise ModrinthError(f"{method} {url} failed with HTTP {error.code}: {payload}") from error


def modrinth_headers(args: argparse.Namespace, authenticated: bool) -> dict[str, str]:
    headers = {
        "Accept": "application/json",
        "User-Agent": args.user_agent,
    }
    if authenticated and args.token:
        headers["Authorization"] = args.token
    return headers


def check_file_exists(args: argparse.Namespace, artifact: Artifact) -> bool:
    encoded_hash = urllib.parse.quote(artifact.sha512)
    url = f"{args.api_base.rstrip('/')}/version_file/{encoded_hash}?algorithm=sha512"
    status, payload = request_json(
        "GET",
        url,
        modrinth_headers(args, authenticated=True),
        ok_statuses={200, 404},
    )
    if status == 404:
        return False
    version_number = payload.get("version_number", "unknown") if isinstance(payload, dict) else "unknown"
    print(f"Skipping {artifact.path.name}: file hash already exists on Modrinth as {version_number}.")
    return True


def existing_version_numbers(args: argparse.Namespace) -> set[str]:
    encoded_project = urllib.parse.quote(args.project_id)
    url = f"{args.api_base.rstrip('/')}/project/{encoded_project}/version"
    status, payload = request_json(
        "GET",
        url,
        modrinth_headers(args, authenticated=True),
        ok_statuses={200, 404},
    )
    if status == 404:
        return set()
    if not isinstance(payload, list):
        return set()
    return {
        str(version.get("version_number"))
        for version in payload
        if isinstance(version, dict) and version.get("version_number")
    }


def should_skip_existing_version(
    args: argparse.Namespace,
    artifact: Artifact,
    version_number: str,
    existing_numbers: set[str],
) -> bool:
    if version_number not in existing_numbers:
        return False

    message = (
        f"Skipping {artifact.path.name}: version_number {version_number} already exists on Modrinth. "
        "Use --version-suffix if this is a different build that must be uploaded separately."
    )
    if args.fail_on_existing_version:
        raise ModrinthError(message)
    print(message)
    return True


def encode_multipart(data: dict[str, Any], file_path: Path) -> tuple[bytes, str]:
    boundary = f"----seeu-modrinth-{uuid.uuid4().hex}"
    lines: list[bytes] = []

    def add(value: str | bytes) -> None:
        if isinstance(value, str):
            lines.append(value.encode("utf-8"))
        else:
            lines.append(value)

    add(f"--{boundary}\r\n")
    add('Content-Disposition: form-data; name="data"\r\n')
    add("Content-Type: application/json; charset=utf-8\r\n\r\n")
    add(json.dumps(data, ensure_ascii=False, separators=(",", ":")))
    add("\r\n")

    mime_type = mimetypes.guess_type(file_path.name)[0] or "application/java-archive"
    add(f"--{boundary}\r\n")
    add(f'Content-Disposition: form-data; name="file"; filename="{file_path.name}"\r\n')
    add(f"Content-Type: {mime_type}\r\n\r\n")
    add(file_path.read_bytes())
    add("\r\n")
    add(f"--{boundary}--\r\n")

    return b"".join(lines), boundary


def publish_version(args: argparse.Namespace, data: dict[str, Any], artifact: Artifact) -> dict[str, Any]:
    body, boundary = encode_multipart(data, artifact.path)
    headers = modrinth_headers(args, authenticated=True)
    headers["Content-Type"] = f"multipart/form-data; boundary={boundary}"
    headers["Content-Length"] = str(len(body))
    url = f"{args.api_base.rstrip('/')}/version"
    _, payload = request_json("POST", url, headers, body=body)
    if not isinstance(payload, dict):
        raise ModrinthError(f"Unexpected Modrinth response for {artifact.path.name}: {payload}")
    return payload


def validate_args(args: argparse.Namespace) -> None:
    if not args.project_id:
        raise ModrinthError("Missing --project-id or MODRINTH_PROJECT_ID.")
    if not args.dry_run and not args.token:
        raise ModrinthError("Missing --token or MODRINTH_TOKEN.")
    if args.all_branches and not args.build:
        raise ModrinthError("--all-branches requires --build so stale jars from another branch cannot be uploaded.")
    if args.status == "draft":
        print(
            "Publishing as draft; draft versions may not be visible on the public project page. "
            "Use --status unlisted if you want a private review URL."
        )


def publish_current_branch(args: argparse.Namespace, existing_numbers: set[str]) -> None:
    props = read_gradle_properties()
    for key in ("mod_version", "minecraft_version"):
        if key not in props:
            raise ModrinthError(f"Missing {key} in gradle.properties.")

    branch = run_git(["branch", "--show-current"], fallback="unknown")
    print(f"\n=== Publishing branch {branch} for Minecraft {props['minecraft_version']} ===")

    if args.build:
        run_build(args, props)

    changelog = read_changelog(args, props)
    artifacts = discover_artifacts(args.only)

    for artifact in artifacts:
        data = format_metadata(args, props, artifact, changelog)
        print(f"\n{artifact.loader_display}: {artifact.path.name}")
        print(f"  version_number: {data['version_number']}")
        print(f"  sha512: {artifact.sha512}")

        if args.dry_run:
            print(json.dumps(data, indent=2, ensure_ascii=False))
            continue

        warn_if_loader_is_not_enabled(args, artifact)

        if not args.skip_existing_check and check_file_exists(args, artifact):
            continue

        if not args.skip_existing_check and should_skip_existing_version(
            args,
            artifact,
            data["version_number"],
            existing_numbers,
        ):
            continue

        response = publish_version(args, data, artifact)
        print(f"  published: {response.get('id')} ({response.get('version_number')})")
        existing_numbers.add(data["version_number"])


def publish_all_branches(args: argparse.Namespace, existing_numbers: set[str]) -> None:
    ensure_clean_worktree()
    branches = args.branches or list(DEFAULT_BRANCHES)
    original_branch = run_git(["branch", "--show-current"], fallback="")

    try:
        for branch in branches:
            switch_branch(branch)
            publish_current_branch(args, existing_numbers)
    finally:
        if original_branch and not args.keep_branch:
            switch_branch(original_branch)


def main() -> int:
    args = parse_args()
    try:
        validate_args(args)
        resolve_project_reference(args)
        existing_numbers = (
            set()
            if args.dry_run or args.skip_existing_check
            else existing_version_numbers(args)
        )
        if args.all_branches:
            publish_all_branches(args, existing_numbers)
        else:
            publish_current_branch(args, existing_numbers)

        return 0
    except (ModrinthError, subprocess.CalledProcessError, OSError, KeyError) as error:
        print(f"publish_modrinth.py: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
