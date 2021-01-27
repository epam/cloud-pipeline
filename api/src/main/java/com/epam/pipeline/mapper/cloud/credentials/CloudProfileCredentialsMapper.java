/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.mapper.cloud.credentials;

import com.epam.pipeline.dto.cloud.credentials.AbstractCloudProfileCredentials;
import com.epam.pipeline.entity.cloud.credentials.CloudProfileCredentialsEntity;
import com.epam.pipeline.dto.cloud.credentials.aws.AWSProfileCredentials;
import com.epam.pipeline.entity.cloud.credentials.aws.AWSProfileCredentialsEntity;
import com.epam.pipeline.entity.region.CloudProvider;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
@SuppressWarnings("unchecked")
public interface CloudProfileCredentialsMapper {

    String UNSUPPORTED_CLOUD_PROVIDER = "Unsupported cloud provider: ";

    default CloudProfileCredentialsEntity toEntity(final AbstractCloudProfileCredentials credentials) {
        return getMapper(credentials.getCloudProvider()).toEntity(this, credentials);
    }

    default AbstractCloudProfileCredentials toDto(final CloudProfileCredentialsEntity entity) {
        return getMapper(entity.getCloudProvider()).toDTO(this, entity);
    };

    AWSProfileCredentialsEntity toAWSEntity(AWSProfileCredentials credentials);

    AWSProfileCredentials toAWSDto(AWSProfileCredentialsEntity entity);

    default CloudProfileCredentialsMapperHelper getMapper(final CloudProvider provider) {
        switch (provider) {
            case AWS: return new CloudProfileCredentialsMapperHelper.AWSHelper();
            default: throw new IllegalArgumentException(UNSUPPORTED_CLOUD_PROVIDER + provider.name());
        }
    }
}
