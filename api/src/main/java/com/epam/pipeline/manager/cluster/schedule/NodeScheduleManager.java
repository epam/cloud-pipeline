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

package com.epam.pipeline.manager.cluster.schedule;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.vo.cluster.schedule.NodeScheduleVO;
import com.epam.pipeline.dao.cluster.schedule.NodeScheduleDao;
import com.epam.pipeline.entity.cluster.schedule.NodeSchedule;
import com.epam.pipeline.entity.cluster.schedule.ScheduleEntry;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.mapper.cluster.schedule.NodeScheduleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NodeScheduleManager {

    private final NodeScheduleDao nodeScheduleDao;
    private final NodeScheduleMapper nodeScheduleMapper;
    private final MessageHelper messageHelper;

    @Transactional
    public NodeSchedule createOrUpdate(final NodeScheduleVO vo) {
        return vo.getId() == null ? create(vo) : update(vo);
    }

    public NodeSchedule load(final Long scheduleId) {
        return nodeScheduleDao.find(scheduleId)
                .orElseThrow(() -> new IllegalArgumentException(
                        messageHelper.getMessage(MessageConstants.ERROR_NODE_SCHEDULE_NOT_FOUND, scheduleId)));
    }

    public List<NodeSchedule> loadAll() {
        return nodeScheduleDao.loadAll();
    }

    @Transactional
    public NodeSchedule delete(final Long scheduleId) {
        final NodeSchedule schedule = load(scheduleId);
        nodeScheduleDao.delete(scheduleId);
        return schedule;
    }

    private NodeSchedule create(final NodeScheduleVO vo) {
        validate(vo);
        final NodeSchedule schedule = nodeScheduleMapper.toEntity(vo);
        schedule.setCreated(DateUtils.nowUTC());
        return nodeScheduleDao.create(schedule);
    }

    private NodeSchedule update(final NodeScheduleVO vo) {
        load(vo.getId());
        validate(vo);
        return nodeScheduleDao.update(nodeScheduleMapper.toEntity(vo));
    }

    private void validate(final NodeScheduleVO vo) {
        Assert.notEmpty(vo.getScheduleEntries(),
                messageHelper.getMessage(MessageConstants.ERROR_NODE_SCHEDULE_MISSING_ENTRIES));
        vo.getScheduleEntries().forEach(this::validate);
    }

    private void validate(final ScheduleEntry scheduleEntry) {
        Assert.notNull(scheduleEntry.getFrom(),
                messageHelper.getMessage(MessageConstants.ERROR_NODE_SCHEDULE_MISSING_FROM));
        Assert.notNull(scheduleEntry.getFromTime(),
                messageHelper.getMessage(MessageConstants.ERROR_NODE_SCHEDULE_MISSING_FROM_TIME));
        Assert.notNull(scheduleEntry.getTo(),
                messageHelper.getMessage(MessageConstants.ERROR_NODE_SCHEDULE_MISSING_TO));
        Assert.notNull(scheduleEntry.getToTime(),
                messageHelper.getMessage(MessageConstants.ERROR_NODE_SCHEDULE_MISSING_TO_TIME));
    }
}
