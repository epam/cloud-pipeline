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

import com.epam.pipeline.entity.cluster.pool.filter.instancefilter.PipelinePoolInstanceFilter;
import com.epam.pipeline.entity.cluster.pool.filter.instancefilter.PoolInstanceFilterOperator;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


public class PipelineFilterHandlerTest {

    private static final long PIPELINE_ID_1 = 1L;
    private static final long PIPELINE_ID_2 = 2L;
    private final PipelineFilterHandler handler = new PipelineFilterHandler();

    @Test
    public void shouldPassEqualFilterWithMatchingId() {
        final PipelineRun run = getRunWithPipelineId(PIPELINE_ID_1);
        final PipelinePoolInstanceFilter filter =
                getFilter(PoolInstanceFilterOperator.EQUAL, PIPELINE_ID_1);
        assertTrue(handler.matches(filter, run));
    }

    @Test
    public void shouldFailEqualFilterWithNonMatchingId() {
        final PipelineRun run = getRunWithPipelineId(PIPELINE_ID_1);
        final PipelinePoolInstanceFilter filter =
                getFilter(PoolInstanceFilterOperator.EQUAL, PIPELINE_ID_2);
        assertFalse(handler.matches(filter, run));
    }

    @Test
    public void shouldPassNotEqualFilterWithNotMatchingId() {
        final PipelineRun run = getRunWithPipelineId(PIPELINE_ID_2);
        final PipelinePoolInstanceFilter filter =
                getFilter(PoolInstanceFilterOperator.NOT_EQUAL, PIPELINE_ID_1);
        assertTrue(handler.matches(filter, run));
    }

    @Test
    public void shouldFailNotEqualFilterWithMatchingId() {
        final PipelineRun run = getRunWithPipelineId(PIPELINE_ID_1);
        final PipelinePoolInstanceFilter filter =
                getFilter(PoolInstanceFilterOperator.NOT_EQUAL, PIPELINE_ID_1);
        assertFalse(handler.matches(filter, run));
    }

    @Test
    public void shouldPassEmptyFilterWithoutId() {
        final PipelineRun run = getRunWithPipelineId(null);
        final PipelinePoolInstanceFilter filter =
                getFilter(PoolInstanceFilterOperator.EMPTY, null);
        assertTrue(handler.matches(filter, run));
    }

    @Test
    public void shouldFailEmptyFilterWithId() {
        final PipelineRun run = getRunWithPipelineId(PIPELINE_ID_1);
        final PipelinePoolInstanceFilter filter =
                getFilter(PoolInstanceFilterOperator.EMPTY, null);
        assertFalse(handler.matches(filter, run));
    }

    @Test
    public void shouldFailNotEmptyFilterWithoutId() {
        final PipelineRun run = getRunWithPipelineId(null);
        final PipelinePoolInstanceFilter filter =
                getFilter(PoolInstanceFilterOperator.NOT_EMPTY, null);
        assertFalse(handler.matches(filter, run));
    }

    @Test
    public void shouldPassNotEmptyFilterWithId() {
        final PipelineRun run = getRunWithPipelineId(PIPELINE_ID_1);
        final PipelinePoolInstanceFilter filter =
                getFilter(PoolInstanceFilterOperator.NOT_EMPTY, null);
        assertTrue(handler.matches(filter, run));
    }

    private PipelineRun getRunWithPipelineId(final Long pipelineId) {
        final PipelineRun run = new PipelineRun();
        run.setPipelineId(pipelineId);
        return run;
    }

    private PipelinePoolInstanceFilter getFilter(final PoolInstanceFilterOperator operator,
                                                 final Long value) {
        final PipelinePoolInstanceFilter filter = new PipelinePoolInstanceFilter();
        filter.setOperator(operator);
        filter.setValue(value);
        return filter;
    }
}