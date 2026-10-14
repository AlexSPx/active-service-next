#!/usr/bin/env python3
"""Audit the pinned exercise source, reproducible fingerprints and separate review metadata."""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "src/main/resources/catalog/exercises.json"
MANIFEST = ROOT / "src/main/resources/catalog/legacy-fingerprints.json"
SOURCE_SHA256 = "5bb747e3fc658f095a60dcbf6d53c96627acdcc6ffb6fffde86f7e26995d40bf"
SOURCE_REVISION = "f00c92c7dcf1216a928a52c3706c7ce8e2f71ed5"
LICENSE_SHA256 = "6b0382b16279f26ff69014300541967a356a666eb0b91b422f6862f6b7dad17e"
EXPECTED_COUNT = 876

# Exact JSON-to-Exercise enum conversion from the @JsonCreator methods and enum names.
ENUM_FIELDS = {
    "level": {"beginner": "BEGINNER", "intermediate": "INTERMEDIATE", "expert": "EXPERT"},
    "force": {"static": "STATIC", "pull": "PULL", "push": "PUSH"},
    "mechanic": {"isolation": "ISOLATION", "compound": "COMPOUND"},
    "equipment": {
        "medicine ball": "MEDICINE_BALL", "dumbbell": "DUMBBELL", "body only": "BODY_ONLY",
        "bands": "BANDS", "kettlebells": "KETTLEBELLS", "foam roll": "FOAM_ROLL",
        "cable": "CABLE", "machine": "MACHINE", "barbell": "BARBELL",
        "exercise ball": "EXERCISE_BALL", "e-z curl bar": "E_Z_CURL_BAR", "other": "OTHER",
    },
    "category": {
        "powerlifting": "POWERLIFTING", "strength": "STRENGTH", "stretching": "STRETCHING",
        "cardio": "CARDIO", "olympic weightlifting": "OLYMPIC_WEIGHTLIFTING",
        "strongman": "STRONGMAN", "plyometrics": "PLYOMETRICS",
    },
}
STORED_FIELDS = (
    "name", "level", "force", "mechanic", "equipment", "primaryMuscles",
    "secondaryMuscles", "instructions", "category",
)
EXPECTED_SOURCE_FIELDS = {"id", *STORED_FIELDS, "images"}
LIST_FIELDS = ("primaryMuscles", "secondaryMuscles", "instructions")
NULLABLE_ENUM_FIELDS = ("force", "mechanic", "equipment")


class AuditError(Exception):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AuditError(message)


def fingerprint(record: dict[str, Any]) -> str:
    """Hash canonical stored values; arrays stay ordered and null remains JSON null."""
    values: dict[str, Any] = {"name": record["name"]}
    for field in ("level", "force", "mechanic", "equipment"):
        raw = record[field]
        if raw is None:
            require(field in NULLABLE_ENUM_FIELDS, f"{record['id']}: {field} cannot be null")
            values[field] = None
        else:
            try:
                values[field] = ENUM_FIELDS[field][raw.lower()]
            except (KeyError, AttributeError):
                raise AuditError(f"{record['id']}: unmapped {field} enum value {raw!r}")
    for field in LIST_FIELDS:
        values[field] = record[field]
    raw_category = record["category"]
    try:
        values["category"] = ENUM_FIELDS["category"][raw_category.lower()]
    except (KeyError, AttributeError):
        raise AuditError(f"{record['id']}: unmapped category enum value {raw_category!r}")
    encoded = json.dumps(values, ensure_ascii=False, separators=(",", ":"), sort_keys=True).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


def load_and_validate_catalog() -> tuple[list[dict[str, Any]], dict[str, str]]:
    raw = CATALOG.read_bytes()
    actual_sha = hashlib.sha256(raw).hexdigest()
    require(actual_sha == SOURCE_SHA256, f"catalog SHA-256 mismatch: expected {SOURCE_SHA256}, got {actual_sha}")
    records = json.loads(raw)
    require(isinstance(records, list), "catalog root must be an array")
    require(len(records) == EXPECTED_COUNT, f"expected {EXPECTED_COUNT} records, got {len(records)}")

    fingerprints: dict[str, str] = {}
    seen_fingerprints: dict[str, str] = {}
    for index, record in enumerate(records):
        require(isinstance(record, dict), f"record {index} must be an object")
        require(set(record) == EXPECTED_SOURCE_FIELDS,
                f"record {index} fields mismatch: missing={sorted(EXPECTED_SOURCE_FIELDS - set(record))}, "
                f"unexpected={sorted(set(record) - EXPECTED_SOURCE_FIELDS)}")
        key = record["id"]
        require(isinstance(key, str) and key, f"record {index} id must be a non-empty string")
        require(key not in fingerprints, f"duplicate source id: {key}")
        require(isinstance(record["name"], str) and record["name"], f"{key}: name must be non-empty text")
        for field in ("level", "category"):
            require(isinstance(record[field], str), f"{key}: {field} must be text")
        for field in NULLABLE_ENUM_FIELDS:
            require(record[field] is None or isinstance(record[field], str), f"{key}: {field} must be text or null")
        for field in LIST_FIELDS:
            require(isinstance(record[field], list), f"{key}: {field} must be an array (including empty arrays)")
            require(all(isinstance(value, str) for value in record[field]), f"{key}: {field} must contain only text")
        require(isinstance(record["images"], list) and all(isinstance(value, str) for value in record["images"]),
                f"{key}: ignored images field must be a string array")
        digest = fingerprint(record)
        if digest in seen_fingerprints:
            raise AuditError(f"stored-field fingerprint collision: {key} and {seen_fingerprints[digest]} ({digest})")
        seen_fingerprints[digest] = key
        fingerprints[key] = digest
    return records, fingerprints


def candidate_manifest(fingerprints: dict[str, str]) -> dict[str, Any]:
    return {
        "status": "proposed",
        "approval": None,
        "source": {
            "repository": "https://github.com/yuhonas/free-exercise-db",
            "revision": SOURCE_REVISION,
            "path": "dist/exercises.json",
            "sha256": SOURCE_SHA256,
        },
        "fingerprint": {
            "algorithm": "SHA-256",
            "canonicalization": "UTF-8 compact JSON object with lexicographically sorted keys; fields are the nine persisted Exercise fields only; enum values use their Java enum constant names; nulls remain null; arrays preserve source order.",
            "storedFields": list(STORED_FIELDS),
        },
        "entries": [
            {"sourceKey": key, "legacyFingerprint": fingerprints[key]}
            for key in sorted(fingerprints)
        ],
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--write-candidate", action="store_true", help="rewrite proposed manifest from pinned catalog")
    args = parser.parse_args()
    try:
        records, fingerprints = load_and_validate_catalog()
        manifest = candidate_manifest(fingerprints)
        if args.write_candidate:
            MANIFEST.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        stored = json.loads(MANIFEST.read_text(encoding="utf-8"))
        require({k: v for k, v in stored.items() if k not in ("status", "approval")} ==
                {k: v for k, v in manifest.items() if k not in ("status", "approval")}, "manifest differs from reproducible candidate; run with --write-candidate and review the diff")
        require(stored.get("status") in ("proposed", "reviewed"), "invalid manifest status")
        if stored["status"] == "proposed":
            require(stored.get("approval") is None, "proposed manifest must clear approval")
        else:
            approval = stored.get("approval")
            require(isinstance(approval, dict) and set(approval) == {"reviewer", "scope", "evidence"},
                    "reviewed manifest requires separate review metadata")
            require(approval["reviewer"] == "Codex parent orchestrator" and
                    all(isinstance(v, str) and v.strip() for v in approval.values()), "invalid review metadata")
        license_sha = hashlib.sha256((ROOT / "src/main/resources/catalog/LICENSE.md").read_bytes()).hexdigest()
        require(license_sha == LICENSE_SHA256, f"LICENSE.md SHA-256 mismatch: expected {LICENSE_SHA256}, got {license_sha}")
        print(f"PASS catalog_sha256={SOURCE_SHA256}")
        print(f"PASS source_revision={SOURCE_REVISION} records={len(records)} unique_source_keys={len(fingerprints)}")
        print(f"PASS stored_fields={len(STORED_FIELDS)} unique_fingerprints={len(set(fingerprints.values()))}")
        print("INFO null_optional_enums=" + ",".join(
            f"{field}:{sum(record[field] is None for record in records)}" for field in NULLABLE_ENUM_FIELDS))
        print("INFO empty_arrays=" + ",".join(
            f"{field}:{sum(not record[field] for record in records)}" for field in LIST_FIELDS))
        print(f"INFO duplicate_names={len(records) - len({record['name'] for record in records})}")
        print(f"PASS manifest_status={stored['status']} entries={len(stored['entries'])} exact_1_to_1_coverage=true reproducible=true")
        print(f"PASS license_sha256={license_sha}")
        print("NOTE runtime binding requires exactly one unmapped UUID row per reviewed fingerprint; ambiguous or uncertain legacy matches remain untouched and are reported; truly new keys may be inserted.")
    except (AuditError, OSError, json.JSONDecodeError) as error:
        print(f"FAIL {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
