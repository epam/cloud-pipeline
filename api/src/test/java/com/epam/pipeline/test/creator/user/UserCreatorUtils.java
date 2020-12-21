/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.test.creator.user;

import com.epam.pipeline.controller.Result;
import com.epam.pipeline.controller.vo.PipelineUserExportVO;
import com.epam.pipeline.controller.vo.PipelineUserVO;
import com.epam.pipeline.controller.vo.user.RoleVO;
import com.epam.pipeline.entity.info.UserInfo;
import com.epam.pipeline.entity.user.CustomControl;
import com.epam.pipeline.entity.user.ExtendedRole;
import com.epam.pipeline.entity.user.GroupStatus;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING_LIST;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING_MAP;

public final class UserCreatorUtils {

    public static final TypeReference<Result<Role>> ROLE_INSTANCE_TYPE = new TypeReference<Result<Role>>() {};
    public static final TypeReference<Result<Collection<Role>>> COLLECTION_ROLE_INSTANCE_TYPE =
            new TypeReference<Result<Collection<Role>>>() {};
    public static final TypeReference<Result<ExtendedRole>> EXTENDED_ROLE_INSTANCE_TYPE =
            new TypeReference<Result<ExtendedRole>>() {};
    public static final TypeReference<Result<PipelineUser>> PIPELINE_USER_INSTANCE_TYPE =
            new TypeReference<Result<PipelineUser>>() {};
    public static final TypeReference<Result<GroupStatus>> GROUP_STATUS_INSTANCE_TYPE =
            new TypeReference<Result<GroupStatus>>() {};

    public static final TypeReference<Result<List<PipelineUser>>> PIPELINE_USER_LIST_INSTANCE_TYPE =
            new TypeReference<Result<List<PipelineUser>>>() {};
    public static final TypeReference<Result<List<UserInfo>>> USER_INFO_LIST_INSTANCE_TYPE =
            new TypeReference<Result<List<UserInfo>>>() {};
    public static final TypeReference<Result<List<CustomControl>>> CUSTOM_CONTROL_LIST_INSTANCE_TYPE =
            new TypeReference<Result<List<CustomControl>>>() {};
    public static final TypeReference<Result<List<GroupStatus>>> GROUP_STATUS_LIST_INSTANCE_TYPE =
            new TypeReference<Result<List<GroupStatus>>>() {};

    private UserCreatorUtils() {

    }

    public static PipelineUser getPipelineUser(final String name) {
        final PipelineUser pipelineUser = new PipelineUser();
        pipelineUser.setId(ID);
        pipelineUser.setUserName(name);
        return pipelineUser;
    }

    public static PipelineUser getPipelineUser() {
        PipelineUser pipelineUser = getPipelineUser(TEST_STRING);
        pipelineUser.setRoles(Collections.singletonList(getRole()));
        pipelineUser.setGroups(TEST_STRING_LIST);
        pipelineUser.setAttributes(TEST_STRING_MAP);
        return pipelineUser;
    }

    public static PipelineUserVO getPipelineUserVO() {
        return new PipelineUserVO();
    }

    public static GroupStatus getGroupStatus() {
        return new GroupStatus(TEST_STRING, true);
    }

    public static UserInfo getUserInfo(final PipelineUser pipelineUser) {
        return new UserInfo(pipelineUser);
    }

    public static CustomControl getCustomControl() {
        return new CustomControl();
    }

    public static PipelineUserExportVO getPipelineUserExportVO() {
        return new PipelineUserExportVO();
    }

    public static Role getRole() {
        return new Role();
    }

    public static RoleVO getRoleVO() {
        return new RoleVO();
    }

    public static ExtendedRole getExtendedRole() {
        return new ExtendedRole();
    }
}
