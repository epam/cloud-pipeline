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

package com.epam.pipeline.entity.cloud.credentials;

import com.epam.pipeline.entity.cloud.credentials.aws.AWSProfileCredentials;
import com.epam.pipeline.entity.region.CloudProvider;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "cloudProvider")
@JsonSubTypes({@JsonSubTypes.Type(value = AWSProfileCredentials.class, name = "AWS")})
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class CloudProfileCredentials {

    private Long id;

    public abstract CloudProvider getCloudProvider();

    public abstract void setCloudProvider(CloudProvider cloudProvider);
}
