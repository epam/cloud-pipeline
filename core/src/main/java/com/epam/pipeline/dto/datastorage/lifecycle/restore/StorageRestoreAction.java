/*
 * Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.dto.datastorage.lifecycle.restore;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StorageRestoreAction {
    private Long id;
    private Long datastorageId;
    private Long userActorId;
    private String path;
    private StorageRestorePathType type;
    private Boolean restoreVersions;
    private String restoreMode;
    private Long days;
    private LocalDateTime started;
    private LocalDateTime updated;
    private LocalDateTime restoredTill;
    private StorageRestoreStatus status;
    private StorageRestoreActionNotification notification;

    public String toDescriptionString() {
        return String.format("Id: %d, datastorageId: %d, path: %s, days: %d, " +
                        "started: %s, status: %s, restoredTill: %s", id, datastorageId, path, days,
                started.toString(), status.name(), restoredTill != null ? restoredTill.toString() : ""
        );
    }
}
