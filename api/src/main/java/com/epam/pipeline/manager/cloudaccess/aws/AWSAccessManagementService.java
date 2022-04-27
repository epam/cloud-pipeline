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

package com.epam.pipeline.manager.cloudaccess.aws;

import com.epam.pipeline.entity.cloudaccess.CloudUser;
import com.epam.pipeline.entity.cloudaccess.CloudUserAccessKeys;
import com.epam.pipeline.entity.cloudaccess.CloudUserAccessPolicy;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.manager.cloudaccess.CloudAccessManagementService;

public class AWSAccessManagementService implements CloudAccessManagementService {

    @Override
    public CloudProvider getProvider() {
        return null;
    }

    @Override
    public boolean doesCloudUserExist(String username) {
        return false;
    }

    @Override
    public CloudUser createCloudUser(String username) {
        return null;
    }

    @Override
    public CloudUser deleteCloudUser(String username) {
        return null;
    }

    @Override
    public CloudUserAccessPolicy grantCloudUserPermissions(String policyName, CloudUserAccessPolicy userPolicy) {
        return null;
    }

    @Override
    public boolean revokeCloudUserPermissions(String policyName) {
        return false;
    }

    @Override
    public CloudUserAccessKeys generateCloudKeysForUser(String username) {
        return null;
    }

    @Override
    public boolean revokeCloudKeysForUser(String username, String keyId) {
        return false;
    }
}
