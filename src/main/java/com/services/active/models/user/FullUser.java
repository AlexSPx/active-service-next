package com.services.active.models.user;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
public class FullUser {
    @JsonUnwrapped
    private User user;

    @JsonUnwrapped
    private WorkOSUser workOSUser;

    public static FullUser from(User user, WorkOSUser workOSUser) {
        return FullUser.builder()
                .user(user)
                .workOSUser(workOSUser)
                .build();
    }
}