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

import com.epam.pipeline.entity.region.AbstractCloudRegionCredentials;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.entity.region.GCPRegion;
import lombok.Getter;
import lombok.SneakyThrows;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;

@Service
public class GCPRegionDaoHelper extends AbstractCloudRegionDaoHelper<GCPRegion, AbstractCloudRegionCredentials> {

    @Getter
    private final CloudProvider provider = CloudProvider.GCP;

    @Override
    MapSqlParameterSource getProviderParameters(final GCPRegion region,
                                                final AbstractCloudRegionCredentials credentials) {
        final MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue(CloudRegionParameters.AUTH_FILE.name(), region.getAuthFile());
        params.addValue(CloudRegionParameters.SSH_PUBLIC_KEY.name(), region.getSshPublicKeyPath());
        params.addValue(CloudRegionParameters.PROJECT.name(), region.getProject());
        return params;
    }

    @Override
    @SneakyThrows
    public GCPRegion parseCloudRegion(final ResultSet rs) {
        final GCPRegion gcpRegion = new GCPRegion();
        fillCommonCloudRegionFields(gcpRegion, rs);
        gcpRegion.setAuthFile(rs.getString(CloudRegionParameters.AUTH_FILE.name()));
        gcpRegion.setSshPublicKeyPath(rs.getString(CloudRegionParameters.SSH_PUBLIC_KEY.name()));
        gcpRegion.setProject(rs.getString(CloudRegionParameters.PROJECT.name()));
        return gcpRegion;
    }

    @Override
    public AbstractCloudRegionCredentials parseCloudRegionCredentials(final ResultSet rs) {
        return null;
    }
}
