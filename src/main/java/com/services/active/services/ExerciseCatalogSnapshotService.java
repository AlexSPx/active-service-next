package com.services.active.services;

import com.services.active.dto.ExerciseCatalogSnapshot;
import com.services.active.dto.ExerciseCatalogSnapshot.Item;
import com.services.active.repository.ExerciseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ExerciseCatalogSnapshotService {
    private final ExerciseRepository repository;
    private final Set<String> pinnedKeys;
    private final String sourceDatasetVersion;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public ExerciseCatalogSnapshotService(ExerciseRepository repository, ExerciseCatalog catalog) {
        this.repository = repository;
        pinnedKeys = Set.copyOf(catalog.parse(catalog.pinnedJson()).keySet());
        sourceDatasetVersion = hash(catalog.pinnedJson().getBytes(StandardCharsets.UTF_8));
    }

    @Transactional(readOnly = true)
    public ExerciseCatalogSnapshot snapshot() {
        // One read: items, version and readiness all derive from the same database statement snapshot.
        var items = repository.findAll().stream().map(Item::from)
                .sorted(Comparator.comparing(item -> item.id().toString())).toList();
        var mappedKeys = items.stream().map(Item::sourceKey).filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        var unresolvedKeys = pinnedKeys.stream().filter(key -> !mappedKeys.contains(key)).sorted().toList();
        var retiredKeys = items.stream().filter(Item::retired).map(Item::sourceKey)
                .filter(java.util.Objects::nonNull).filter(pinnedKeys::contains).sorted().toList();
        var legacyIds = items.stream().filter(item -> item.sourceKey() == null).map(Item::id).toList();
        return new ExerciseCatalogSnapshot(hash(mapper.writeValueAsBytes(items)), sourceDatasetVersion,
                unresolvedKeys.isEmpty() && retiredKeys.isEmpty() && legacyIds.isEmpty(),
                unresolvedKeys, retiredKeys, legacyIds, items);
    }

    private static String hash(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
