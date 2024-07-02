/*
 * Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.entity.cloudaccess;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CloudAccessManagementConfig {

    Long regionId;
    boolean enabled;

    /**
     * Prefix for name of cloud policy to be created and attached to cloud user.
     * e.g. AWS user inline policy.
     * */
    String cloudAccessPolicyPrefix;

    /**
     * Prefix for cloud-pipeline user metadata tag to mark that user has a cloud access key for specific region
     * */
    String cloudAccessKeyUserMetadataPrefix;

    /**
     * Prefix to be added to username when user is created on the cloud.
     * e.g.: cloud-pipeline username: test_user, cloudUserNamePrefix = 'cp-',
     *       then cloud user name would be 'cp-test_user'.
     *       Such approach will allow to bound set of permission that cloud-pipeline will need
     *       to be able to enable this feature
     * */
    String cloudUserNamePrefix;

    /**
     * Template of the message to show to user when cloud keys are generated
     * */
    String markdownTemplate;

}
