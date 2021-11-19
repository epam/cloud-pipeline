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

import com.epam.pipeline.entity.cluster.pool.filter.instancefilter.PoolInstanceFilterType;
import com.epam.pipeline.entity.cluster.pool.filter.instancefilter.RunOwnerPoolInstanceFilter;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RunOwnerFilterHandler implements PoolFilterHandler<String, RunOwnerPoolInstanceFilter> {

    @Override
    public boolean matches(final RunOwnerPoolInstanceFilter filter, final PipelineRun run) {
        final String owner = run.getOwner();
        log.debug("Matching run {} owner {} to filter {}.", run.getId(), owner, filter);
        return filter.evaluate(owner);
    }

    @Override
    public PoolInstanceFilterType type() {
        return PoolInstanceFilterType.RUN_OWNER;
    }
}
