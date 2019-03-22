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

import com.epam.pipeline.entity.datastorage.FileShareMount;
import com.epam.pipeline.entity.datastorage.MountType;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.AbstractCloudRegionCredentials;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.utils.CommonUtils;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@SuppressWarnings("unchecked")
public class CloudRegionDao extends NamedParameterJdbcDaoSupport {

    private final String loadRegionByIdQuery;
    private final String loadRegionByNameQuery;
    private final String loadAllRegionsQuery;
    private final String createRegionQuery;
    private final String updateRegionQuery;
    private final String deleteRegionQuery;
    private final String loadDefaultRegionQuery;
    private final String loadRegionByRegionNameQuery;
    private final String loadByProviderAndRegionCodeQuery;
    private final String loadCredentialsByRegionId;
    private final Map<CloudProvider, ? extends CloudRegionDaoHelper> helpers;

    public CloudRegionDao(final String loadRegionByIdQuery,
                          final String loadRegionByNameQuery,
                          final String loadAllRegionsQuery,
                          final String createRegionQuery,
                          final String updateRegionQuery,
                          final String deleteRegionQuery,
                          final String loadDefaultRegionQuery,
                          final String loadRegionByRegionNameQuery,
                          final String loadByProviderAndRegionCodeQuery,
                          final String loadCredentialsByRegionId,
                          final List<CloudRegionDaoHelper> helpers) {
        this.loadRegionByIdQuery = loadRegionByIdQuery;
        this.loadRegionByNameQuery = loadRegionByNameQuery;
        this.loadAllRegionsQuery = loadAllRegionsQuery;
        this.createRegionQuery = createRegionQuery;
        this.updateRegionQuery = updateRegionQuery;
        this.deleteRegionQuery = deleteRegionQuery;
        this.loadDefaultRegionQuery = loadDefaultRegionQuery;
        this.loadRegionByRegionNameQuery = loadRegionByRegionNameQuery;
        this.loadByProviderAndRegionCodeQuery = loadByProviderAndRegionCodeQuery;
        this.loadCredentialsByRegionId = loadCredentialsByRegionId;
        this.helpers = CommonUtils.groupByCloudProvider(helpers);
    }

    public Optional<AbstractCloudRegion> loadById(final long id) {
        return getJdbcTemplate().query(loadRegionByIdQuery, getRowMapper(), id)
                .stream()
                .findFirst();
    }

    public Optional<AbstractCloudRegion> loadDefaultRegion() {
        return getJdbcTemplate().query(loadDefaultRegionQuery, getRowMapper())
                .stream()
                .findFirst();
    }

    public Optional<AbstractCloudRegion> loadByName(final String name) {
        return getJdbcTemplate().query(loadRegionByNameQuery, getRowMapper(), name)
                .stream()
                .findFirst();
    }

    public Optional<AbstractCloudRegion> loadByRegionName(final String regionName) {
        return getJdbcTemplate().query(loadRegionByRegionNameQuery, getRowMapper(), regionName)
                .stream()
                .findFirst();
    }

    public List<? extends AbstractCloudRegion> loadAll() {
        return getJdbcTemplate().query(loadAllRegionsQuery, getRowMapper());
    }

    /**
     * Stores a new cloud region.
     *
     * @param region to be stored.
     * @return Stored cloud region.
     */
    public AbstractCloudRegion create(final AbstractCloudRegion region) {
        return create(region, null);
    }

    /**
     * Stores a new cloud region.
     *
     * @param region to be stored.
     * @param credentials if specified then cloud region credentials will be stored as well.
     * @return Stored cloud region.
     */
    public AbstractCloudRegion create(final AbstractCloudRegion region,
                                      final AbstractCloudRegionCredentials credentials) {
        final KeyHolder keyHolder = new GeneratedKeyHolder();
        getNamedParameterJdbcTemplate()
                .update(createRegionQuery, getParameters(region, credentials), keyHolder,
                        new String[] { CloudRegionParameters.REGION_ID.name().toLowerCase() });
        region.setId(keyHolder.getKey().longValue());
        return region;
    }

    public void update(final AbstractCloudRegion region, final AbstractCloudRegionCredentials credentials) {
        getNamedParameterJdbcTemplate()
                .update(updateRegionQuery, getParameters(region, credentials));
    }

    public void delete(final Long id) {
        getJdbcTemplate().update(deleteRegionQuery, id);
    }

    public Optional<AbstractCloudRegion> loadByProviderAndRegionCode(final CloudProvider provider,
                                                                     final String regionCode) {
        final MapSqlParameterSource parameterSource = new MapSqlParameterSource();
        parameterSource.addValue(CloudRegionParameters.REGION_NAME.name(), regionCode);
        parameterSource.addValue(CloudRegionParameters.CLOUD_PROVIDER.name(), provider.name());
        return getNamedParameterJdbcTemplate()
                .query(loadByProviderAndRegionCodeQuery, parameterSource, getRowMapper())
                .stream()
                .findFirst();
    }

    public Optional<AbstractCloudRegionCredentials> loadCredentials(final Long id) {
        final MapSqlParameterSource parameterSource = new MapSqlParameterSource();
        parameterSource.addValue(CloudRegionParameters.REGION_ID.name(), id);
        return getNamedParameterJdbcTemplate()
                .query(loadCredentialsByRegionId, parameterSource, getCredentialsRowMapper())
                .stream()
                .filter(Objects::nonNull)
                .findFirst();
    }

    private MapSqlParameterSource getParameters(final AbstractCloudRegion region,
                                                final AbstractCloudRegionCredentials credentials) {
        return getHelper(region.getProvider()).getParameters(region, credentials);
    }

    private ResultSetExtractor<List<AbstractCloudRegion>> getRowMapper() {
        return (rs) -> {
            Map<Long, AbstractCloudRegion> regions = new HashMap<>();
            while (rs.next()) {
                Long regionId = rs.getLong(CloudRegionParameters.REGION_ID.name());
                AbstractCloudRegion cloudRegion = regions.get(regionId);
                if (cloudRegion == null) {
                    cloudRegion = getHelper(CloudProvider.valueOf(rs.getString(
                            CloudRegionParameters.CLOUD_PROVIDER.name()))).parseCloudRegion(rs);
                    cloudRegion.setFileShareMounts(new ArrayList<>());
                    regions.put(regionId, cloudRegion);
                }
                getFileShareMount(rs, cloudRegion);
            }
            return new ArrayList<>(regions.values());
        };
    }

    private RowMapper<AbstractCloudRegionCredentials> getCredentialsRowMapper() {
        return (rs, rowNum) -> getHelper(CloudProvider.valueOf(rs.getString(
                CloudRegionParameters.CLOUD_PROVIDER.name()))).parseCloudRegionCredentials(rs);
    }

    private CloudRegionDaoHelper getHelper(final CloudProvider type) {
        return helpers.get(type);
    }

    private void getFileShareMount(ResultSet rs, AbstractCloudRegion cloudRegion) throws SQLException {
        Long mountId = rs.getLong(CloudRegionParameters.MOUNT_ID.name());
        if (!rs.wasNull()) {
            FileShareMount shareMount = new FileShareMount();
            shareMount.setId(mountId);
            shareMount.setRegionId(cloudRegion.getId());
            String mountOptions = rs.getString(CloudRegionParameters.MOUNT_OPTIONS.name());
            if (!rs.wasNull()) {
                shareMount.setMountOptions(mountOptions);
            }
            String mountRoot = rs.getString(CloudRegionParameters.MOUNT_ROOT.name());
            if (!rs.wasNull()) {
                shareMount.setMountRoot(mountRoot);
            }
            String mountType = rs.getString(CloudRegionParameters.MOUNT_TYPE.name());
            if (!rs.wasNull()) {
                shareMount.setMountType(MountType.valueOf(mountType));
            }
            cloudRegion.getFileShareMounts().add(shareMount);
        }
    }
}
