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
import com.epam.pipeline.entity.security.acl.AclClass;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class AwsRegion extends AbstractSecuredEntity {

    private final AclClass aclClass = AclClass.AWS_REGION;
    // There is no parent for aws region
    private final AbstractSecuredEntity parent = null;
    /**
     * AWS region identifier
     */
    @JsonProperty(value = "regionId")
    private String awsRegionName;
    @JsonProperty(value = "default")
    private boolean isDefault;
    private String corsRules;
    private String policy;
    private String kmsKeyId;
    private String kmsKeyArn;
    private List<String> efsHosts;
}
