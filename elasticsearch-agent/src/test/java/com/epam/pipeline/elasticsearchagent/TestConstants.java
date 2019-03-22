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
package com.epam.pipeline.elasticsearchagent;

import com.epam.pipeline.elasticsearchagent.model.PermissionsContainer;
import com.epam.pipeline.entity.user.PipelineUser;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.epam.pipeline.elasticsearchagent.ObjectCreationUtils.buildPermissions;
import static com.epam.pipeline.elasticsearchagent.ObjectCreationUtils.buildPipelineUser;

public final class TestConstants {
    public static final String TEST_NAME = "test";
    public static final String USER_NAME = "USER";
    public static final String GROUP_NAME = "GROUP";
    public static final String ADMIN_GROUP = "ROLE_ADMIN";
    public static final String TEST_KEY = "key";
    public static final String TEST_VALUE = "value";
    public static final String ALLOW_USER = "allow_user_name";
    public static final String ALLOW_GROUP = "allow_group_name";
    public static final String DENY_USER = "deny_user_name";
    public static final String DENY_GROUP = "deny_group_name";
    public static final String TEST_DESCRIPTION = "description";
    public static final String TEST_REPO = "repo";
    public static final String TEST_TEMPLATE = "template";
    public static final String TEST_PATH = "test/path";
    public static final String TEST_LABEL = "label";
    public static final String TEST_VERSION = "version";
    public static final String TEST_CMD = "pwd";
    public static final String TEST_REGION = "eu-central-1";
    public static final String TEST_SNAPSHOT = "snapshot";

    public static final Set<String> ALLOWED_USERS = Collections.singleton(ALLOW_USER);
    public static final Set<String> ALLOWED_USERS_WITH_OWNER = new HashSet<>(Arrays.asList(ALLOW_USER, USER_NAME));
    public static final Set<String> DENIED_USERS = Collections.singleton(DENY_USER);
    public static final Set<String> ALLOWED_GROUPS = Collections.singleton(ALLOW_GROUP);
    public static final Set<String> ALLOWED_GROUPS_WITH_ADMIN = new HashSet<>(Arrays.asList(ALLOW_GROUP, ADMIN_GROUP));
    public static final Set<String> DENIED_GROUPS = Collections.singleton(DENY_GROUP);
    public static final List<String> USER_GROUPS = Collections.singletonList(GROUP_NAME);

    public static final Map<String, String> METADATA = Collections.singletonMap(TEST_KEY, TEST_VALUE);
    public static final PipelineUser USER = buildPipelineUser(TEST_NAME, USER_NAME, USER_GROUPS);
    public static final PermissionsContainer PERMISSIONS_CONTAINER =
            buildPermissions(ALLOWED_USERS, DENIED_USERS, ALLOWED_GROUPS, DENIED_GROUPS);
    public static final List<String> EXPECTED_METADATA = Collections.singletonList(TEST_KEY + " " + TEST_VALUE);
    public static final PermissionsContainer PERMISSIONS_CONTAINER_WITH_OWNER =
            buildPermissions(ALLOWED_USERS_WITH_OWNER, DENIED_USERS, ALLOWED_GROUPS_WITH_ADMIN, DENIED_GROUPS);

    private TestConstants() {
        // no-op
    }
}
