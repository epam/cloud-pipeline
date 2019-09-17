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

package com.epam.pipeline.dao.user;

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.entity.user.GroupStatus;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

@Transactional
public class GroupStatusDaoTest extends AbstractSpringTest {

    private static final String TEST_GROUP_1 = "test_group_1";

    @Autowired
    private GroupStatusDao groupStatusDao;

    @Test
    public void testGroupStatusCRUD() {
        final GroupStatus groupStatusArgument = new GroupStatus(TEST_GROUP_1, false);
        final GroupStatus savedGroupStatus = groupStatusDao.upsertGroupBlockingStatusQuery(groupStatusArgument);
        Assert.assertEquals(TEST_GROUP_1, savedGroupStatus.getGroupName());
        Assert.assertFalse(savedGroupStatus.isBlocked());

        final GroupStatus loadedGroupStatus = loadGroupStatus(TEST_GROUP_1);
        Assert.assertEquals(savedGroupStatus.getGroupName(), loadedGroupStatus.getGroupName());
        Assert.assertEquals(savedGroupStatus.isBlocked(), loadedGroupStatus.isBlocked());

        final GroupStatus blockedGroupStatus = new GroupStatus(TEST_GROUP_1, true);
        final GroupStatus updatedGroupStatus = groupStatusDao.upsertGroupBlockingStatusQuery(blockedGroupStatus);
        Assert.assertEquals(blockedGroupStatus.getGroupName(), updatedGroupStatus.getGroupName());
        Assert.assertTrue(updatedGroupStatus.isBlocked());

        groupStatusDao.deleteGroupBlockingStatus(TEST_GROUP_1);
        Assert.assertNull(loadGroupStatus(TEST_GROUP_1));
    }

    private GroupStatus loadGroupStatus(final String groupName) {
        return groupStatusDao.loadGroupsBlockingStatus(Collections.singletonList(groupName))
                             .stream()
                             .findFirst()
                             .orElse(null);
    }
}
