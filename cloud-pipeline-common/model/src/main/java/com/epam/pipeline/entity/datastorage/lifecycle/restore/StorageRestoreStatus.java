package com.epam.pipeline.entity.datastorage.lifecycle.restore;
/*
 * Copyright 2023 EPAM Systems, Inc. (https://www.epam.com/)
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

import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Getter
public enum StorageRestoreStatus {

    INITIATED(true, false),
    RUNNING(true, false),
    SUCCEEDED(true, true),
    CANCELLED(false, true),
    FAILED(false, true);

    public static final List<StorageRestoreStatus> ACTIVE_STATUSES = Arrays.stream(StorageRestoreStatus.values())
            .filter(StorageRestoreStatus::isActive).collect(Collectors.toList());

    public static final List<StorageRestoreStatus> TERMINAL_STATUSES = Arrays.stream(StorageRestoreStatus.values())
            .filter(StorageRestoreStatus::isTerminal).collect(Collectors.toList());

    private final boolean active;
    private final boolean terminal;

    StorageRestoreStatus(final boolean active, final boolean terminal) {
        this.active = active;
        this.terminal = terminal;
    }

}
