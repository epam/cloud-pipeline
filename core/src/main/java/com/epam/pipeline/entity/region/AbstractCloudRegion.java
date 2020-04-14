/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.entity.region;

import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.datastorage.FileShareMount;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor
@Getter
@Setter
public abstract class AbstractCloudRegion extends AbstractSecuredEntity {

    private final AclClass aclClass = AclClass.CLOUD_REGION;
    // There is no parent for cloud region
    private final AbstractSecuredEntity parent = null;
    /**
     * String code from cloud provider
     */
    @JsonProperty(value = "regionId")
    private String regionCode;
    @JsonProperty(value = "default")
    private boolean isDefault;
    private List<FileShareMount> fileShareMounts = new ArrayList<>();
    private MountStorageRule mountStorageRule = MountStorageRule.NONE;
    public abstract CloudProvider getProvider();
}
