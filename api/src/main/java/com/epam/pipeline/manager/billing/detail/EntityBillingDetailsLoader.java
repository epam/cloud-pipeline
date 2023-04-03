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

package com.epam.pipeline.manager.billing.detail;

import com.epam.pipeline.entity.billing.BillingGrouping;

import java.util.Collections;
import java.util.Map;

public interface EntityBillingDetailsLoader {

    String ID = "id";
    String OWNER = "owner";
    String NAME = "name";
    String BILLING_CENTER = "billing_center";
    String REGION = "region";
    String PROVIDER = "provider";
    String CREATED = "created";
    String IS_DELETED = "is_deleted";

    BillingGrouping getGrouping();

    default Map<String, String> loadDetails(String entityIdentifier) {
        return loadInformation(entityIdentifier, true);
    }

    /**
     * Build a map of details for given entity. Always contains a value for {@linkplain #NAME} property
     * @param entityIdentifier id of entity to build information
     * @return entity's details
     */
    default Map<String, String> loadInformation(String entityIdentifier, boolean loadDetails) {
        return loadInformation(entityIdentifier, loadDetails, Collections.emptyMap());
    }

    Map<String, String> loadInformation(String entityIdentifier,
                                        boolean loadDetails,
                                        Map<String, String> defaults);

}
