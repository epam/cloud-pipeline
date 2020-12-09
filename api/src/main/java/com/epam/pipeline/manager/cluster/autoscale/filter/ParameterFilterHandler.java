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

package com.epam.pipeline.manager.cluster.autoscale.filter;

import com.epam.pipeline.entity.cluster.pool.filter.instancefilter.ParameterPoolInstanceFilter;
import com.epam.pipeline.entity.cluster.pool.filter.instancefilter.PoolInstanceFilterType;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class ParameterFilterHandler implements
        PoolFilterHandler<Map<String, String>, ParameterPoolInstanceFilter> {
    @Override
    public boolean matches(final ParameterPoolInstanceFilter filter, final PipelineRun run) {
        final Map<String, String> params = ListUtils.emptyIfNull(run.getPipelineRunParameters())
                .stream()
                .collect(HashMap::new, (m, v) -> m.put(v.getName(), v.getValue()), HashMap::putAll);
        log.debug("Matching run {} parameters {} to filter {}.", run.getId(), params, filter);
        return filter.evaluate(params);
    }

    @Override
    public PoolInstanceFilterType type() {
        return PoolInstanceFilterType.RUN_PARAMETER;
    }
}
