"""Exercise SDK provenance boundaries with tiny archives, without EDT or Maven."""

import contextlib
import gzip
import importlib.util
import io
import json
from pathlib import Path
import shutil
import tempfile
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET
import zipfile


ROOT = Path(__file__).resolve().parents[3]
SPEC = importlib.util.spec_from_file_location("edt_sdk_target", ROOT / "scripts/edt-sdk-target.py")
SDK = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(SDK)
ECLIPSE = "org.eclipse.core.runtime"
ECLIPSE_VERSION = "3.30.0.v20231102-0719"


def tiny_bundle(path, name, version, payload=b"original", folded=False):
    """Only a manifest and arbitrary bytes: these are parser fixtures, not SDK APIs."""
    path.parent.mkdir(parents=True, exist_ok=True)
    symbolic_name = name
    if folded:
        symbolic_name = name[:12] + "\r\n " + name[12:]
    manifest = ("Manifest-Version: 1.0\r\nBundle-SymbolicName: " + symbolic_name
                + ";singleton:=true\r\nBundle-Version: " + version + "\r\n\r\n")
    with zipfile.ZipFile(path, "w") as archive:
        archive.writestr("META-INF/MANIFEST.MF", manifest)
        archive.writestr("payload.bin", payload)
    return path


class SdkVerifierTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.snapshot = self.root / "immutable"
        self.runtime = self.root / "runtime with spaces"
        self.reports = self.root / "reports"
        self.reports.mkdir()
        self.inventory_path = self.snapshot / "sdk-inventory.json"
        self.inventory = {"baseline": "2025.2.5", "core": SDK.CORE_VERSION,
                          "bundles": [], "artifacts": []}
        self.core = self.installed(SDK.CORE, SDK.CORE_VERSION)
        self.eclipse = self.installed(ECLIPSE, ECLIPSE_VERSION)
        self.save_inventory()

    def installed(self, name, version, folded=False):
        relative = "plugins/" + name + "_" + version + ".jar"
        archive = tiny_bundle(self.snapshot / relative, name, version, folded=folded)
        runtime = self.runtime / archive.name
        runtime.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(archive, runtime)
        self.inventory["bundles"].append({"id": name, "version": version,
                                           "sha256": SDK.digest(archive)})
        self.inventory["artifacts"].append({"path": relative, "sha256": SDK.digest(archive)})
        return runtime

    def save_inventory(self):
        self.inventory_path.write_text(json.dumps(self.inventory), encoding="utf-8")

    def report(self, references=None, properties=None, name="TEST-sdk.xml"):
        root = ET.Element("testsuite")
        props = ET.SubElement(root, "properties")
        values = properties if properties is not None else {
            "osgi.bundles": ",".join(references or [self.core.as_uri(), self.eclipse.as_uri()])}
        for key, value in values.items():
            ET.SubElement(props, "property", name=key, value=value)
        ET.ElementTree(root).write(self.reports / name, encoding="utf-8")

    def verify(self):
        with contextlib.redirect_stdout(io.StringIO()):
            SDK.verify(self.inventory_path, self.reports)
        return json.loads((self.reports / "edt-sdk-identity.json").read_text(encoding="utf-8"))

    def test_verifies_real_bytes_and_records_both_core_and_eclipse(self):
        self.report()
        result = self.verify()
        self.assertEqual("2025.2.5", result["baseline"])
        self.assertEqual([[SDK.CORE, SDK.CORE_VERSION], [ECLIPSE, ECLIPSE_VERSION]],
                         result["resolvedSdkBundles"])

    def test_no_reports_and_missing_or_empty_bundle_inventory_fail(self):
        with self.assertRaisesRegex(ValueError, "No canonical Surefire"):
            self.verify()
        for value in ({}, {"osgi.bundles": ""}, {"osgi.bundles": "  "}):
            with self.subTest(properties=value):
                self.report(properties=value)
                with self.assertRaisesRegex(ValueError, "no resolved OSGi bundle inventory"):
                    self.verify()

    def test_each_report_must_supply_inventory(self):
        self.report()
        self.report(properties={}, name="TEST-empty.xml")
        with self.assertRaisesRegex(ValueError, "TEST-empty.xml"):
            self.verify()

    def test_inventory_itself_demands_only_exact_core(self):
        for bundles in ([], [{"id": SDK.CORE, "version": "26.0.1.v202605050943"}],
                        self.inventory["bundles"] + [{"id": SDK.CORE, "version": "99.0.0"}]):
            with self.subTest(bundles=bundles):
                self.inventory["bundles"] = bundles
                self.save_inventory()
                self.report()
                with self.assertRaisesRegex(ValueError, "Inventory must contain exact"):
                    self.verify()

    def test_resolved_core_is_mandatory_even_when_inventory_is_correct(self):
        self.report([self.eclipse.as_uri()])
        with self.assertRaisesRegex(ValueError, "expected core"):
            self.verify()

    def test_installed_eclipse_version_drift_is_rejected(self):
        tiny_bundle(self.eclipse, ECLIPSE, "3.99.0")
        self.report()
        with self.assertRaisesRegex(ValueError, "org.eclipse.core.runtime.*3.99.0"):
            self.verify()

    def test_manifest_identity_beats_filename(self):
        tiny_bundle(self.eclipse, "org.example.unapproved", ECLIPSE_VERSION)
        self.report()
        with self.assertRaisesRegex(ValueError, "Unapproved supplemental.*org.example.unapproved"):
            self.verify()

    def test_same_symbolic_name_and_version_with_wrong_bytes_is_rejected(self):
        tiny_bundle(self.eclipse, ECLIPSE, ECLIPSE_VERSION, payload=b"modified bytes")
        self.report()
        with self.assertRaisesRegex(ValueError, "binary checksum differs: org.eclipse.core.runtime"):
            self.verify()

    def test_feature_with_same_filename_cannot_overwrite_plugin_checksum(self):
        feature = tiny_bundle(self.snapshot / "features" / self.eclipse.name,
                              ECLIPSE, ECLIPSE_VERSION, payload=b"feature bytes")
        self.inventory["artifacts"].append({"path": "features/" + feature.name,
                                           "sha256": SDK.digest(feature)})
        self.save_inventory()
        self.report()
        self.assertIn([ECLIPSE, ECLIPSE_VERSION], self.verify()["resolvedSdkBundles"])

    def test_osgi_abbreviated_manifest_version_matches_canonical_inventory(self):
        name = "org.example.abbreviated"
        relative = "plugins/" + name + "_1.77.0.jar"
        archive = tiny_bundle(self.snapshot / relative, name, "1.77")
        runtime = self.runtime / archive.name
        shutil.copyfile(archive, runtime)
        self.inventory["bundles"].append({"id": name, "version": "1.77.0"})
        self.inventory["artifacts"].append({"path": relative, "sha256": SDK.digest(archive)})
        self.save_inventory()
        self.report([self.core.as_uri(), runtime.as_uri()])
        self.assertIn([name, "1.77.0"], self.verify()["resolvedSdkBundles"])
        tiny_bundle(runtime, name, "1.78")
        with self.assertRaisesRegex(ValueError, "org.example.abbreviated.*1.78.0"):
            self.verify()

    def test_percent_encoded_native_and_relative_locations(self):
        references = ["reference:" + self.core.as_uri() + "@1:start",
                      "reference:file:" + self.eclipse.as_posix() + "@4"]
        self.assertIn("%20", references[0])
        self.report(references)
        self.assertEqual(2, len(self.verify()["resolvedSdkBundles"]))
        for base in (self.runtime.as_uri() + "/", str(self.runtime)):
            with self.subTest(base=base):
                self.report(properties={"osgi.bundles": "reference:file:" + self.core.name + ",file:" + self.eclipse.name,
                                        "osgi.install.area": base})
                self.assertEqual(2, len(self.verify()["resolvedSdkBundles"]))

    def test_missing_relative_bundle_cannot_be_ignored(self):
        self.report([self.core.as_uri(), "reference:file:does-not-exist.jar"])
        with self.assertRaisesRegex(ValueError, "Cannot inspect resolved"):
            self.verify()

    def test_non_file_or_identityless_bundle_cannot_be_ignored(self):
        plain = self.runtime / "plain.txt"
        plain.write_text("not a manifest", encoding="utf-8")
        identityless = self.runtime / "identityless.jar"
        with zipfile.ZipFile(identityless, "w") as archive:
            archive.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n")
        for reference, message in (("http://example.invalid/bundle.jar", "Unsupported resolved"),
                                   (plain.as_uri(), "has no manifest"),
                                   (identityless.as_uri(), "has no identity")):
            with self.subTest(reference=reference):
                self.report([self.core.as_uri(), reference])
                with self.assertRaisesRegex(ValueError, message):
                    self.verify()

    def test_expanded_bundle_compares_all_content_and_folded_manifest(self):
        folded = self.installed("org.example.installed", "1.0.0", folded=True)
        self.save_inventory()
        expanded = self.runtime / "expanded"
        with zipfile.ZipFile(folded) as archive:
            archive.extractall(expanded)
        self.report([self.core.as_uri(), expanded.as_uri()])
        self.assertIn(["org.example.installed", "1.0.0"], self.verify()["resolvedSdkBundles"])
        (expanded / "payload.bin").write_bytes(b"changed")
        with self.assertRaisesRegex(ValueError, "binary checksum differs"):
            self.verify()

    def test_approved_supplemental_is_explicit_and_cannot_override_installed_id(self):
        name, version = "org.mockito", "2.23.0.v20200310-1642"
        mockito = tiny_bundle(self.runtime / "mockito.jar", name, version)
        self.report([self.core.as_uri(), mockito.as_uri()])
        self.assertEqual([[name, version]], self.verify()["supplementalTestBundles"])
        tiny_bundle(mockito, name, "99.0.0")
        with self.assertRaisesRegex(ValueError, "Unapproved supplemental"):
            self.verify()
        self.installed(name, "1.0.0")
        self.save_inventory()
        tiny_bundle(mockito, name, version)
        with self.assertRaisesRegex(ValueError, "runtime differs from exact installed SDK"):
            self.verify()

    def test_two_installed_versions_are_explicitly_reported_but_uninstalled_third_fails(self):
        first = self.installed("org.example.multi", "1.0.0")
        second = self.installed("org.example.multi", "2.0.0")
        self.save_inventory()
        self.report([self.core.as_uri(), first.as_uri(), second.as_uri()])
        self.assertEqual({"org.example.multi": ["1.0.0", "2.0.0"]},
                         self.verify()["multipleInstalledVersions"])
        tiny_bundle(second, "org.example.multi", "3.0.0")
        with self.assertRaisesRegex(ValueError, "org.example.multi.*3.0.0"):
            self.verify()


class SdkSnapshotTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.install = self.root / "install"
        self.output = self.root / "snapshots"
        self.template = self.root / "default.target"
        self.bundles = [(SDK.CORE, SDK.CORE_VERSION), (ECLIPSE, ECLIPSE_VERSION)]
        info = self.install / "configuration/org.eclipse.equinox.simpleconfigurator/bundles.info"
        info.parent.mkdir(parents=True)
        rows = []
        for name, version in self.bundles:
            location = "plugins/" + name + "_" + version + ".jar"
            tiny_bundle(self.install / location, name, version)
            rows.append(",".join((name, version, location, "4", "false")))
        info.write_text("\n".join(rows), encoding="utf-8")
        self.profile(10, self.bundles)
        self.template.write_text('''<target name="test"><locations>
            <location type="InstallableUnit" includeMode="planner" includeAllPlatforms="true" includeSource="true">
            <repository location="https://edt.1c.ru/2025.2"/><unit id="old" version="0.0.0"/></location>
            <location type="InstallableUnit" includeMode="planner" includeAllPlatforms="true" includeSource="true">
            <repository location="https://example.invalid/test-libraries"/><unit id="test" version="1.0.0"/>
            </location></locations></target>''', encoding="utf-8")

    def profile(self, timestamp, bundles):
        root = ET.Element("profile", timestamp=str(timestamp))
        units = ET.SubElement(root, "units")
        for name, version in bundles:
            unit = ET.SubElement(units, "unit", id=name, version=version)
            ET.SubElement(ET.SubElement(unit, "artifacts"), "artifact", classifier="osgi.bundle", id=name, version=version)
        path = self.install / "p2/org.eclipse.equinox.p2.engine/profileRegistry/sdk.profile" / (str(timestamp) + ".profile.gz")
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(gzip.compress(ET.tostring(root)))
        return path

    def snapshot(self):
        with contextlib.redirect_stderr(io.StringIO()):
            return SDK.snapshot(self.install, self.output, self.template)

    def test_newer_incomplete_profile_does_not_replace_full_active_inventory(self):
        self.profile(20, [self.bundles[0]])
        target = self.snapshot()
        inventory = json.loads((target.parent / "sdk-inventory.json").read_text(encoding="utf-8"))
        self.assertTrue(inventory["profile"].endswith("10.profile.gz"))
        locations = ET.parse(target).getroot().findall("locations/location")
        self.assertEqual(2, len(locations))
        for attribute, expected in (("includeMode", "slicer"), ("includeAllPlatforms", "false"), ("includeSource", "false")):
            self.assertEqual({expected}, {location.get(attribute) for location in locations})
        self.assertNotIn("edt.1c.ru", target.read_text(encoding="utf-8"))

    def test_existing_snapshot_metadata_tamper_is_refused_and_preserved(self):
        target = self.snapshot()
        for filename in ("content.xml", "artifacts.xml", "installed.target"):
            with self.subTest(filename=filename):
                path = target.parent / filename
                original = path.read_bytes()
                altered = original + b"\n<!-- altered -->"
                path.write_bytes(altered)
                with self.assertRaisesRegex(ValueError, "metadata checksum mismatch: " + filename):
                    self.snapshot()
                self.assertEqual(altered, path.read_bytes())
                path.write_bytes(original)

    def test_existing_snapshot_binary_tamper_is_refused(self):
        target = self.snapshot()
        path = target.parent / "plugins" / (SDK.CORE + "_" + SDK.CORE_VERSION + ".jar")
        path.write_bytes(b"changed archive")
        with self.assertRaisesRegex(ValueError, "SDK snapshot checksum mismatch"):
            self.snapshot()

    def test_generator_semantics_change_creates_new_snapshot_and_preserves_old(self):
        first = self.snapshot()
        before = first.read_bytes()
        with patch.object(SDK, "SNAPSHOT_FORMAT_VERSION", SDK.SNAPSHOT_FORMAT_VERSION + 1):
            second = self.snapshot()
        self.assertNotEqual(first.parent, second.parent)
        self.assertEqual(before, first.read_bytes())
        self.assertTrue(second.exists())

    def test_installed_core_wrong_version_or_missing_is_refused(self):
        info = self.install / "configuration/org.eclipse.equinox.simpleconfigurator/bundles.info"
        for rows in ("", ECLIPSE + "," + ECLIPSE_VERSION + ",plugins/" + ECLIPSE + "_" + ECLIPSE_VERSION + ".jar,4,false"):
            info.write_text(rows, encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "Expected exact EDT 2025.2.5 core"):
                self.snapshot()
        newer = "26.0.1.v202605050943"
        location = "plugins/" + SDK.CORE + "_" + newer + ".jar"
        tiny_bundle(self.install / location, SDK.CORE, newer)
        info.write_text(SDK.CORE + "," + newer + "," + location + ",4,false", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "Expected exact EDT 2025.2.5 core"):
            self.snapshot()


if __name__ == "__main__":
    unittest.main()
