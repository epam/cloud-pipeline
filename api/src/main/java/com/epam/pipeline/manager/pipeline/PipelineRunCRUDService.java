/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.pipeline;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.pipeline.PipelineRunDao;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

//TODO: Move all CRUD and DB persistence methods from PipelineRunManager to this class
@Service
@RequiredArgsConstructor
public class PipelineRunCRUDService {

    private final PipelineRunDao pipelineRunDao;
    private final MessageHelper messageHelper;

    @Transactional(propagation = Propagation.REQUIRED)
    public PipelineRun updateRunStatus(PipelineRun run) {
        updatePrettyUrlForFinishedRun(run);
        pipelineRunDao.updateRunStatus(run);
        return run;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void updatePrettyUrlForFinishedRun(PipelineRun run) {
        if (run.getStatus().isFinal() && StringUtils.isNotBlank(run.getPrettyUrl())) {
            run.setPrettyUrl(null);
            pipelineRunDao.updateRun(run);
        }
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void updateKubernetesService(final PipelineRun run, final boolean enable) {
        run.setKubeServiceEnabled(enable);
        pipelineRunDao.updateRun(run);
    }

    public List<PipelineRun> loadRunsByIds(final List<Long> runIds) {
        if (CollectionUtils.isEmpty(runIds)) {
            return Collections.emptyList();
        }
        return pipelineRunDao.loadRunByIdIn(runIds);
    }

    public PipelineRun loadRunById(final Long id) {
        return findRunByRunId(id).orElseThrow(() -> new IllegalArgumentException(messageHelper.getMessage(
                MessageConstants.ERROR_RUN_PIPELINES_NOT_FOUND, id)));
    }

    public Optional<PipelineRun> findRunByRunId(final Long id) {
        return Optional.ofNullable(pipelineRunDao.loadPipelineRun(id));
    }

    public List<PipelineRun> loadRunsForNodeName(final String nodeName) {
        return pipelineRunDao.loadRunsByNodeName(nodeName);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void updateClusterPrices(final Collection<PipelineRun> runs) {
        pipelineRunDao.batchUpdateClusterPrices(CollectionUtils.emptyIfNull(runs).stream()
                .filter(run -> Objects.nonNull(run.getWorkersPrice()))
                .collect(Collectors.toList()));
    }

    public Map<Long, List<PipelineRun>> loadRunsByParentRuns(final Collection<Long> parents) {
        return CollectionUtils.emptyIfNull(pipelineRunDao.loadRunsByParentRuns(parents)).stream()
                .collect(Collectors.groupingBy(PipelineRun::getParentRunId));
    }

    public List<PipelineRun> loadRunsByPoolId(final Long poolId) {
        return pipelineRunDao.loadRunsByPoolId(poolId);
    }
}
