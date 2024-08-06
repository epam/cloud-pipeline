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

import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.region.AwsRegionCredentials;
import com.epam.pipeline.entity.region.CloudProvider;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.util.Optional;

@Service
class AwsRegionDaoHelper extends AbstractCloudRegionDaoHelper<AwsRegion, AwsRegionCredentials> {
    @Getter
    private final CloudProvider provider = CloudProvider.AWS;

    @Override
    public MapSqlParameterSource getProviderParameters(final AwsRegion region,
                                                       final AwsRegionCredentials credentials) {
        final MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue(CloudRegionParameters.CORS_RULES.name(), region.getCorsRules());
        params.addValue(CloudRegionParameters.POLICY.name(), region.getPolicy());
        params.addValue(CloudRegionParameters.KMS_KEY_ID.name(), region.getKmsKeyId());
        params.addValue(CloudRegionParameters.KMS_KEY_ARN.name(), region.getKmsKeyArn());
        params.addValue(CloudRegionParameters.PROFILE.name(), region.getProfile());
        params.addValue(CloudRegionParameters.TEMP_CREDENTIALS_ROLE.name(), region.getTempCredentialsRole());
        params.addValue(CloudRegionParameters.BACKUP_DURATION.name(), region.getBackupDuration());
        params.addValue(CloudRegionParameters.VERSIONING_ENABLED.name(), region.isVersioningEnabled());
        params.addValue(CloudRegionParameters.SSH_KEY_NAME.name(), region.getSshKeyName());
        params.addValue(CloudRegionParameters.AWS_IAM_ROLE.name(), region.getIamRole());
        params.addValue(CloudRegionParameters.AWS_OMICS_SERVICE_ROLE.name(), region.getOmicsServiceRole());
        params.addValue(CloudRegionParameters.AWS_OMICS_ECR_URL.name(), region.getOmicsEcrUrl());
        params.addValue(CloudRegionParameters.AWS_S3_ENDPOINT.name(), region.getS3Endpoint());
        Optional.ofNullable(credentials).ifPresent(creds -> {
            params.addValue(CloudRegionParameters.AWS_KEY_ID.name(), creds.getKeyId());
            params.addValue(CloudRegionParameters.AWS_ACCESS_KEY.name(), creds.getAccessKey());
        });

        return params;
    }

    @Override
    @SneakyThrows
    public AwsRegion parseCloudRegion(final ResultSet rs) {
        final AwsRegion region = new AwsRegion();
        fillCommonCloudRegionFields(region, rs);
        region.setCorsRules(rs.getString(CloudRegionParameters.CORS_RULES.name()));
        region.setPolicy(rs.getString(CloudRegionParameters.POLICY.name()));
        region.setKmsKeyId(rs.getString(CloudRegionParameters.KMS_KEY_ID.name()));
        region.setKmsKeyArn(rs.getString(CloudRegionParameters.KMS_KEY_ARN.name()));
        region.setProfile(rs.getString(CloudRegionParameters.PROFILE.name()));
        region.setTempCredentialsRole(rs.getString(CloudRegionParameters.TEMP_CREDENTIALS_ROLE.name()));
        final int backupDuration = rs.getInt(CloudRegionParameters.BACKUP_DURATION.name());
        if (!rs.wasNull()) {
            region.setBackupDuration(backupDuration);
        }
        region.setVersioningEnabled(rs.getBoolean(CloudRegionParameters.VERSIONING_ENABLED.name()));
        region.setSshKeyName(rs.getString(CloudRegionParameters.SSH_KEY_NAME.name()));
        region.setIamRole(rs.getString(CloudRegionParameters.AWS_IAM_ROLE.name()));
        region.setOmicsServiceRole(rs.getString(CloudRegionParameters.AWS_OMICS_SERVICE_ROLE.name()));
        region.setOmicsEcrUrl(rs.getString(CloudRegionParameters.AWS_OMICS_ECR_URL.name()));
        region.setS3Endpoint(rs.getString(CloudRegionParameters.AWS_S3_ENDPOINT.name()));
        return region;
    }

    @Override
    @SneakyThrows
    public AwsRegionCredentials parseCloudRegionCredentials(final ResultSet rs) {
        final String keyId = rs.getString(CloudRegionParameters.AWS_KEY_ID.name());
        if (StringUtils.isBlank(keyId)) {
            return null;
        }
        final AwsRegionCredentials credentials = new AwsRegionCredentials();
        credentials.setKeyId(keyId);
        credentials.setAccessKey(rs.getString(CloudRegionParameters.AWS_ACCESS_KEY.name()));
        return credentials;
    }
}
