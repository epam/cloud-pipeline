/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.repository.async;

import com.epam.pipeline.dto.async.AsyncTaskStatus;
import com.epam.pipeline.entity.async.AsyncTaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

/**
 * Every state change is conditional on the status the caller expects, so a concurrent change loses
 * instead of overwriting. Statuses travel as strings: a native query would bind an enum by ordinal.
 */
public interface AsyncTaskRepository extends JpaRepository<AsyncTaskEntity, String>,
        JpaSpecificationExecutor<AsyncTaskEntity> {

    String LOCK_NEXT_PENDING = "SELECT id FROM pipeline.async_task "
            + "WHERE status = 'PENDING' AND type = :type "
            + "ORDER BY created "
            + "LIMIT 1 FOR UPDATE SKIP LOCKED";

    String MOVE_STATUS = "UPDATE pipeline.async_task SET "
            + "status = :target, "
            + "status_changed = :changedAt, "
            + "started = CASE WHEN :target = 'RUNNING' THEN :changedAt ELSE started END, "
            + "finished = CASE WHEN :target = 'SUCCESS' THEN :changedAt ELSE finished END, "
            + "result = COALESCE(CAST(:result AS jsonb), result), "
            + "error_message = COALESCE(:errorMessage, error_message) "
            + "WHERE id = :id AND status = :expected";

    String CANCEL_REQUEST = "UPDATE pipeline.async_task SET "
            + "status = CASE status WHEN 'PENDING' THEN 'CANCELLED' ELSE 'CANCEL_REQUESTED' END, "
            + "status_changed = :changedAt "
            + "WHERE id = :id AND status IN ('PENDING', 'RUNNING')";

    long countByOwnerAndStatusIn(String owner, Collection<AsyncTaskStatus> statuses);

    /**
     * Takes the oldest pending task of the type and holds its row lock until the transaction ends.
     * Rows locked by another claimer are skipped, so two callers get two different tasks.
     *
     * @return id of the locked task, empty when nothing is pending
     */
    @Query(value = LOCK_NEXT_PENDING, nativeQuery = true)
    Optional<String> lockNextPending(@Param("type") String type);

    /**
     * Moves the task to {@code target} only while it is still in {@code expected}.
     *
     * @param resultJson   serialized result, ignored when null
     * @param errorMessage error text, ignored when null
     * @return number of rows changed, so 0 means someone else moved the task first
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = MOVE_STATUS, nativeQuery = true)
    int moveStatus(@Param("id") String id,
                   @Param("expected") String expected,
                   @Param("target") String target,
                   @Param("result") String resultJson,
                   @Param("errorMessage") String errorMessage,
                   @Param("changedAt") LocalDateTime changedAt);


    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = CANCEL_REQUEST, nativeQuery = true)
    int cancelTask(@Param("id") String id, @Param("changedAt") LocalDateTime changedAt);
}
