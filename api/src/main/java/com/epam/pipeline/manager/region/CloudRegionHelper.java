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

package com.epam.pipeline.manager.region;

import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.AbstractCloudRegionCredentials;
import com.epam.pipeline.manager.cloud.CloudAwareService;

import java.util.List;

public interface CloudRegionHelper<R extends AbstractCloudRegion, C extends AbstractCloudRegionCredentials>
        extends CloudAwareService {

    /**
     * Asserts if the given cloud region and credentials instances are sufficient for use as a cloud region.
     *
     * @param region Cloud region to be asserted.
     * @param credentials Optional credentials to be asserted.
     */
    void validateRegion(R region, C credentials);

    /**
     * Lists all available region codes for the current provider.
     */
    List<String> loadAvailableRegions();

    /**
     * Merges two region instances into a single one with fields from the original region overridden by the fields
     * in the updated region. It also preserves cloud region perpetual fields.
     *
     * Notice: It mutates the original region instance and returns it.
     *
     * @param originalRegion Region that will be used as a base one while merging.
     * @param updatedRegion Region that will be used as a changed one while merging.
     * @return Region with merged fields from both region.
     */
    R mergeRegions(R originalRegion, R updatedRegion);

    /**
     * Merges two region credentials instances into a single one with fields from the original credentials overridden
     * by the fields in the updated region.
     *
     * @param oldCredentials Region credentials that will be used as a base one while merging.
     * @param updatedCredentials Region credentials that will be used as a changed one while merging.
     * @return Region credentials with merged fields from both region or null.
     */
    default C mergeCredentials(final C oldCredentials, final C updatedCredentials) {
        return updatedCredentials;
    }
}
