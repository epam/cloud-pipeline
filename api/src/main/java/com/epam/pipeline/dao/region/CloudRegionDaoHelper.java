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

package com.epam.pipeline.dao.region;

import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.AbstractCloudRegionCredentials;
import com.epam.pipeline.manager.cloud.CloudAwareService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;

/**
 * Provider-specific cloud region helper.
 */
interface CloudRegionDaoHelper<R extends AbstractCloudRegion, C extends AbstractCloudRegionCredentials>
        extends CloudAwareService {

    /**
     * Retrieves all SQL query parameters from the given region and credentials.
     */
    MapSqlParameterSource getParameters(R region, C credentials);

    /**
     * Parses specific cloud region from the given SQL result set.
     */
    R parseCloudRegion(ResultSet rs);

    /**
     * Parses specific cloud region credentials from the given SQL result set.
     */
    C parseCloudRegionCredentials(ResultSet rs);
}
