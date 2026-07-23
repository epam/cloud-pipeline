/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.dto.credits;

import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.vo.SecuredEntityVO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.StringJoiner;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformUsageCreditsUpdateEvent {

    private static final String ID_DELIMITER = ":";

    private String id;
    private Long userId;
    /** Null for manual admin adjustments. */
    private Long ruleId;
    /** Null for manual admin adjustments. */
    private SecuredEntityVO entity;
    private PlatformUsageCreditsUpdateAction.ActionType incidentType;
    /** Always positive; direction is given by incidentType. */
    private int value;
    private String message;
    private LocalDateTime createdDate;

    public static PlatformUsageCreditsUpdateEvent fromRequest(final PlatformUsageCreditsUpdateRequest request) {
        final PlatformUsageCreditsUpdateEvent event = PlatformUsageCreditsUpdateEvent.builder().userId(request.getUserId())
                .ruleId(request.getRuleId())
                .entity(request.getEntity())
                .incidentType(request.getIncidentType())
                .value(request.getValue())
                .message(request.getMessage())
                .createdDate(request.getCreatedDate())
                .build();
        if (event.getCreatedDate() == null) {
            event.setCreatedDate(DateUtils.nowUTC());
        }
        event.setId(PlatformUsageCreditsUpdateEvent.computeId(event));
        return event;
    }

    public static String computeId(final PlatformUsageCreditsUpdateEvent event) {
        if (event == null || event.getRuleId() == null) {
            return UUID.randomUUID().toString();
        }
        final StringJoiner key = new StringJoiner(ID_DELIMITER);
        key.add(String.valueOf(event.getUserId()));
        if (event.getRuleId() != null) {
            key.add(String.valueOf(event.getRuleId()));
        }
        if (event.getEntity() != null) {
            key.add(event.getEntity().getEntityClass());
            key.add(String.valueOf(event.getEntity().getEntityId()));
        }
        key.add(event.getIncidentType().name());
        return UUID.nameUUIDFromBytes(key.toString().getBytes(StandardCharsets.UTF_8)).toString();
    }
}
