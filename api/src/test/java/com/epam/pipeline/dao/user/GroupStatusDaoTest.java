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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

@Transactional
public class GroupStatusDaoTest extends AbstractJdbcTest {

    private static final String TEST_GROUP_1 = "test_group_1";
    private static final String TEST_GROUP_2 = "test_group_2";

    @Autowired
    private GroupStatusDao groupStatusDao;

    @Test
    public void testGroupStatusCRUD() {
        final GroupStatus groupStatusArgument = new GroupStatus(TEST_GROUP_1, false, null);
        final GroupStatus savedGroupStatus = groupStatusDao.upsertGroupBlockingStatusQuery(groupStatusArgument);
        assertEquals(TEST_GROUP_1, savedGroupStatus.groupName());
        assertFalse(savedGroupStatus.blocked());

        final GroupStatus loadedGroupStatus = loadGroupStatus(TEST_GROUP_1);
        assertEquals(savedGroupStatus.groupName(), loadedGroupStatus.groupName());
        assertEquals(savedGroupStatus.blocked(), loadedGroupStatus.blocked());

        final GroupStatus blockedGroupStatus = new GroupStatus(TEST_GROUP_1, true, null);
        final GroupStatus updatedGroupStatus = groupStatusDao.upsertGroupBlockingStatusQuery(blockedGroupStatus);
        assertEquals(blockedGroupStatus.groupName(), updatedGroupStatus.groupName());
        assertTrue(updatedGroupStatus.blocked());

        groupStatusDao.deleteGroupBlockingStatus(TEST_GROUP_1);
        assertNull(loadGroupStatus(TEST_GROUP_1));
    }

    @Test
    public void testGroupStatusLoadAll() {
        final GroupStatus groupStatus1 = new GroupStatus(TEST_GROUP_1, false, null);
        final GroupStatus groupStatus2 = new GroupStatus(TEST_GROUP_2, false, null);
        groupStatusDao.upsertGroupBlockingStatusQuery(groupStatus1);
        groupStatusDao.upsertGroupBlockingStatusQuery(groupStatus2);
        final Map<String, Boolean> loadedStatuses = groupStatusDao.loadAllGroupsBlockingStatuses()
            .stream()
            .collect(Collectors.toMap(GroupStatus::groupName, GroupStatus::blocked));
        assertEquals(2, loadedStatuses.size());
        assertEquals(groupStatus1.blocked(), loadedStatuses.get(groupStatus1.groupName()));
        assertEquals(groupStatus2.blocked(), loadedStatuses.get(groupStatus2.groupName()));

    }

    private GroupStatus loadGroupStatus(final String groupName) {
        return groupStatusDao.loadGroupsBlockingStatus(Collections.singletonList(groupName))
                             .stream()
                             .findFirst()
                             .orElse(null);
    }
}
