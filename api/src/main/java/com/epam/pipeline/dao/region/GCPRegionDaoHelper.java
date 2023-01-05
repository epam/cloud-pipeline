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

package com.epam.pipeline.dao.region;

import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.entity.region.AbstractCloudRegionCredentials;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.entity.region.GCPCustomInstanceType;
import com.epam.pipeline.entity.region.GCPRegion;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.util.Collections;
import java.util.List;

@Service
public class GCPRegionDaoHelper extends AbstractCloudRegionDaoHelper<GCPRegion, AbstractCloudRegionCredentials> {

    @Getter
    private final CloudProvider provider = CloudProvider.GCP;

    @Override
    MapSqlParameterSource getProviderParameters(final GCPRegion region,
                                                final AbstractCloudRegionCredentials credentials) {
        final MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue(CloudRegionParameters.CORS_RULES.name(), region.getCorsRules());
        params.addValue(CloudRegionParameters.POLICY.name(), region.getPolicy());
        params.addValue(CloudRegionParameters.AUTH_FILE.name(), region.getAuthFile());
        params.addValue(CloudRegionParameters.SSH_PUBLIC_KEY.name(), region.getSshPublicKeyPath());
        params.addValue(CloudRegionParameters.PROJECT.name(), region.getProject());
        params.addValue(CloudRegionParameters.APPLICATION_NAME.name(), region.getApplicationName());
        params.addValue(CloudRegionParameters.IMPERSONATED_ACCOUNT.name(), region.getImpersonatedAccount());
        if (region.getCustomInstanceTypes() != null) {
            final String customInstanceTypes = JsonMapper.convertDataToJsonStringForQuery(
                    region.getCustomInstanceTypes());
            params.addValue(CloudRegionParameters.CUSTOM_INSTANCE_TYPES.name(), customInstanceTypes);
        }
        params.addValue(CloudRegionParameters.VERSIONING_ENABLED.name(), region.isVersioningEnabled());
        params.addValue(CloudRegionParameters.BACKUP_DURATION.name(), region.getBackupDuration());
        params.addValue(CloudRegionParameters.GLOBAL_DISTRIBUTION_URL.name(), region.getGlobalDistributionUrl());
        return params;
    }

    @Override
    @SneakyThrows
    public GCPRegion parseCloudRegion(final ResultSet rs) {
        final GCPRegion region = new GCPRegion();
        fillCommonCloudRegionFields(region, rs);
        region.setCorsRules(rs.getString(CloudRegionParameters.CORS_RULES.name()));
        region.setPolicy(rs.getString(CloudRegionParameters.POLICY.name()));
        region.setAuthFile(rs.getString(CloudRegionParameters.AUTH_FILE.name()));
        region.setSshPublicKeyPath(rs.getString(CloudRegionParameters.SSH_PUBLIC_KEY.name()));
        region.setProject(rs.getString(CloudRegionParameters.PROJECT.name()));
        region.setApplicationName(rs.getString(CloudRegionParameters.APPLICATION_NAME.name()));
        region.setImpersonatedAccount(rs.getString(CloudRegionParameters.IMPERSONATED_ACCOUNT.name()));
        final String customInstanceTypes = rs.getString(CloudRegionParameters.CUSTOM_INSTANCE_TYPES.name());
        if (StringUtils.isNotBlank(customInstanceTypes)) {
            region.setCustomInstanceTypes(JsonMapper.parseData(customInstanceTypes,
                    new TypeReference<List<GCPCustomInstanceType>>() {}));
        } else {
            region.setCustomInstanceTypes(Collections.emptyList());
        }
        int backup = rs.getInt(CloudRegionParameters.BACKUP_DURATION.name());
        if (!rs.wasNull()) {
            region.setBackupDuration(backup);
        }
        region.setVersioningEnabled(rs.getBoolean(CloudRegionParameters.VERSIONING_ENABLED.name()));
        region.setGlobalDistributionUrl(rs.getString(CloudRegionParameters.GLOBAL_DISTRIBUTION_URL.name()));
        return region;
    }

    @Override
    public AbstractCloudRegionCredentials parseCloudRegionCredentials(final ResultSet rs) {
        return null;
    }
}
