# Exercise catalog endpoint

`GET /api/exercises/catalog` returns a complete snapshot of the persisted exercise catalog for an authenticated user. Authentication follows the existing exercise endpoints. Missing or invalid authentication returns HTTP `401`; a successful request returns HTTP `200` with a JSON object.

The endpoint includes every persisted row, including retired exercises and unresolved legacy UUIDs. It has no pagination or search filters. Query parameters such as `name`, `category`, `page`, and `size` do not restrict the response. An empty database returns an empty `items` array and incomplete mapping readiness.

## Response fields

| Field | Type | Meaning |
| --- | --- | --- |
| `catalogVersion` | string | Lowercase SHA-256 of the canonical persisted `items` array. |
| `sourceDatasetVersion` | string | Lowercase SHA-256 of the embedded pinned source JSON bytes. Independent of persisted rows. |
| `mappingComplete` | boolean | Whether every pinned source key has an active persisted mapping and no unresolved legacy UUID remains. |
| `unresolvedSourceKeys` | string array | Pinned source keys absent from persisted rows, sorted lexicographically. |
| `retiredSourceKeys` | string array | Pinned source keys whose persisted row is retired, sorted lexicographically. |
| `unresolvedLegacyIds` | UUID string array | IDs of all persisted rows with a null `sourceKey`, sorted by UUID string. |
| `items` | object array | All persisted exercises, sorted by UUID string. |

Each item has the following fields, including explicit JSON nulls for nullable values:

| Field | Type | Meaning |
| --- | --- | --- |
| `id` | UUID string | Persisted UUID used by workout and history references. |
| `sourceKey` | string or null | Source dataset identity, separate from the UUID. Null identifies an unresolved legacy row. |
| `retired` | boolean | Persisted retirement flag. |
| `name` | string | Persisted exercise name. |
| `level` | string or null | Java enum constant such as `BEGINNER`. |
| `force` | string or null | Java enum constant such as `PUSH`. |
| `mechanic` | string or null | Java enum constant such as `COMPOUND`. |
| `equipment` | string or null | Java enum constant such as `BARBELL`. |
| `primaryMuscles` | string array or null | Persisted ordered muscle values. |
| `secondaryMuscles` | string array or null | Persisted ordered muscle values. |
| `instructions` | string array or null | Persisted ordered instruction values. |
| `category` | string or null | Java enum constant such as `STRENGTH`. |

## Version contract

The canonical representation is compact UTF-8 JSON of `items`, sorted by the lowercase UUID string. Item properties use this fixed order:

```text
id, sourceKey, retired, name, level, force, mechanic, equipment,
primaryMuscles, secondaryMuscles, instructions, category
```

Array order and nulls remain unchanged. Null arrays and empty arrays have different versions. Changes to UUIDs, source keys, retirement, any of the nine metadata fields, or collection membership change `catalogVersion`. Repository result order does not change the version. Readiness fields and `sourceDatasetVersion` are outside this hash.

`sourceDatasetVersion` identifies the bundled source described in [catalog provenance](exercise-catalog-provenance.md). It does not assert that the database matches that source. Persisted metadata changes can change `catalogVersion` while `sourceDatasetVersion` remains fixed.

## Mapping readiness and read behavior

`mappingComplete` is true exactly when `unresolvedSourceKeys`, `retiredSourceKeys`, and `unresolvedLegacyIds` are all empty. A retired pinned key appears in `retiredSourceKeys`, not in `unresolvedSourceKeys`. Every null source key blocks readiness, including on a retired row. Extra mapped keys outside the pinned source remain in `items` and do not block readiness, even when retired.

The service reads all exercises once in a read-only transaction. Items, version, and readiness derive from that same query result. The GET does not invoke the importer, assign UUIDs, bind source keys, update metadata, or change retirement flags.

`GET /api/exercises/search` retains its existing filters and JSON array response. Search still exposes UUID `id` values and the existing metadata fields, with `sourceKey` and `retired` hidden. The catalog endpoint uses a separate response DTO.
