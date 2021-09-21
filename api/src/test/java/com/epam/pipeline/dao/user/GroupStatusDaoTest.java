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

package com.epam.pipeline.dao.user;

import com.epam.pipeline.entity.user.GroupStatus;
import com.epam.pipeline.test.jdbc.AbstractJdbcTest;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

@Transactional
public class GroupStatusDaoTest extends AbstractJdbcTest {

    private static final String TEST_GROUP_1 = "test_group_1";
    private static final String TEST_GROUP_2 = "test_group_2";

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

    @Test
    public void testGroupStatusLoadAll() {
        final GroupStatus groupStatus1 = new GroupStatus(TEST_GROUP_1, false);
        final GroupStatus groupStatus2 = new GroupStatus(TEST_GROUP_2, false);
        groupStatusDao.upsertGroupBlockingStatusQuery(groupStatus1);
        groupStatusDao.upsertGroupBlockingStatusQuery(groupStatus2);
        final Map<String, Boolean> loadedStatuses = groupStatusDao.loadAllGroupsBlockingStatuses()
            .stream()
            .collect(Collectors.toMap(GroupStatus::getGroupName, GroupStatus::isBlocked));
        Assert.assertEquals(2, loadedStatuses.size());
        Assert.assertEquals(groupStatus1.isBlocked(), loadedStatuses.get(groupStatus1.getGroupName()));
        Assert.assertEquals(groupStatus2.isBlocked(), loadedStatuses.get(groupStatus2.getGroupName()));

    }

    private GroupStatus loadGroupStatus(final String groupName) {
        return groupStatusDao.loadGroupsBlockingStatus(Collections.singletonList(groupName))
                             .stream()
                             .findFirst()
                             .orElse(null);
    }
}
