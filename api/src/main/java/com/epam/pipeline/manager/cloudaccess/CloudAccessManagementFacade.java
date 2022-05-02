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

import com.epam.pipeline.entity.cloudaccess.CloudUserAccessKeys;
import com.epam.pipeline.entity.cloudaccess.policy.CloudAccessPolicy;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.user.PipelineUser;

public interface CloudAccessManagementFacade {

    <R extends AbstractCloudRegion> CloudUserAccessKeys generateAccessKeys(R region, PipelineUser user);

    <R extends AbstractCloudRegion> CloudUserAccessKeys getAccessKeys(R region, PipelineUser user, String keyId);

    <R extends AbstractCloudRegion> void revokeKeys(R region,  PipelineUser user, String keysId);

    <R extends AbstractCloudRegion> CloudAccessPolicy updateCloudUserAccessPolicy(R region, PipelineUser user,
                                                                                  CloudAccessPolicy accessPolicy);

    <R extends AbstractCloudRegion> void revokeCloudUserAccessPermissions(R region, PipelineUser user);

    <R extends AbstractCloudRegion> void deleteUser(R region, PipelineUser user);

    <R extends AbstractCloudRegion> CloudAccessPolicy getCloudUserAccessPermissions(R region, PipelineUser user);
}
