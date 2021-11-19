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
import com.epam.pipeline.entity.cluster.pool.filter.instancefilter.PoolInstanceFilterOperator;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DockerImageFilterHandlerTest {

    private static final String CENTOS_IMAGE = "library/centos:7";
    private static final String UBUNTU_IMAGE = "library/ubuntu:18";
    private final DockerImageFilterHandler handler = new DockerImageFilterHandler();

    @Test
    public void shouldPassEqualFilterWithMatchingDocker() {
        final PipelineRun run = getRunWithDocker(CENTOS_IMAGE);
        final DockerPoolInstanceFilter filter = getFilter(PoolInstanceFilterOperator.EQUAL, CENTOS_IMAGE);
        assertTrue(handler.matches(filter, run));
    }

    @Test
    public void shouldPassNotEqualFilterWithNotMatchingDocker() {
        final PipelineRun run = getRunWithDocker(CENTOS_IMAGE);
        final DockerPoolInstanceFilter filter = getFilter(PoolInstanceFilterOperator.NOT_EQUAL, UBUNTU_IMAGE);
        assertTrue(handler.matches(filter, run));
    }

    @Test
    public void shouldFailEqualFilterWithNotMatchingDocker() {
        final PipelineRun run = getRunWithDocker(UBUNTU_IMAGE);
        final DockerPoolInstanceFilter filter = getFilter(PoolInstanceFilterOperator.EQUAL, CENTOS_IMAGE);
        assertFalse(handler.matches(filter, run));
    }

    @Test
    public void shouldFailNotEqualFilterWithMatchingDocker() {
        final PipelineRun run = getRunWithDocker(CENTOS_IMAGE);
        final DockerPoolInstanceFilter filter = getFilter(PoolInstanceFilterOperator.NOT_EQUAL, CENTOS_IMAGE);
        assertFalse(handler.matches(filter, run));
    }

    private PipelineRun getRunWithDocker(final String centosImage) {
        final PipelineRun run = new PipelineRun();
        run.setActualDockerImage(centosImage);
        return run;
    }

    public DockerPoolInstanceFilter getFilter(final PoolInstanceFilterOperator equal,
                                              final String dockerImage) {
        final DockerPoolInstanceFilter filter = new DockerPoolInstanceFilter();
        filter.setOperator(equal);
        filter.setValue(dockerImage);
        return filter;
    }
}