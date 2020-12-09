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

import com.epam.pipeline.entity.cluster.pool.filter.instancefilter.DockerPoolInstanceFilter;
import com.epam.pipeline.entity.cluster.pool.filter.instancefilter.PoolInstanceFilterType;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class DockerImageFilterHandler implements PoolFilterHandler<String, DockerPoolInstanceFilter> {

    @Override
    public boolean matches(final DockerPoolInstanceFilter filter, final PipelineRun run) {
        final String dockerImage = run.getActualDockerImage();
        log.debug("Matching run {} docker {} to filter {}.", run.getId(), dockerImage, filter);
        return filter.evaluate(dockerImage);
    }

    @Override
    public PoolInstanceFilterType type() {
        return PoolInstanceFilterType.DOCKER_IMAGE;
    }
}
