/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.controller.vo;

import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.StoragePolicy;
import com.epam.pipeline.entity.datastorage.StorageServiceType;
import com.epam.pipeline.entity.pipeline.ToolFingerprint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
public class DataStorageVO {
    private Long id;
    private String name;
    private String description;
    private String path;
    private StorageServiceType serviceType;

    /**
     * Prefer to pass storage type as {@code serviceType},
     * this field is user only for backward compatibility
     */
    @Deprecated
    private DataStorageType type;
    private Date createdDate;
    private Long parentFolderId;
    private StoragePolicy storagePolicy;
    private String mountOptions;
    private String mountPoint;
    private boolean shared;
    private List<String> allowedCidrs;
    private Long regionId;
    private Long fileShareMountId;
    private boolean mountExactPath;
    private boolean sensitive;
    private List<ToolFingerprint> toolsToMount;
    private Boolean mountDisabled;

    // S3 specific fields
    private String tempCredentialsRole;
    private String kmsKeyArn;
    private boolean useAssumedCredentials;

    private Long sourceStorageId;
    private Set<String> linkingMasks;
}
