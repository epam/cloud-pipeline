/*
 * Copyright 2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.dto.quota;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;

import static com.epam.pipeline.dto.quota.QuotaActionType.BLOCK;
import static com.epam.pipeline.dto.quota.QuotaActionType.NOTIFY;
import static com.epam.pipeline.dto.quota.QuotaActionType.READ_MODE;
import static com.epam.pipeline.dto.quota.QuotaActionType.DISABLE_NEW_JOBS;
import static com.epam.pipeline.dto.quota.QuotaActionType.STOP_JOBS;

@RequiredArgsConstructor
@Getter
public enum QuotaGroup {
    GLOBAL(null), COMPUTE_INSTANCE("COMPUTE"), STORAGE("STORAGE");

    private final String resourceType;

    public List<QuotaActionType> getAllowedActions() {
        switch (this) {
            case COMPUTE_INSTANCE:
                return Arrays.asList(NOTIFY, DISABLE_NEW_JOBS, STOP_JOBS, BLOCK);
            case STORAGE:
                return Arrays.asList(NOTIFY, READ_MODE, BLOCK);
            default:
                return Arrays.asList(QuotaActionType.values());
        }
    }
}
