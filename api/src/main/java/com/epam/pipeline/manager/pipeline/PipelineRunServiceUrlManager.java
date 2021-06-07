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

package com.epam.pipeline.manager.pipeline;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.vo.PipelineRunServiceUrlVO;
import com.epam.pipeline.entity.pipeline.run.PipelineRunServiceUrl;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.repository.run.PipelineRunServiceUrlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@Slf4j
@RequiredArgsConstructor
public class PipelineRunServiceUrlManager {

    private final MessageHelper messageHelper;
    private final PreferenceManager preferenceManager;
    private final PipelineRunServiceUrlRepository pipelineRunServiceUrlRepository;

    @Transactional(propagation = Propagation.REQUIRED)
    public PipelineRunServiceUrl update(final Long runId, final String region,
                                        final PipelineRunServiceUrlVO serviceUrl) {
        final String edgeRegion = getRegion(region);
        pipelineRunServiceUrlRepository.findByPipelineRunIdAndRegion(runId, edgeRegion)
                .ifPresent(pipelineRunServiceUrlRepository::delete);
        final PipelineRunServiceUrl pipelineRunServiceUrl = convertPipelineRunServiceUrl(runId, edgeRegion, serviceUrl);
        pipelineRunServiceUrlRepository.save(pipelineRunServiceUrl);
        return pipelineRunServiceUrl;
    }

    public Map<String, String> loadByRunId(final Long runId) {
        return StreamSupport.stream(pipelineRunServiceUrlRepository.findByPipelineRunId(runId).spliterator(), false)
                .collect(Collectors.toMap(PipelineRunServiceUrl::getRegion, PipelineRunServiceUrl::getServiceUrl));
    }

    public String loadByRunIdAndRegion(final Long runId, final String region) {
        return pipelineRunServiceUrlRepository.findByPipelineRunIdAndRegion(runId, region)
                .map(PipelineRunServiceUrl::getServiceUrl)
                .orElse(null);
    }

    private PipelineRunServiceUrl convertPipelineRunServiceUrl(final Long runId, final String region,
                                                               final PipelineRunServiceUrlVO serviceUrl) {
        final PipelineRunServiceUrl pipelineRunServiceUrl = new PipelineRunServiceUrl();
        pipelineRunServiceUrl.setPipelineRunId(runId);
        pipelineRunServiceUrl.setRegion(region);
        pipelineRunServiceUrl.setServiceUrl(serviceUrl.getServiceUrl());
        return pipelineRunServiceUrl;
    }

    private String getRegion(final String region) {
        final String resultRegion = StringUtils.isBlank(region)
                ? preferenceManager.getPreference(SystemPreferences.DEFAULT_EDGE_REGION)
                : region;
        Assert.state(StringUtils.isNotBlank(resultRegion), "Could not find any edge region");
        return resultRegion;
    }
}
