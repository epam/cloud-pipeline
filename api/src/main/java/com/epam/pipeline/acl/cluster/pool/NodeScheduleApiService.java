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

package com.epam.pipeline.acl.cluster.pool;

import com.epam.pipeline.controller.vo.cluster.pool.NodeScheduleVO;
import com.epam.pipeline.entity.cluster.pool.NodeSchedule;
import com.epam.pipeline.manager.cluster.pool.NodeScheduleManager;
import com.epam.pipeline.security.acl.AclExpressions;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NodeScheduleApiService {

    private final NodeScheduleManager nodeScheduleManager;

    @PreAuthorize(AclExpressions.ADMIN_ONLY)
    public NodeSchedule createOrUpdate(final NodeScheduleVO vo) {
        return nodeScheduleManager.createOrUpdate(vo);
    }

    @PreAuthorize(AclExpressions.ADMIN_ONLY)
    public NodeSchedule load(final Long scheduleId) {
        return nodeScheduleManager.load(scheduleId);
    }

    @PreAuthorize(AclExpressions.ADMIN_ONLY)
    public List<NodeSchedule> loadAll() {
        return nodeScheduleManager.loadAll();
    }

    @PreAuthorize(AclExpressions.ADMIN_ONLY)
    public NodeSchedule delete(final Long scheduleId) {
        return nodeScheduleManager.delete(scheduleId);
    }
}
