package com.services.active.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoutineDeleteAck {
    private UUID deletedRoutineId;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long profileVersion;
}
