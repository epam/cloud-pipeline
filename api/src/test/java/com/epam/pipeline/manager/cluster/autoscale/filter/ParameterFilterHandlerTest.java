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
import com.epam.pipeline.entity.cluster.pool.filter.instancefilter.PoolInstanceFilterOperator;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.run.parameter.PipelineRunParameter;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ParameterFilterHandlerTest {

    private static final String KEY_1 = "key1";
    private static final String KEY_2 = "key2";
    private static final String VAL_1 = "val1";
    private static final String VAL_2 = "val2";
    private final ParameterFilterHandler handler = new ParameterFilterHandler();

    @Test
    public void shouldPassEqualFilterWhenParameterValueMatches() {
        final ParameterPoolInstanceFilter filter =
                getFilter(PoolInstanceFilterOperator.EQUAL, Collections.singletonMap(KEY_1, VAL_1));
        final PipelineRun run = getRunWithParameters(
                new ImmutablePair<>(KEY_1, VAL_1),
                new ImmutablePair<>(KEY_2, VAL_2));
        assertTrue(handler.matches(filter, run));
    }

    @Test
    public void shouldFailEqualFilterWhenParameterValueNotMatches() {
        final ParameterPoolInstanceFilter filter =
                getFilter(PoolInstanceFilterOperator.EQUAL, Collections.singletonMap(KEY_1, VAL_2));
        final PipelineRun run = getRunWithParameters(Collections.singletonMap(KEY_1, VAL_1));
        assertFalse(handler.matches(filter, run));
    }

    @Test
    public void shouldFailEqualFilterWhenParameterNotPresent() {
        final ParameterPoolInstanceFilter filter =
                getFilter(PoolInstanceFilterOperator.EQUAL, Collections.singletonMap(KEY_2, VAL_2));
        final PipelineRun run = getRunWithParameters(Collections.singletonMap(KEY_1, VAL_1));
        assertFalse(handler.matches(filter, run));
    }

    @Test
    public void shouldPassNotEqualFilterWhenParameterNotPresent() {
        final ParameterPoolInstanceFilter filter =
                getFilter(PoolInstanceFilterOperator.NOT_EQUAL, Collections.singletonMap(KEY_2, VAL_2));
        final PipelineRun run = getRunWithParameters(Collections.singletonMap(KEY_1, VAL_1));
        assertTrue(handler.matches(filter, run));
    }

    @Test
    public void shouldPassNotEqualFilterWhenParameterValueNotMatches() {
        final ParameterPoolInstanceFilter filter =
                getFilter(PoolInstanceFilterOperator.NOT_EQUAL, Collections.singletonMap(KEY_2, VAL_2));
        final PipelineRun run = getRunWithParameters(
                new ImmutablePair<>(KEY_1, VAL_1),
                new ImmutablePair<>(KEY_2, VAL_1));
        assertTrue(handler.matches(filter, run));
    }

    @Test
    public void shouldFailNotEqualFilterWhenParameterValueMatches() {
        final ParameterPoolInstanceFilter filter =
                getFilter(PoolInstanceFilterOperator.NOT_EQUAL, Collections.singletonMap(KEY_1, VAL_1));
        final PipelineRun run = getRunWithParameters(Collections.singletonMap(KEY_1, VAL_1));
        assertFalse(handler.matches(filter, run));
    }

    @Test
    public void shouldPassEmptyFilterWhenParameterIsNotPresent() {
        final ParameterPoolInstanceFilter filter =
                getFilter(PoolInstanceFilterOperator.EMPTY, Collections.singletonMap(KEY_1, null));
        final PipelineRun run = getRunWithParameters(Collections.singletonMap(KEY_2, VAL_2));
        assertTrue(handler.matches(filter, run));
    }

    @Test
    public void shouldPassEmptyFilterWhenParameterHasNoValue() {
        final ParameterPoolInstanceFilter filter =
                getFilter(PoolInstanceFilterOperator.EMPTY, Collections.singletonMap(KEY_1, null));
        final PipelineRun run = getRunWithParameters(Collections.singletonMap(KEY_1, null));
        assertTrue(handler.matches(filter, run));
    }

    @Test
    public void shouldFailEmptyFilterWhenParameterHasValue() {
        final ParameterPoolInstanceFilter filter =
                getFilter(PoolInstanceFilterOperator.EMPTY, Collections.singletonMap(KEY_1, null));
        final PipelineRun run = getRunWithParameters(Collections.singletonMap(KEY_1, VAL_2));
        assertFalse(handler.matches(filter, run));
    }

    @Test
    public void shouldPassNotEmptyFilterWhenParameterIsPresent() {
        final ParameterPoolInstanceFilter filter =
                getFilter(PoolInstanceFilterOperator.NOT_EMPTY, Collections.singletonMap(KEY_1, null));
        final PipelineRun run = getRunWithParameters(Collections.singletonMap(KEY_1, VAL_1));
        assertTrue(handler.matches(filter, run));
    }

    @Test
    public void shouldPassNotEmptyFilterWhenParameterHasValue() {
        final ParameterPoolInstanceFilter filter =
                getFilter(PoolInstanceFilterOperator.NOT_EMPTY, Collections.singletonMap(KEY_1, null));
        final PipelineRun run = getRunWithParameters(Collections.singletonMap(KEY_1, VAL_1));
        assertTrue(handler.matches(filter, run));
    }

    @Test
    public void shouldFailNotEmptyFilterWhenParameterIsNotPresent() {
        final ParameterPoolInstanceFilter filter =
                getFilter(PoolInstanceFilterOperator.NOT_EMPTY, Collections.singletonMap(KEY_1, null));
        final PipelineRun run = getRunWithParameters(Collections.singletonMap(KEY_2, VAL_2));
        assertFalse(handler.matches(filter, run));
    }

    @Test
    public void shouldFailNotEmptyFilterWhenParameterHasNoValue() {
        final ParameterPoolInstanceFilter filter =
                getFilter(PoolInstanceFilterOperator.NOT_EMPTY, Collections.singletonMap(KEY_1, null));
        final PipelineRun run = getRunWithParameters(Collections.singletonMap(KEY_1, null));
        assertFalse(handler.matches(filter, run));
    }

    private PipelineRun getRunWithParameters(final Map<String, String> parameters) {
        final PipelineRun pipelineRun = new PipelineRun();
        pipelineRun.setPipelineRunParameters(parameters.entrySet()
                .stream()
                .map(entry -> new PipelineRunParameter(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList()));
        return pipelineRun;
    }

    private PipelineRun getRunWithParameters(final Pair<String, String>... parameters) {
        final PipelineRun pipelineRun = new PipelineRun();
        pipelineRun.setPipelineRunParameters(Arrays.stream(parameters)
                .map(entry -> new PipelineRunParameter(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList()));
        return pipelineRun;
    }

    private ParameterPoolInstanceFilter getFilter(final PoolInstanceFilterOperator operator,
                                                  final Map<String, String> values) {
        final ParameterPoolInstanceFilter filter = new ParameterPoolInstanceFilter();
        filter.setValue(values);
        filter.setOperator(operator);
        return filter;
    }
}