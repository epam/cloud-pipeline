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

package com.epam.pipeline.mapper;

import java.time.Duration;
import java.util.Map;

import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.utils.DateUtils;

public final class PipelineRunMapper {
    private static final int SECONDS_IN_MINUTE = 60;
    private PipelineRunMapper() {
    }

    /**
     * Mapping a {@code PipelineRun}
     * @param run {@code PipelineRun}
     * @param threshold
     * @return a mapped {@code PipelineRun}, {@code Map} key - a field name of {@code PipelineRun},
     * {@code Map} value - a {@code PipelineRun} field value
     */
    public static Map<String, Object> map(PipelineRun run, Long threshold) {
        JsonMapper mapper = new JsonMapper();
        Map<String, Object> params = mapper.convertValue(run, mapper.getTypeFactory()
            .constructParametricType(Map.class, String.class, Object.class));

        if (threshold != null) {
            params.put("timeThreshold", threshold / SECONDS_IN_MINUTE);
        }

        params.put("runningTime", Duration.between(run.getStartDate().toInstant(), DateUtils.now()
            .toInstant()).abs().getSeconds() / SECONDS_IN_MINUTE);

        return params;
    }
}
