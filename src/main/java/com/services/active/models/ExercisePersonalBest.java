package com.services.active.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "exercise_personal_bests")
@CompoundIndexes({
        @CompoundIndex(name = "user_exercise_unique", def = "{ 'userId': 1, 'exerciseId': 1 }", unique = true)
})
public class ExercisePersonalBest {
    @Id
    private String id;

    @Indexed
    private String userId;

    @Indexed
    private String exerciseId;

    // Best estimated 1RM (kg) and the record that achieved it
    private Double oneRm;
    private String oneRmRecordId;
    private Integer oneRmRecordSetIndex;

    // Best total volume (kg) across all sets in a record and the record that achieved it
    private Double totalVolume;
    private String totalVolumeRecordId;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

