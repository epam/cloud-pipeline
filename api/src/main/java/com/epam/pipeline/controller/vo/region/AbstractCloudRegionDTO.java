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
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "provider")
@JsonSubTypes({
        @JsonSubTypes.Type(value = AWSRegionDTO.class, name = "AWS"),
        @JsonSubTypes.Type(value = AzureRegionDTO.class, name = "AZURE"),
        @JsonSubTypes.Type(value = GCPRegionDTO.class, name = "GCP")})
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class AbstractCloudRegionDTO {

    private Long id;
    @JsonProperty(value = "regionId")
    private String regionCode;
    private String name;
    @JsonProperty(value = "default")
    private boolean isDefault;
    private CloudProvider provider;
}
