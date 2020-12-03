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

import com.epam.pipeline.controller.vo.PipelineUserExportVO;
import com.epam.pipeline.controller.vo.user.RoleVO;
import com.epam.pipeline.entity.info.UserInfo;
import com.epam.pipeline.entity.user.CustomControl;
import com.epam.pipeline.entity.user.ExtendedRole;
import com.epam.pipeline.entity.user.GroupStatus;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;

public final class UserCreatorUtils {

    private UserCreatorUtils() {

    }

    public static PipelineUser getPipelineUser() {
        return new PipelineUser();
    }

    public static PipelineUser getPipelineUser(final String name) {
        final PipelineUser pipelineUser = new PipelineUser();
        pipelineUser.setId(ID);
        pipelineUser.setUserName(name);
        return pipelineUser;
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
    public static PipelineUser getPipelineUser(final String name) {
        final PipelineUser pipelineUser = new PipelineUser();
        pipelineUser.setId(ID);
        pipelineUser.setUserName(name);
        return pipelineUser;
    }
}
