/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.entity.region.CustomInstanceType;
import com.epam.pipeline.entity.region.LocalRegion;
import com.epam.pipeline.entity.region.LocalRegionCredentials;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class LocalRegionDaoHelper extends AbstractCloudRegionDaoHelper<LocalRegion, LocalRegionCredentials> {
    @Getter
    private final CloudProvider provider = CloudProvider.LOCAL;

    @Override
    MapSqlParameterSource getProviderParameters(final LocalRegion region,
                                                final LocalRegionCredentials credentials) {
        final MapSqlParameterSource params = new MapSqlParameterSource();
        if (region.getCustomInstanceTypes() != null) {
            final String customInstanceTypes = JsonMapper.convertDataToJsonStringForQuery(
                    region.getCustomInstanceTypes());
            params.addValue(CloudRegionParameters.CUSTOM_INSTANCE_TYPES.name(), customInstanceTypes);
        }
        Optional.ofNullable(credentials).ifPresent(creds -> {
            params.addValue(CloudRegionParameters.USER_NAME.name(), creds.getUser());
            params.addValue(CloudRegionParameters.USER_PASSWORD.name(), creds.getPassword());
        });
        return params;
    }

    @Override
    @SneakyThrows
    public LocalRegion parseCloudRegion(final ResultSet rs) {
        final LocalRegion region = new LocalRegion();
        fillCommonCloudRegionFields(region, rs);
        final String customInstanceTypes = rs.getString(CloudRegionParameters.CUSTOM_INSTANCE_TYPES.name());
        if (StringUtils.isNotBlank(customInstanceTypes)) {
            region.setCustomInstanceTypes(JsonMapper.parseData(customInstanceTypes,
                    new TypeReference<List<CustomInstanceType>>() {}));
        } else {
            region.setCustomInstanceTypes(Collections.emptyList());
        }
        return region;
    }

    @Override
    @SneakyThrows
    public LocalRegionCredentials parseCloudRegionCredentials(final ResultSet rs) {
        final String user = rs.getString(CloudRegionParameters.USER_NAME.name());
        if (StringUtils.isBlank(user)) {
            return null;
        }
        final LocalRegionCredentials credentials = new LocalRegionCredentials();
        credentials.setUser(user);
        credentials.setPassword(rs.getString(CloudRegionParameters.USER_PASSWORD.name()));
        return credentials;
    }
}
