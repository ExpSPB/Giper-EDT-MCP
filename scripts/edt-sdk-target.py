#!/usr/bin/env python3
"""Snapshot an installed EDT 2025.2.5 SDK as local p2; verify the unit-test SDK.

Uses installed p2 metadata and copies binaries. Does not launch Eclipse/EDT,
download software, change the installation, or generate replacement SDK APIs.
"""

import argparse
import copy
import gzip
import hashlib
import json
from pathlib import Path
import re
import shutil
import sys
from urllib.parse import unquote
import xml.etree.ElementTree as ET
import zipfile


CORE = "com._1c.g5.v8.dt.core"
CORE_VERSION = "26.0.1.v202604021910"
SNAPSHOT_FORMAT_VERSION = 3
EXCLUDED = ("fm.giper.edt.mcp", "com.ditrix.edt.mcp", "com.e1c.edt.ai")
# Supplemental unit-harness libraries. Mockito/Byte Buddy/Objenesis support
# mocks; Tycho supplies the JUnit booter. Eclipse SDK 2023-12 supplies JDT UI,
# whose Require-Bundle chain needs manipulation -> launching -> JDT debug.
# Production source/manifest imports none of these package namespaces. The
# installed-ID check takes precedence: supplementation cannot replace an SDK ID.
SUPPLEMENTAL = {
    ("net.bytebuddy.byte-buddy", "1.9.0.v20181107-1410"),
    ("net.bytebuddy.byte-buddy-agent", "1.9.0.v20181106-1534"),
    ("org.mockito", "2.23.0.v20200310-1642"),
    ("org.objenesis", "2.6.0.v20180420-1519"),
    ("org.eclipse.tycho.surefire.junit4", "4.0.5"),
    ("org.eclipse.tycho.surefire.osgibooter", "4.0.5"),
    ("org.eclipse.jdt.core.manipulation", "1.20.0.v20231115-2128"),
    ("org.eclipse.jdt.launching", "3.21.0.v20231103-0759"),
    ("org.eclipse.jdt.ui", "3.31.0.v20231115-2128"),
    ("org.eclipse.jdt.debug", "3.21.200.v20231103-0755"),
}


def digest(path):
    if path.is_file():
        with path.open("rb") as stream:
            result = hashlib.sha256()
            for block in iter(lambda: stream.read(1024 * 1024), b""):
                result.update(block)
            return result.hexdigest()
    result = hashlib.sha256()
    for item in sorted(path.rglob("*")):
        if item.is_file():
            result.update(item.relative_to(path).as_posix().encode("utf-8") + b"\0")
            result.update(bytes.fromhex(digest(item)))
    return result.hexdigest()


def archive_contents_digest(path):
    result = hashlib.sha256()
    with zipfile.ZipFile(path) as archive:
        for name in sorted(archive.namelist(), key=Path):
            if not name.endswith("/"):
                result.update(name.encode("utf-8") + b"\0")
                result.update(hashlib.sha256(archive.read(name)).digest())
    return result.hexdigest()


def write_xml(path, element):
    ET.indent(element)
    ET.ElementTree(element).write(path, encoding="utf-8", xml_declaration=True)


def file_path(value):
    value = unquote(value.removeprefix("file:"))
    # Standard file:/// URLs and Eclipse's file:/ / native file:C:/ forms.
    if value.startswith("///"):
        value = value[2:]
    if re.match(r"^/[A-Za-z]:/", value):
        value = value[1:]
    return Path(value)


def bundle_version(value):
    # OSGi Version treats omitted minor/micro components as zero. Manifests
    # commonly say 1.77 while bundles.info and p2 artifact names say 1.77.0.
    match = re.fullmatch(r"(\d+)(?:\.(\d+))?(?:\.(\d+))?(?:\.([A-Za-z0-9_-]+))?", value)
    if not match:
        raise ValueError(f"Invalid OSGi bundle version: {value}")
    result = ".".join(str(int(part or "0")) for part in match.groups()[:3])
    return result + ("." + match.group(4) if match.group(4) else "")


def properties(parent, values):
    element = ET.SubElement(parent, "properties", size=str(len(values)))
    for name, value in values.items():
        ET.SubElement(element, "property", name=name, value=str(value))


def installed_bundles(install):
    result = []
    info = install / "configuration/org.eclipse.equinox.simpleconfigurator/bundles.info"
    for line in info.read_text(encoding="utf-8-sig").splitlines():
        if line.startswith("#") or not line.strip():
            continue
        name, version, location, *_ = line.split(",")
        if name.startswith(EXCLUDED):
            continue
        path = (install / location).resolve()
        if not path.is_relative_to(install) or not path.exists():
            raise ValueError(f"Invalid installed bundle location: {name}: {location}")
        result.append({"id": name, "version": version, "location": location,
                       "sha256": digest(path)})
    if {b["version"] for b in result if b["id"] == CORE} != {CORE_VERSION}:
        raise ValueError(f"Expected exact EDT 2025.2.5 core {CORE_VERSION}")
    return sorted(result, key=lambda b: (b["id"], b["version"]))


def snapshot(install, output, template):
    install, output = install.resolve(), output.resolve()
    if output == install or output.is_relative_to(install):
        raise ValueError("Snapshot output must be outside the EDT installation")
    bundles = installed_bundles(install)
    active = {(b["id"], b["version"]) for b in bundles}
    profiles = []
    registry = install / "p2/org.eclipse.equinox.p2.engine/profileRegistry"
    for path in registry.glob("*/*.profile.gz"):
        root = ET.fromstring(gzip.decompress(path.read_bytes()))
        units = root.findall("./units/unit")
        keys = {(u.get("id"), u.get("version")) for u in units}
        if active.issubset(keys):
            profiles.append((int(root.get("timestamp", "0")), path, units))
    if not profiles:
        raise ValueError("No retained p2 profile contains every active SDK bundle")
    _, profile, units = max(profiles, key=lambda item: item[0])
    profile_sha, template_sha = digest(profile), digest(template)
    fingerprint = hashlib.sha256(json.dumps({"formatVersion": SNAPSHOT_FORMAT_VERSION, "bundles": bundles,
        "profileSha256": profile_sha, "templateSha256": template_sha}, sort_keys=True).encode()).hexdigest()
    output = output / fingerprint[:16]
    inventory_path = output / "sdk-inventory.json"
    if inventory_path.exists():
        old = json.loads(inventory_path.read_text(encoding="utf-8"))
        if old["bundles"] != bundles or old["profileSha256"] != profile_sha:
            raise ValueError("Existing SDK snapshot differs; preserve it and choose a new output")
        for artifact in old["artifacts"]:
            if digest(output / artifact["path"]) != artifact["sha256"]:
                raise ValueError(f"SDK snapshot checksum mismatch: {artifact['path']}")
        for name, checksum in old["metadataSha256"].items():
            if digest(output / name) != checksum:
                raise ValueError(f"SDK snapshot metadata checksum mismatch: {name}")
        return output / "installed.target"
    output.mkdir(parents=True, exist_ok=True)
    repository = ET.Element("repository", name="Installed EDT 2025.2.5 SDK",
        type="org.eclipse.equinox.internal.p2.metadata.repository.LocalMetadataRepository",
        version="1.0.0")
    properties(repository, {"p2.compressed": "false"})
    retained = [u for u in units if not u.get("id", "").startswith(EXCLUDED)
                and not any(a.get("classifier") == "binary"
                            for a in u.findall("./artifacts/artifact"))]
    collection = ET.SubElement(repository, "units", size=str(len(retained)))
    artifacts = ET.Element("repository", name="Installed EDT 2025.2.5 artifacts",
        type="org.eclipse.equinox.p2.artifact.repository.simpleRepository", version="1")
    properties(artifacts, {"p2.compressed": "false"})
    mappings = ET.SubElement(artifacts, "mappings", size="2")
    for classifier, folder in (("osgi.bundle", "plugins"),
                               ("org.eclipse.update.feature", "features")):
        ET.SubElement(mappings, "rule", filter=f"(classifier={classifier})",
            output=f"${{repoUrl}}/{folder}/${{id}}_${{version}}.jar")
    artifact_collection = ET.SubElement(artifacts, "artifacts")
    copied, seen = [], set()
    for unit in retained:
        collection.append(copy.deepcopy(unit))
        for artifact in unit.findall("./artifacts/artifact"):
            key = tuple(artifact.get(k) for k in ("classifier", "id", "version"))
            if key in seen:
                continue
            seen.add(key)
            classifier, name, version = key
            folder = {"osgi.bundle": "plugins", "org.eclipse.update.feature": "features"}[classifier]
            base = install / folder / f"{name}_{version}"
            source = Path(str(base) + ".jar") if Path(str(base) + ".jar").is_file() else base
            destination = output / folder / f"{name}_{version}.jar"
            destination.parent.mkdir(exist_ok=True)
            if source.is_dir():
                with zipfile.ZipFile(destination, "w", compression=zipfile.ZIP_DEFLATED) as archive:
                    for item in sorted(source.rglob("*")):
                        if item.is_file():
                            archive.write(item, item.relative_to(source).as_posix())
            elif source.is_file():
                shutil.copyfile(source, destination)
            else:
                raise ValueError(f"Installed p2 artifact missing: {source}")
            checksum = digest(destination)
            record = ET.SubElement(artifact_collection, "artifact", classifier=classifier,
                                   id=name, version=version)
            properties(record, {"artifact.size": destination.stat().st_size,
                                "download.size": destination.stat().st_size,
                                "download.checksum.sha-256": checksum})
            copied.append({"path": destination.relative_to(output).as_posix(),
                           "sha256": checksum})
    artifact_collection.set("size", str(len(copied)))
    write_xml(output / "content.xml", repository)
    write_xml(output / "artifacts.xml", artifacts)
    target = ET.parse(template).getroot()
    target.set("name", "Exact installed EDT 2025.2.5")
    locations = target.find("locations")
    for location in list(locations):
        if any("edt.1c.ru/" in repo.get("location", "")
               for repo in location.findall("repository")):
            locations.remove(location)
        else:
            # Tycho requires target-resolution flags to agree across
            # all p2 locations, including supplemental test-library repositories.
            location.set("includeAllPlatforms", "false")
            location.set("includeMode", "slicer")
            location.set("includeSource", "false")
    location = ET.Element("location", type="InstallableUnit", includeMode="slicer",
                         includeAllPlatforms="false", includeConfigurePhase="false",
                         includeSource="false", followRepositoryReferences="false")
    ET.SubElement(location, "repository", location=output.as_uri() + "/")
    for name, version in sorted(active):
        ET.SubElement(location, "unit", id=name, version=version)
    locations.insert(0, location)
    write_xml(output / "installed.target", target)
    inventory = {"formatVersion": SNAPSHOT_FORMAT_VERSION, "baseline": "2025.2.5", "core": CORE_VERSION,
                 "install": str(install), "profile": str(profile),
                 "profileSha256": profile_sha, "templateSha256": template_sha,
                 "bundles": bundles, "artifacts": copied,
                 "metadataSha256": {name: digest(output / name) for name in
                     ("content.xml", "artifacts.xml", "installed.target")}}
    inventory_path.write_text(json.dumps(inventory, indent=2) + "\n", encoding="utf-8")
    print(f"Snapshot: {len(bundles)} active bundles, {len(copied)} artifacts, core {CORE_VERSION}",
          file=sys.stderr)
    return output / "installed.target"


def verify(inventory_path, reports):
    inventory = json.loads(inventory_path.read_text(encoding="utf-8"))
    allowed = {(b["id"], b["version"]) for b in inventory["bundles"]}
    if {version for name, version in allowed if name == CORE} != {CORE_VERSION}:
        raise ValueError(f"Inventory must contain exact EDT 2025.2.5 core {CORE_VERSION}")
    installed_ids = {name for name, version in allowed}
    files = sorted(reports.glob("TEST-*.xml"))
    if not files:
        raise ValueError("No canonical Surefire reports found")
    references = {}
    for path in files:
        props = {p.get("name"): p.get("value", "") for p in
                 ET.parse(path).getroot().findall("./properties/property")}
        if not props.get("osgi.bundles", "").strip():
            raise ValueError(f"Canonical report has no resolved OSGi bundle inventory: {path.name}")
        bases = [props.get(k, "") for k in ("osgi.install.area", "osgi.configuration.area", "user.dir")]
        for reference in props["osgi.bundles"].split(","):
            references.setdefault(reference, set()).update(bases)
    sdk, supplemental = set(), set()
    expected_hashes = {a["path"]: a["sha256"] for a in inventory["artifacts"]}
    for original_reference, bases in sorted(references.items()):
        reference = original_reference
        reference = reference.removeprefix("reference:")
        if not reference.startswith("file:"):
            raise ValueError(f"Unsupported resolved OSGi bundle reference: {original_reference}")
        path = file_path(re.sub(r"@\d+(?::start)?$", "", reference))
        if not path.is_absolute():
            candidates = []
            for base in bases:
                if base:
                    candidates.append(file_path(base) / path)
            path = next((candidate for candidate in candidates if candidate.exists()), path)
        if not path.exists():
            raise ValueError(f"Cannot inspect resolved OSGi bundle: {original_reference}")
        manifest_path = path / "META-INF/MANIFEST.MF"
        if path.is_file() and zipfile.is_zipfile(path):
            with zipfile.ZipFile(path) as archive:
                manifest = archive.read("META-INF/MANIFEST.MF").decode("utf-8")
        elif manifest_path.is_file():
            manifest = manifest_path.read_text(encoding="utf-8")
        else:
            raise ValueError(f"Resolved OSGi bundle has no manifest: {original_reference}")
        manifest = manifest.replace("\r\n", "\n").replace("\n ", "")
        name_match = re.search(r"^Bundle-SymbolicName: ([^;\n]+)", manifest, re.M)
        version_match = re.search(r"^Bundle-Version: ([^\n]+)", manifest, re.M)
        if not name_match or not version_match:
            raise ValueError(f"Resolved OSGi bundle manifest has no identity: {original_reference}")
        name, version = name_match.group(1), bundle_version(version_match.group(1))
        if name.startswith(EXCLUDED):
            continue
        if name not in installed_ids:
            if (name, version) not in SUPPLEMENTAL:
                raise ValueError(f"Unapproved supplemental runtime bundle: {name} {version}")
            supplemental.add((name, version))
            continue
        sdk.add((name, version))
        if (name, version) in allowed:
            filename = f"{name}_{version}.jar"
            if path.is_file():
                matches = digest(path) == expected_hashes.get("plugins/" + filename)
            else:
                # Tycho expands a few bundles into work/plugins. Compare every
                # filename and byte content against the immutable archive.
                matches = digest(path) == archive_contents_digest(inventory_path.parent / "plugins" / filename)
            if not matches:
                raise ValueError(f"Resolved SDK binary checksum differs: {name} {version}")
    if (CORE, CORE_VERSION) not in sdk or sdk - allowed:
        mismatch = sorted(sdk - allowed)
        raise ValueError(f"Unit runtime differs from exact installed SDK: {len(mismatch)} mismatches; "
                         f"sample {mismatch[:5]}; expected core {CORE_VERSION}")
    duplicate_ids = {name: sorted(version for bundle, version in sdk if bundle == name)
                     for name in installed_ids if sum(bundle == name for bundle, version in sdk) > 1}
    result = {"baseline": inventory["baseline"], "core": CORE_VERSION,
              "verifiedReports": len(files), "resolvedSdkBundles": sorted(sdk),
              "supplementalTestBundles": sorted(supplemental),
              "multipleInstalledVersions": duplicate_ids}
    (reports / "edt-sdk-identity.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(f"Verified exact .5 unit runtime: {len(files)} reports, {len(sdk)} SDK bundles")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    prepare = commands.add_parser("snapshot")
    prepare.add_argument("--install", type=Path, required=True)
    prepare.add_argument("--output", type=Path, required=True)
    prepare.add_argument("--template", type=Path, required=True)
    check = commands.add_parser("verify")
    check.add_argument("--inventory", type=Path, required=True)
    check.add_argument("--reports", type=Path, required=True)
    args = parser.parse_args()
    try:
        if args.command == "snapshot":
            print(snapshot(args.install, args.output, args.template))
        else:
            verify(args.inventory, args.reports)
    except (ValueError, OSError, ET.ParseError, KeyError) as error:
        parser.exit(1, f"SDK target error: {error}\n")


if __name__ == "__main__":
    main()
