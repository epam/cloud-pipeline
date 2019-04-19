/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.controller.vo.region;

import com.epam.pipeline.entity.region.CloudProvider;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.epam.pipeline.entity.region.GCPCustomInstanceType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class GCPRegionDTO extends AbstractCloudRegionDTO {

    private CloudProvider provider = CloudProvider.GCP;
    private String authFile;
    private String sshPublicKeyPath;
    private String project;
    private String applicationName;
    @JsonProperty("tempCredentialsRole")
    private String impersonatedAccount;
    private List<GCPCustomInstanceType> customInstanceTypes;
    private String corsRules;
    private String policy;
}
