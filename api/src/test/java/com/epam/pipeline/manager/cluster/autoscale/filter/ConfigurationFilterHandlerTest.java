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

import com.epam.pipeline.entity.cluster.pool.filter.instancefilter.ConfigurationPoolInstanceFilter;
import com.epam.pipeline.entity.cluster.pool.filter.instancefilter.PoolInstanceFilterOperator;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConfigurationFilterHandlerTest {

    private static final long CONFIGURATION_ID_1 = 1L;
    private static final long CONFIGURATION_ID_2 = 2L;
    private final ConfigurationFilterHandler handler = new ConfigurationFilterHandler();

    @Test
    public void shouldPassEqualFilterWithMatchingId() {
        final PipelineRun run = getRunWithConfigurationId(CONFIGURATION_ID_1);
        final ConfigurationPoolInstanceFilter filter =
                getFilter(PoolInstanceFilterOperator.EQUAL, CONFIGURATION_ID_1);
        assertTrue(handler.matches(filter, run));
    }

    @Test
    public void shouldFailEqualFilterWithNonMatchingId() {
        final PipelineRun run = getRunWithConfigurationId(CONFIGURATION_ID_1);
        final ConfigurationPoolInstanceFilter filter =
                getFilter(PoolInstanceFilterOperator.EQUAL, CONFIGURATION_ID_2);
        assertFalse(handler.matches(filter, run));
    }

    @Test
    public void shouldPassNotEqualFilterWithNotMatchingId() {
        final PipelineRun run = getRunWithConfigurationId(CONFIGURATION_ID_2);
        final ConfigurationPoolInstanceFilter filter =
                getFilter(PoolInstanceFilterOperator.NOT_EQUAL, CONFIGURATION_ID_1);
        assertTrue(handler.matches(filter, run));
    }

    @Test
    public void shouldFailNotEqualFilterWithMatchingId() {
        final PipelineRun run = getRunWithConfigurationId(CONFIGURATION_ID_1);
        final ConfigurationPoolInstanceFilter filter =
                getFilter(PoolInstanceFilterOperator.NOT_EQUAL, CONFIGURATION_ID_1);
        assertFalse(handler.matches(filter, run));
    }

    @Test
    public void shouldPassEmptyFilterWithoutId() {
        final PipelineRun run = getRunWithConfigurationId(null);
        final ConfigurationPoolInstanceFilter filter =
                getFilter(PoolInstanceFilterOperator.EMPTY, null);
        assertTrue(handler.matches(filter, run));
    }

    @Test
    public void shouldFailEmptyFilterWithId() {
        final PipelineRun run = getRunWithConfigurationId(CONFIGURATION_ID_1);
        final ConfigurationPoolInstanceFilter filter =
                getFilter(PoolInstanceFilterOperator.EMPTY, null);
        assertFalse(handler.matches(filter, run));
    }

    @Test
    public void shouldFailNotEmptyFilterWithoutId() {
        final PipelineRun run = getRunWithConfigurationId(null);
        final ConfigurationPoolInstanceFilter filter =
                getFilter(PoolInstanceFilterOperator.NOT_EMPTY, null);
        assertFalse(handler.matches(filter, run));
    }

    @Test
    public void shouldPassNotEmptyFilterWithId() {
        final PipelineRun run = getRunWithConfigurationId(CONFIGURATION_ID_1);
        final ConfigurationPoolInstanceFilter filter =
                getFilter(PoolInstanceFilterOperator.NOT_EMPTY, null);
        assertTrue(handler.matches(filter, run));
    }

    private PipelineRun getRunWithConfigurationId(final Long configurationId) {
        final PipelineRun run = new PipelineRun();
        run.setConfigurationId(configurationId);
        return run;
    }

    private ConfigurationPoolInstanceFilter getFilter(final PoolInstanceFilterOperator operator,
                                                      final Long value) {
        final ConfigurationPoolInstanceFilter filter = new ConfigurationPoolInstanceFilter();
        filter.setOperator(operator);
        filter.setValue(value);
        return filter;
    }
}