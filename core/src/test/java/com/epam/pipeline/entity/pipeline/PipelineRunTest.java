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

package com.epam.pipeline.entity.pipeline;

import com.epam.pipeline.entity.configuration.PipeConfValueVO;
import com.epam.pipeline.entity.pipeline.run.parameter.PipelineRunParameter;
import org.junit.Test;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

public class PipelineRunTest {

    private static final String PARAM1_NAME = "param1";
    private static final String PARAM1_TYPE = "input";
    private static final String PARAM1_VALUE = "value1";

    private static final String PARAM2_NAME = "param2";
    private static final String PARAM2_TYPE = "output";
    private static final String PARAM2_VALUE = "value2";

    @Test
    public void shouldReturnValidStringWithoutType() {
        PipelineRun pipelineRun = new PipelineRun();
        Map<String, PipeConfValueVO> params = new HashMap<>();
        params.put(PARAM1_NAME, new PipeConfValueVO(PARAM1_VALUE, null));
        params.put(PARAM2_NAME, new PipeConfValueVO(PARAM2_VALUE, null));
        pipelineRun.convertParamsToString(params);

        assertThat(pipelineRun.getParams(),
                is(glueParams(glueParam(PARAM1_NAME, PARAM1_VALUE), glueParam(PARAM2_NAME, PARAM2_VALUE))));
    }

    @Test
    public void shouldReturnValidStringWithType() {
        PipelineRun pipelineRun = new PipelineRun();
        Map<String, PipeConfValueVO> params = new HashMap<>();
        params.put(PARAM1_NAME, new PipeConfValueVO(PARAM1_VALUE, PARAM1_TYPE));
        params.put(PARAM2_NAME, new PipeConfValueVO(PARAM2_VALUE, PARAM2_TYPE));
        pipelineRun.convertParamsToString(params);

        assertThat(pipelineRun.getParams(),
                is(glueParams(glueParam(PARAM1_NAME, PARAM1_VALUE, PARAM1_TYPE),
                        glueParam(PARAM2_NAME, PARAM2_VALUE, PARAM2_TYPE))));
    }

    @Test
    public void shouldParseParamWithTypeValueParam() {
        PipelineRun pipelineRun = new PipelineRun();
        pipelineRun.setParams(glueParams(glueParam(PARAM1_NAME, PARAM1_VALUE, PARAM1_TYPE),
                glueParam(PARAM2_NAME, PARAM2_VALUE, PARAM2_TYPE)));
        pipelineRun.parseParameters();
        List<PipelineRunParameter> pipelineRunParameters = pipelineRun.getPipelineRunParameters();
        assertThat(pipelineRunParameters.size(), is(2));

        assertThat(pipelineRunParameters.get(0).getName(), is(PARAM1_NAME));
        assertThat(pipelineRunParameters.get(0).getValue(), is(PARAM1_VALUE));
        assertThat(pipelineRunParameters.get(0).getType(), is(PARAM1_TYPE));

        assertThat(pipelineRunParameters.get(1).getName(), is(PARAM2_NAME));
        assertThat(pipelineRunParameters.get(1).getValue(), is(PARAM2_VALUE));
        assertThat(pipelineRunParameters.get(1).getType(), is(PARAM2_TYPE));
    }

    @Test
    public void shouldParseSingleValueParam() {
        PipelineRun pipelineRun = new PipelineRun();
        pipelineRun.setParams(glueParam(PARAM1_NAME, PARAM1_VALUE));
        pipelineRun.parseParameters();
        List<PipelineRunParameter> pipelineRunParameters = pipelineRun.getPipelineRunParameters();
        assertThat(pipelineRunParameters.size(), is(1));
        assertThat(pipelineRunParameters.get(0).getName(), is(PARAM1_NAME));
        assertThat(pipelineRunParameters.get(0).getValue(), is(PARAM1_VALUE));
    }

    @Test
    public void shouldParseSeveralParams() {
        PipelineRun pipelineRun = new PipelineRun();
        pipelineRun.setParams(glueParams(glueParam(PARAM1_NAME, PARAM1_VALUE), glueParam(PARAM2_NAME, PARAM2_VALUE)));
        pipelineRun.parseParameters();
        List<PipelineRunParameter> pipelineRunParameters = pipelineRun.getPipelineRunParameters();
        assertThat(pipelineRunParameters.size(), is(2));
        assertThat(pipelineRunParameters.get(0).getName(), is(PARAM1_NAME));
        assertThat(pipelineRunParameters.get(0).getValue(), is(PARAM1_VALUE));
        assertThat(pipelineRunParameters.get(1).getName(), is(PARAM2_NAME));
        assertThat(pipelineRunParameters.get(1).getValue(), is(PARAM2_VALUE));
    }

    @Test
    public void shouldParseMultiValueParam() {
        PipelineRun pipelineRun = new PipelineRun();
        String multiValue = Stream.of(PARAM1_VALUE, PARAM2_VALUE).collect(Collectors.joining(","));
        pipelineRun.setParams(glueParam(PARAM1_NAME, multiValue));
        pipelineRun.parseParameters();
        List<PipelineRunParameter> pipelineRunParameters = pipelineRun.getPipelineRunParameters();
        assertThat(pipelineRunParameters.size(), is(1));
        assertThat(pipelineRunParameters.get(0).getName(), is(PARAM1_NAME));
        assertThat(pipelineRunParameters.get(0).getValue(), is(multiValue));
    }

    private String glueParams(String... param) {
        return Arrays.stream(param).collect(Collectors.joining(PipelineRun.PARAM_DELIMITER));
    }

    private String glueParam(String name, String value) {
        return glueParam(name, value, null);
    }

    private String glueParam(String name, String value, String type) {
        String result = name + PipelineRun.KEY_VALUE_DELIMITER + value;
        if (StringUtils.hasText(type)) {
            result += PipelineRun.KEY_VALUE_DELIMITER + type;
        }
        return result;
    }
}