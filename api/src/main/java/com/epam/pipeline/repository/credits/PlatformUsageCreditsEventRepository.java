/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.repository.credits;

import com.epam.pipeline.entity.credits.PlatformUsageCreditsUpdateEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.sql.Timestamp;

public interface PlatformUsageCreditsEventRepository
        extends JpaRepository<PlatformUsageCreditsUpdateEventEntity, Long>,
                JpaSpecificationExecutor<PlatformUsageCreditsUpdateEventEntity> {

    @Modifying
    @Query(value = "INSERT INTO pipeline.usage_credits_update_event"
            + " (user_id, rule_id, entity_class, entity_id, incident_type, value, message, created_date)"
            + " VALUES (:userId, :ruleId, :entityClass, :entityId, :incidentType, :value, :message, :createdDate)"
            + " ON CONFLICT (rule_id, entity_class, entity_id, incident_type)"
            + " WHERE rule_id IS NOT NULL AND entity_class IS NOT NULL AND entity_id IS NOT NULL"
            + " DO NOTHING",
           nativeQuery = true)
    void insertIfAbsent(
            @Param("userId") Long userId,
            @Param("ruleId") Long ruleId,
            @Param("entityClass") String entityClass,
            @Param("entityId") Long entityId,
            @Param("incidentType") String incidentType,
            @Param("value") int value,
            @Param("message") String message,
            @Param("createdDate") Timestamp createdDate);
}
