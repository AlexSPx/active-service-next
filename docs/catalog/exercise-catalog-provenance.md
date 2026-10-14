# Exercise catalog and legacy fingerprint review

## Pinned source

- Repository: <https://github.com/yuhonas/free-exercise-db>
- Revision: `f00c92c7dcf1216a928a52c3706c7ce8e2f71ed5`
- File: `dist/exercises.json`
- SHA-256: `5bb747e3fc658f095a60dcbf6d53c96627acdcc6ffb6fffde86f7e26995d40bf`
- License: Unlicense, copied as `src/main/resources/catalog/LICENSE.md` (SHA-256 `6b0382b16279f26ff69014300541967a356a666eb0b91b422f6862f6b7dad17e`).

The pinned JSON is embedded in the production classpath at `catalog/exercises.json`. Its upstream `images` property is retained in the source snapshot, but the current `Exercise` deserializer ignores it. The upstream string `id` is consumed by `Exercise.setJsonId` and intentionally discarded; the legacy loader assigns a random UUID instead.

## Stored-field contract

The values used for a fingerprint are precisely the nine fields in the `exercises` table and `Exercise` entity:

| Source JSON field | Stored field | Conversion |
| --- | --- | --- |
| `name` | `name` | unchanged text |
| `level` | `level` | source lowercase enum name to Java enum constant (`beginner` → `BEGINNER`) |
| `force` | `force` | `static/pull/push` to `STATIC/PULL/PUSH`; null remains null |
| `mechanic` | `mechanic` | `isolation/compound` to `ISOLATION/COMPOUND`; null remains null |
| `equipment` | `equipment` | known source label to its Java enum constant; null remains null |
| `primaryMuscles` | `primary_muscles` | ordered text array, unchanged |
| `secondaryMuscles` | `secondary_muscles` | ordered text array, unchanged |
| `instructions` | `instructions` | ordered text array, unchanged |
| `category` | `category` | source label to Java enum constant, including `olympic weightlifting` → `OLYMPIC_WEIGHTLIFTING` |

`V1__init.sql` requires `name` NOT NULL; the other eight stored columns permit null. `ExerciseLoaderConfig` has no null-to-empty normalization. The source snapshot has all three array properties present as arrays; the three optional enum properties can be null. Do not coalesce null arrays to empty arrays in a matcher: that would invent an equality the importer did not apply. Instructions and muscle arrays are ordered; do not sort, case-fold, trim, tokenize, or otherwise normalize values. Enum mapping follows each enum's `@JsonCreator`; the pinned source uses only supported values. Unrecognized enum values would deserialize to null in the legacy loader, so the audit rejects them instead of silently broadening equality.

## Fingerprint manifest

`src/main/resources/catalog/legacy-fingerprints.json` is a generated manifest **reviewed by the Codex parent orchestrator**. The parent reviewed the script, all nine legacy fields, old loader, enum converters and V1 schema, and ran the audit successfully. This is scoped approval for exactly-one unmapped UUID fingerprint matching, with no claimed human sign-off. Each `sourceKey` is the upstream JSON `id`; each `legacyFingerprint` is SHA-256 over UTF-8 compact JSON containing only the nine persisted values after enum conversion. JSON object keys are lexicographically sorted; arrays retain order; null stays JSON null. Thus source id, UUID, `images`, and other non-persisted data do not affect a fingerprint.

The audit checks that all 876 source keys and all 876 fingerprints are unique and that manifest entries reproduce exactly from the pinned data. A runtime importer may use a fingerprint only when it identifies exactly one currently unmapped legacy UUID row. Multiple candidates and uncertain zero-candidate matches must be reported and left unchanged; truly new keys may be inserted; names are never a fallback. The manifest records reviewed fingerprints, not UUID assignments, because no deployment UUIDs were available during artifact generation.

## Reproduce the audit

Run from the repository root:

```sh
python3 scripts/audit_exercise_catalog.py
```

To intentionally regenerate the proposal after changing the pinned catalog or fingerprint rules:

```sh
python3 scripts/audit_exercise_catalog.py --write-candidate
```

The script uses only the Python standard library. Regeneration resets status to proposed and clears approval. The audit validates review metadata separately from reproducible source, fingerprint rules and entries. Review the catalog hash, stored-field rules, and manifest diff before restoring reviewed status.

## Importer handoff

`ExerciseCatalogImporter.importPinnedCatalog()` imports the embedded snapshot. `importCatalog(String json)` accepts a complete authoritative snapshot, validates it before DB interaction, and uses the same reviewed baseline manifest for legacy matching. Call the Spring bean so `@Transactional` applies. The read-only [catalog endpoint](exercise-catalog-endpoint.md) exposes persisted items and mapping readiness without invoking the importer.

The importer serializes calls across service instances with PostgreSQL transaction advisory lock `714876001`, then reads and synchronizes the catalog in that transaction. UUID primary keys and references remain intact. Known source keys update metadata in place; missing mapped keys retire; restored keys reactivate. Unmapped legacy rows never retire. Source keys and retirement flags are hidden from existing Exercise JSON; current search semantics are preserved, including access to retired history.

The returned `ImportReport` contains inserted, bound, updated, retired and reactivated counts, plus diagnostics with source keys, reason codes and candidate UUIDs. `updated` counts metadata changes to already mapped rows; a newly bound row is counted under `bound` even if its metadata also changes. `AMBIGUOUS_FINGERPRINT` defers duplicate exact candidates; `POSSIBLE_KEY_RENAME` defers exact metadata already associated with another source key; `LEGACY_METADATA_MISMATCH` defers same-name or unreviewed exact legacy candidates. `UNMATCHED_LEGACY` lists each remaining unmapped UUID, including candidates already identified by another diagnostic. These diagnostics do not abort safe imports. Startup logs the complete report. There is no persisted report/state table.

A source key rename requires explicit resolution before it can bind to an existing UUID. The old mapped key retires if omitted from the authoritative snapshot; it remains fetchable. Fingerprints preserve null arrays and array order. Names help detect uncertainty only, never establish identity.

Focused unit tests cover synchronization, prevalidation, diagnostics, UUID JSON compatibility and all 876 cross-language fingerprints. They use repository/EntityManager mocks and do not prove real transaction rollback, advisory-lock concurrency, or Flyway behavior. Those require the separate isolated PostgreSQL acceptance run. V4 must be accepted only after stacking this branch on ALE-68 PR 29, which owns V3. No database or live server was accessed for this slice.
