/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.entity.datastorage;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Value;

import java.util.Map;

@AllArgsConstructor
@Builder
@Data
public class StorageUsage {
    private Long id;
    private String name;
    private DataStorageType type;
    private String path;
    private Long size;
    private Long count;
    private Long effectiveSize;
    private Long effectiveCount;
    private Long oldVersionsSize;
    private Long oldVersionsEffectiveSize;

    private Map<String, StorageUsageStats> usage;

    @Value
    @Builder
    @AllArgsConstructor
    public static class StorageUsageStats {
        String storageClass;
        Long size;
        Long count;
        Long effectiveSize;
        Long effectiveCount;
        Long oldVersionsSize;
        Long oldVersionsEffectiveSize;
    }
}
