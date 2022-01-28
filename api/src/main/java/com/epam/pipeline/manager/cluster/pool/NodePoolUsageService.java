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
import com.epam.pipeline.repository.cluster.pool.NodePoolUsageRepository;
import com.epam.pipeline.mapper.cluster.pool.NodePoolUsageMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.ListUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NodePoolUsageService {
    private final NodePoolUsageRepository nodePoolUsageRepository;
    private final NodePoolUsageMapper nodePoolUsageMapper;

    public List<NodePoolUsage> getByPeriod(final LocalDateTime from, final LocalDateTime to) {
        return ListUtils.emptyIfNull(nodePoolUsageRepository
                .findByLogDateGreaterThanAndLogDateLessThan(from, to)).stream()
                .map(nodePoolUsageMapper::toVO)
                .collect(Collectors.toList());
    }

    @Transactional
    public List<NodePoolUsage> save(final List<NodePoolUsage> records) {
        return records.stream()
                .map(nodePoolUsageMapper::toEntity)
                .peek(nodePoolUsageRepository::save)
                .map(nodePoolUsageMapper::toVO)
                .collect(Collectors.toList());
    }

    @Transactional
    public boolean deleteExpired(final LocalDate date) {
        nodePoolUsageRepository.deleteByLogDateLessThan(date.atStartOfDay());
        return true;
    }
}
