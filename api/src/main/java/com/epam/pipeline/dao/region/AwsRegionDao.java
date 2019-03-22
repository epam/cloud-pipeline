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

import com.epam.pipeline.dao.DaoHelper;
import com.epam.pipeline.entity.region.AwsRegion;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

@RequiredArgsConstructor
// TODO 17.07.18: Replace all fields @Setter with single type annotation after migration to the newest lombok version.
//@Setter(onMethod_={@Required})
public class AwsRegionDao extends NamedParameterJdbcDaoSupport {

    private final DaoHelper daoHelper;
    @Setter(onMethod_={@Required}) private String loadAwsRegionByIdQuery;
    @Setter(onMethod_={@Required}) private String loadAwsRegionByNameQuery;
    @Setter(onMethod_={@Required}) private String loadAllAwsRegionsQuery;
    @Setter(onMethod_={@Required}) private String createAwsRegionQuery;
    @Setter(onMethod_={@Required}) private String updateAwsRegionQuery;
    @Setter(onMethod_={@Required}) private String deleteAwsRegionQuery;
    @Setter(onMethod_={@Required}) private String loadDefaultAwsRegionQuery;
    @Setter(onMethod_={@Required}) private String loadAwsRegionByAwsRegionQuery;

    public Optional<AwsRegion> loadById(final long id) {
        return getJdbcTemplate().query(loadAwsRegionByIdQuery, AwsRegionParameters.getRowMapper(), id)
                .stream()
                .findFirst();
    }

    public Optional<AwsRegion> loadDefaultRegion() {
        return getJdbcTemplate().query(loadDefaultAwsRegionQuery, AwsRegionParameters.getRowMapper())
                .stream()
                .findFirst();
    }

    public Optional<AwsRegion> loadByName(final String name) {
        return getJdbcTemplate().query(loadAwsRegionByNameQuery, AwsRegionParameters.getRowMapper(), name)
                .stream()
                .findFirst();
    }

    public Optional<AwsRegion> loadByAwsRegion(final String awsRegionName) {
        return getJdbcTemplate().query(loadAwsRegionByAwsRegionQuery, AwsRegionParameters.getRowMapper(), awsRegionName)
                .stream()
                .findFirst();
    }

    public List<AwsRegion> loadAll() {
        return getJdbcTemplate().query(loadAllAwsRegionsQuery, AwsRegionParameters.getRowMapper());
    }

    public AwsRegion create(final AwsRegion region) {
        final KeyHolder keyHolder = new GeneratedKeyHolder();
        getNamedParameterJdbcTemplate()
                .update(createAwsRegionQuery, AwsRegionParameters.getParameters(region), keyHolder,
                        new String[] { AwsRegionParameters.AWS_REGION_ID.name().toLowerCase() });
        region.setId(keyHolder.getKey().longValue());
        return region;
    }

    public void update(final AwsRegion region) {
        getNamedParameterJdbcTemplate()
                .update(updateAwsRegionQuery, AwsRegionParameters.getParameters(region));
    }

    public void delete(final Long id) {
        getJdbcTemplate().update(deleteAwsRegionQuery, id);
    }

    private enum AwsRegionParameters {
        AWS_REGION_ID,
        REGION_ID,
        NAME,
        IS_DEFAULT,
        CORS_RULES,
        POLICY,
        KMS_KEY_ID,
        KMS_KEY_ARN,
        OWNER,
        CREATED_DATE,
        EFS_HOSTS;

        private static final String EFS_HOSTS_DELIMITER = ",";

        public static MapSqlParameterSource getParameters(final AwsRegion region) {
            MapSqlParameterSource params = new MapSqlParameterSource();
            Optional.ofNullable(region.getId())
                    .ifPresent(id -> params.addValue(AWS_REGION_ID.name(), region.getId()));
            params.addValue(REGION_ID.name(), region.getAwsRegionName());
            params.addValue(NAME.name(), region.getName());
            params.addValue(IS_DEFAULT.name(), region.isDefault());
            params.addValue(CORS_RULES.name(), region.getCorsRules());
            params.addValue(POLICY.name(), region.getPolicy());
            params.addValue(KMS_KEY_ID.name(), region.getKmsKeyId());
            params.addValue(KMS_KEY_ARN.name(), region.getKmsKeyArn());
            params.addValue(OWNER.name(), region.getOwner());
            params.addValue(CREATED_DATE.name(), region.getCreatedDate());
            params.addValue(EFS_HOSTS.name(),
                    StringUtils.join(ListUtils.emptyIfNull(region.getEfsHosts()), EFS_HOSTS_DELIMITER));
            return params;
        }

        public static RowMapper<AwsRegion> getRowMapper() {
            return (rs, rowNum) -> {
                final AwsRegion awsRegion = new AwsRegion();
                awsRegion.setId(rs.getLong(AWS_REGION_ID.name()));
                awsRegion.setAwsRegionName(rs.getString(REGION_ID.name()));
                awsRegion.setName(rs.getString(NAME.name()));
                awsRegion.setDefault(rs.getBoolean(IS_DEFAULT.name()));
                awsRegion.setCorsRules(rs.getString(CORS_RULES.name()));
                awsRegion.setPolicy(rs.getString(POLICY.name()));
                awsRegion.setKmsKeyId(rs.getString(KMS_KEY_ID.name()));
                awsRegion.setKmsKeyArn(rs.getString(KMS_KEY_ARN.name()));
                awsRegion.setOwner(rs.getString(OWNER.name()));
                awsRegion.setCreatedDate(new Date(rs.getTimestamp(CREATED_DATE.name()).getTime()));
                awsRegion.setEfsHosts(Arrays.asList(
                        StringUtils.defaultIfBlank(rs.getString(EFS_HOSTS.name()), StringUtils.EMPTY)
                                .split(EFS_HOSTS_DELIMITER)));
                return awsRegion;
            };
        }
    }
}
