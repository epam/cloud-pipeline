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

package com.epam.pipeline.manager.cloudaccess;

import com.epam.pipeline.entity.cloudaccess.CloudUser;
import com.epam.pipeline.entity.cloudaccess.CloudUserAccessKeys;
import com.epam.pipeline.entity.cloudaccess.CloudUserAccessPolicy;
import com.epam.pipeline.manager.cloud.CloudAwareService;

public interface CloudAccessManagementService extends CloudAwareService {

    boolean doesCloudUserExist(String username);

    CloudUser createCloudUser(String username);

    CloudUser deleteCloudUser(String username);

    CloudUserAccessPolicy grantCloudUserPermissions(String policyName, CloudUserAccessPolicy userPolicy);

    boolean revokeCloudUserPermissions(String policyName);

    CloudUserAccessKeys generateCloudKeysForUser(String username);

    boolean revokeCloudKeysForUser(String username, String keyId);

}
