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
import com.epam.pipeline.entity.region.MountStorageRule;
import lombok.SneakyThrows;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.util.Arrays;
import java.util.Date;
import java.util.Optional;

abstract class AbstractCloudRegionDaoHelper<R extends AbstractCloudRegion, C extends AbstractCloudRegionCredentials>
        implements CloudRegionDaoHelper<R, C> {

    @Override
    public MapSqlParameterSource getParameters(final R region, final C credentials) {
        final MapSqlParameterSource params = new MapSqlParameterSource();
        Optional.ofNullable(region.getId())
                .ifPresent(id -> params.addValue(CloudRegionParameters.REGION_ID.name(), region.getId()));
        params.addValue(CloudRegionParameters.REGION_NAME.name(), region.getRegionCode());
        params.addValue(CloudRegionParameters.NAME.name(), region.getName());
        params.addValue(CloudRegionParameters.IS_DEFAULT.name(), region.isDefault());
        params.addValue(CloudRegionParameters.OWNER.name(), region.getOwner());
        params.addValue(CloudRegionParameters.CREATED_DATE.name(), region.getCreatedDate());
        params.addValue(CloudRegionParameters.CLOUD_PROVIDER.name(), region.getProvider().name());
        params.addValue(CloudRegionParameters.MOUNT_STORAGE_RULE.name(), region.getMountStorageRule().name());
        params.addValues(getProviderParameters(region, credentials).getValues());
        return withFilledMissingValues(params);
    }

    abstract MapSqlParameterSource getProviderParameters(R region, C credentials);

    private MapSqlParameterSource withFilledMissingValues(final MapSqlParameterSource params) {
        Arrays.stream(CloudRegionParameters.values())
                .map(CloudRegionParameters::name)
                .filter(param -> !params.hasValue(param))
                .forEach(param -> params.addValue(param, null));
        return params;
    }

    @SneakyThrows
    void fillCommonCloudRegionFields(final R region, final ResultSet rs) {
        region.setId(rs.getLong(CloudRegionParameters.REGION_ID.name()));
        region.setRegionCode(rs.getString(CloudRegionParameters.REGION_NAME.name()));
        region.setName(rs.getString(CloudRegionParameters.NAME.name()));
        region.setDefault(rs.getBoolean(CloudRegionParameters.IS_DEFAULT.name()));
        region.setOwner(rs.getString(CloudRegionParameters.OWNER.name()));
        region.setCreatedDate(new Date(rs.getTimestamp(CloudRegionParameters.CREATED_DATE.name()).getTime()));
        region.setMountStorageRule(MountStorageRule.valueOf(
                rs.getString(CloudRegionParameters.MOUNT_STORAGE_RULE.name())));
    }
}
