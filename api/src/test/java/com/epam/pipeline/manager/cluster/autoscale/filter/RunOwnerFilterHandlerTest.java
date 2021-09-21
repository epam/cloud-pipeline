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

import com.epam.pipeline.entity.cluster.pool.filter.instancefilter.PoolInstanceFilterOperator;
import com.epam.pipeline.entity.cluster.pool.filter.instancefilter.RunOwnerPoolInstanceFilter;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RunOwnerFilterHandlerTest {

    public static final String USER = "USER";
    private static final String FILTER_USER = "pipe_admin";
    private static final String PIPE_ADMIN = "PIPE_ADMIN";
    private final RunOwnerFilterHandler handler = new RunOwnerFilterHandler();

    @Test
    public void shouldPassEqualFilterWithMatchingOwner() {
        final RunOwnerPoolInstanceFilter filter = getFilter(PoolInstanceFilterOperator.EQUAL);
        final PipelineRun run = getRunWithOwner(PIPE_ADMIN);
        assertTrue(handler.matches(filter, run));
    }

    @Test
    public void shouldFailNotEqualFilterWithMatchingOwner() {
        final RunOwnerPoolInstanceFilter filter = getFilter(PoolInstanceFilterOperator.NOT_EQUAL);
        final PipelineRun run = getRunWithOwner(PIPE_ADMIN);
        assertFalse(handler.matches(filter, run));
    }

    @Test
    public void shouldFailEqualFilterWithNonMatchingOwner() {
        final RunOwnerPoolInstanceFilter filter = getFilter(PoolInstanceFilterOperator.EQUAL);
        final PipelineRun run = getRunWithOwner("USER");
        assertFalse(handler.matches(filter, run));
    }

    @Test
    public void shouldPassNotEqualFilterWithNonMatchingOwner() {
        final RunOwnerPoolInstanceFilter filter = getFilter(PoolInstanceFilterOperator.NOT_EQUAL);
        final PipelineRun run = getRunWithOwner(USER);
        assertTrue(handler.matches(filter, run));
    }

    private RunOwnerPoolInstanceFilter getFilter(final PoolInstanceFilterOperator equal) {
        final RunOwnerPoolInstanceFilter filter = new RunOwnerPoolInstanceFilter();
        filter.setValue(FILTER_USER);
        filter.setOperator(equal);
        return filter;
    }

    private PipelineRun getRunWithOwner(final String pipeAdmin) {
        final PipelineRun run = new PipelineRun();
        run.setOwner(pipeAdmin);
        return run;
    }
}