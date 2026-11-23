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

package com.epam.pipeline.entity.async;

import com.epam.pipeline.dto.async.AsyncTaskParams;
import com.epam.pipeline.dto.async.AsyncTaskResult;
import com.epam.pipeline.dto.async.AsyncTaskStatus;
import com.epam.pipeline.dto.async.AsyncTaskType;
import com.epam.pipeline.entity.utils.TimestampConverter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "async_task", schema = "pipeline")
public class AsyncTaskEntity {

    @Id
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private AsyncTaskType type;

    @Column(name = "owner", nullable = false)
    private String owner;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AsyncTaskStatus status;

    @Column(name = "error_message")
    private String errorMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "params")
    private AsyncTaskParams params;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result")
    private AsyncTaskResult result;

    @Convert(converter = TimestampConverter.class)
    @Column(name = "created", nullable = false)
    private LocalDateTime created;

    @Convert(converter = TimestampConverter.class)
    @Column(name = "started")
    private LocalDateTime started;

    @Convert(converter = TimestampConverter.class)
    @Column(name = "finished")
    private LocalDateTime finished;

    @Convert(converter = TimestampConverter.class)
    @Column(name = "status_changed", nullable = false)
    private LocalDateTime statusChanged;

    @PrePersist
    void assignId() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
    }
}
