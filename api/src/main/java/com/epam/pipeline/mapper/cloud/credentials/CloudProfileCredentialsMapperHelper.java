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

import com.epam.pipeline.entity.cloud.credentials.CloudProfileCredentials;
import com.epam.pipeline.entity.cloud.credentials.CloudProfileCredentialsEntity;
import com.epam.pipeline.entity.cloud.credentials.aws.AWSProfileCredentials;
import com.epam.pipeline.entity.cloud.credentials.aws.AWSProfileCredentialsEntity;

public interface CloudProfileCredentialsMapperHelper<D extends CloudProfileCredentials,
        E extends CloudProfileCredentialsEntity> {

    E toEntity(CloudProfileCredentialsMapper mapper, D dto);

    D toDTO(CloudProfileCredentialsMapper mapper, E entity);

    class AWSHelper implements CloudProfileCredentialsMapperHelper<AWSProfileCredentials, AWSProfileCredentialsEntity> {

        @Override
        public AWSProfileCredentialsEntity toEntity(final CloudProfileCredentialsMapper mapper,
                                                    final AWSProfileCredentials dto) {
            return mapper.toAWSEntity(dto);
        }

        @Override
        public AWSProfileCredentials toDTO(final CloudProfileCredentialsMapper mapper,
                                           final AWSProfileCredentialsEntity entity) {
            return mapper.toAWSDto(entity);
        }
    }
}
