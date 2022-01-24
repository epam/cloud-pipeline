/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.cluster.pool;

import com.epam.pipeline.entity.cluster.pool.NodePoolUsage;
import com.epam.pipeline.entity.cluster.pool.NodePoolUsageEntity;
import com.epam.pipeline.entity.cluster.pool.NodePoolUsageRecord;
import com.epam.pipeline.entity.cluster.pool.NodePoolUsageRecordEntity;
import com.epam.pipeline.repository.cluster.pool.NodePoolUsageRepository;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.mapper.cluster.pool.NodePoolUsageMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NodePoolUsageService {
    private final NodePoolUsageRepository nodePoolUsageRepository;
    private final NodePoolUsageMapper nodePoolUsageMapper;

    @Transactional
    public NodePoolUsage save(final List<NodePoolUsageRecord> records) {
        final NodePoolUsageEntity entity = new NodePoolUsageEntity();
        entity.setLogDate(DateUtils.nowUTC());
        entity.setRecords(prepareRecords(records, entity));
        nodePoolUsageRepository.save(entity);

        return nodePoolUsageMapper.toVO(entity);
    }

    @Transactional
    public boolean deleteExpired(final LocalDate date) {
        nodePoolUsageRepository.deleteByLogDateLessThan(date.atStartOfDay());
        return true;
    }

    private List<NodePoolUsageRecordEntity> prepareRecords(final List<NodePoolUsageRecord> records,
                                                           final NodePoolUsageEntity entity) {
        if (CollectionUtils.isEmpty(records)) {
            return null;
        }
        return records.stream()
                .map(record -> prepareRecord(record, entity))
                .collect(Collectors.toList());
    }

    private NodePoolUsageRecordEntity prepareRecord(final NodePoolUsageRecord record,
                                                    final NodePoolUsageEntity entity) {
        final NodePoolUsageRecordEntity recordEntity = nodePoolUsageMapper.toRecordEntity(record);
        recordEntity.setId(null);
        recordEntity.setRecord(entity);
        return recordEntity;
    }
}
