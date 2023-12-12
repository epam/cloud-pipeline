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

package com.epam.pipeline.entity.info;

import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CloudRegionInfo {

    private Long id;
    private String name;
    private CloudProvider provider;
    private String regionId;
    private String endpoint;

    public CloudRegionInfo(final AbstractCloudRegion region) {
        this.id = region.getId();
        this.name = region.getName();
        this.provider = region.getProvider();
        this.regionId = region.getRegionCode();
        if (region instanceof AwsRegion) {
            this.endpoint = ((AwsRegion)region).getS3Endpoint();
        }
    }
}
