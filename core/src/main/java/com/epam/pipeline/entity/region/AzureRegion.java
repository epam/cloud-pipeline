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

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Azure cloud region.
 *
 * Some of the entity fields are perpetual and cannot be changed since the region is created in dao.
 *
 * Azure cloud region perpetual fields are: {@link AbstractCloudRegion#getRegionCode()}, {@link #resourceGroup},
 * {@link #storageAccount}, {@link #subscription}.
 *
 * Azure cloud region credentials class is {@link AzureRegionCredentials}.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class AzureRegion extends AbstractCloudRegion {

    private CloudProvider provider = CloudProvider.AZURE;
    private String resourceGroup;
    private String storageAccount;
    private AzurePolicy azurePolicy;
    private String corsRules;
    private String subscription;
    private String authFile;

    //Not empty
    private String sshPublicKeyPath;
    private String meterRegionName;
    private String azureApiUrl;
    private String priceOfferId;
    private boolean enterpriseAgreements;
}
