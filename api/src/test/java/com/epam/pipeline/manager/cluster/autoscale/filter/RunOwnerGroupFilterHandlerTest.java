/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.manager.cluster.autoscale.filter;

import com.epam.pipeline.entity.cluster.pool.filter.instancefilter.PoolInstanceFilterOperator;
import com.epam.pipeline.entity.cluster.pool.filter.instancefilter.RunOwnerGroupPoolInstanceFilter;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.manager.user.UserManager;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.stream.Collectors;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;

public class RunOwnerGroupFilterHandlerTest {

    private static final String USER_NAME = "USER";
    private static final String GROUP_1 = "GROUP1";
    private static final String GROUP_2 = "GROUP2";
    private static final String GROUP_3 = "group3";
    private static final String ROLE_USER = "ROLE_USER";
    private static final String ROLE_MANAGER = "ROLE_MANAGER";

    private final UserManager userManager = Mockito.mock(UserManager.class);
    private final RunOwnerGroupFilterHandler handler = new RunOwnerGroupFilterHandler(userManager);

    @Test
    public void shouldPassEqualFilterWithMatchingGroup() {
        initUserWithGroups(GROUP_1, GROUP_2);
        final PipelineRun pipelineRun = getRunWithOwner();
        final RunOwnerGroupPoolInstanceFilter filter = getFilter(PoolInstanceFilterOperator.EQUAL, GROUP_1);
        assertTrue(handler.matches(filter, pipelineRun));
    }

    @Test
    public void shouldPassEqualFilterWithMatchingRole() {
        initUserWithRoles(ROLE_USER, ROLE_MANAGER);
        final PipelineRun pipelineRun = getRunWithOwner();
        final RunOwnerGroupPoolInstanceFilter filter = getFilter(PoolInstanceFilterOperator.EQUAL, ROLE_USER);
        assertTrue(handler.matches(filter, pipelineRun));
    }

    @Test
    public void shouldFailEqualFilterWithoutMatchingGroup() {
        initUserWithGroups(GROUP_1, GROUP_2);
        final PipelineRun pipelineRun = getRunWithOwner();
        final RunOwnerGroupPoolInstanceFilter filter = getFilter(PoolInstanceFilterOperator.EQUAL, GROUP_3);
        assertFalse(handler.matches(filter, pipelineRun));
    }

    @Test
    public void shouldPassNotEqualFilterWithoutMatchingGroup() {
        initUserWithGroups(GROUP_1, GROUP_2);
        final PipelineRun pipelineRun = getRunWithOwner();
        final RunOwnerGroupPoolInstanceFilter filter = getFilter(PoolInstanceFilterOperator.NOT_EQUAL, GROUP_3);
        assertTrue(handler.matches(filter, pipelineRun));
    }

    @Test
    public void shouldFailNotEqualFilterWithMatchingGroup() {
        initUserWithGroups(GROUP_1, GROUP_2);
        final PipelineRun pipelineRun = getRunWithOwner();
        final RunOwnerGroupPoolInstanceFilter filter = getFilter(PoolInstanceFilterOperator.NOT_EQUAL, GROUP_2);
        assertFalse(handler.matches(filter, pipelineRun));
    }

    @Test
    public void shouldFailNotEqualFilterWithMatchingRole() {
        initUserWithRoles(ROLE_USER, ROLE_MANAGER);
        final PipelineRun pipelineRun = getRunWithOwner();
        final RunOwnerGroupPoolInstanceFilter filter =
                getFilter(PoolInstanceFilterOperator.NOT_EQUAL, ROLE_USER);
        assertFalse(handler.matches(filter, pipelineRun));
    }

    private void initUserWithRoles(final String... roles) {
        final PipelineUser user = new PipelineUser();
        user.setUserName(USER_NAME);
        user.setRoles(Arrays.stream(roles).map(Role::new).collect(Collectors.toList()));
        doReturn(user).when(userManager).loadUserByName(eq(USER_NAME));
    }

    private PipelineRun getRunWithOwner() {
        final PipelineRun pipelineRun = new PipelineRun();
        pipelineRun.setOwner(USER_NAME);
        return pipelineRun;
    }

    private void initUserWithGroups(String... groups) {
        final PipelineUser user = new PipelineUser();
        user.setUserName(USER_NAME);
        user.setGroups(Arrays.asList(groups));
        doReturn(user).when(userManager).loadUserByName(eq(USER_NAME));
    }

    private RunOwnerGroupPoolInstanceFilter getFilter(final PoolInstanceFilterOperator equal,
                                                      final String value) {
        final RunOwnerGroupPoolInstanceFilter filter = new RunOwnerGroupPoolInstanceFilter();
        filter.setOperator(equal);
        filter.setValue(value.toLowerCase());
        return filter;
    }
}