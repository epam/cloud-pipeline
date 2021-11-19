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

package com.epam.pipeline.test.creator.cloud.credentials;

import com.epam.pipeline.dto.cloud.credentials.aws.AWSProfileCredentials;
import com.epam.pipeline.entity.cloud.credentials.aws.AWSProfileCredentialsEntity;
import com.epam.pipeline.entity.region.CloudProvider;

public final class CloudProfileCredentialsCreatorUtils {
    public static final String ASSUMED_ROLE = "role";
    public static final String POLICY = "{}";
    public static final String PROFILE = "default";

    private CloudProfileCredentialsCreatorUtils() {
        // no-op
    }

    public static AWSProfileCredentialsEntity awsProfileCredentialsEntity() {
        return awsProfileCredentialsEntity(null);
    }

    public static AWSProfileCredentialsEntity awsProfileCredentialsEntity(final Long id) {
        final AWSProfileCredentialsEntity entity = new AWSProfileCredentialsEntity();
        entity.setId(id);
        entity.setCloudProvider(CloudProvider.AWS);
        entity.setAssumedRole(ASSUMED_ROLE);
        entity.setPolicy(POLICY);
        entity.setProfileName(PROFILE);
        return entity;
    }

    public static AWSProfileCredentials awsProfileCredentials() {
        return awsProfileCredentials(null);
    }

    public static AWSProfileCredentials awsProfileCredentials(final Long id) {
        final AWSProfileCredentials credentials = new AWSProfileCredentials();
        credentials.setId(id);
        credentials.setAssumedRole(ASSUMED_ROLE);
        credentials.setPolicy(POLICY);
        credentials.setProfileName(PROFILE);
        return credentials;
    }
}
