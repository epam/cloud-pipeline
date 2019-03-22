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

package com.epam.pipeline.manager.execution;


import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.configuration.PipeConfValueVO;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(Parameterized.class) public class CommandBuilderTest {

    private static final String PYTHON_SRC = "python src/";
    private static final String RUN_DATE = "run_date";
    private static final String RUN_TIME = "run_time";
    private static final String TEST_VERSION = "version";
    private static final String SAMPLE = "sample";
    private static final String INPUT_FASTQ = "input_fastq";
    private static final String MAIN_FILE = "main_file";
    private static final String PIPELINE_NAME = "pipeline_name";
    private static final String POD_ID = "parent";
    private static final String MAIN_CLASS = "main_class";
    private static final String KUBE_NAMESPACE = "namespace";
    private static final String API_HOST = "api";
    private static final String PIPELINE_ID = "pipeline_id";
    private static final String RUN_ID = "run_id";

    private static final boolean ENABLE_AUTOSCALING = true;


    private String template;
    private String expected;

    public CommandBuilderTest(String template, String expected) {
        this.template = template;
        this.expected = expected;
    }

    @InjectMocks private CommandBuilder commandBuilder;

    @Mock private MessageHelper messageHelper;

    @Rule public MockitoRule rule = MockitoJUnit.rule();

    @Before public void setUp() {
        Mockito.when(messageHelper.getMessage(Mockito.anyString(), Mockito.anyObject()))
                .thenReturn("");
    }

    @Parameters public static List<Object[]> balanceRates() {
        return Arrays.asList(
            new Object[][] {
                {
                    PYTHON_SRC + "[main_file] [main_class] [sample] -I [input-fastq] -a [api] -v [version]",
                    PYTHON_SRC + MAIN_FILE + " " + MAIN_CLASS + " " + SAMPLE + " -I " + INPUT_FASTQ
                        + " -a " + API_HOST + " -v " + TEST_VERSION
                },
                {
                    PYTHON_SRC + "[main_file] [main_class] [user-params] [sys-params]",
                    PYTHON_SRC + MAIN_FILE + " " + MAIN_CLASS + " --sample $" + SAMPLE + " --input-fastq $"
                        + INPUT_FASTQ + " --api " + API_HOST + " --version " + TEST_VERSION
                        + " --namespace " + KUBE_NAMESPACE + " --parent " + POD_ID + " --pipeline-name "
                        + PIPELINE_NAME + " --run-date " + RUN_DATE + " --run-time " + RUN_TIME + " --run-id " + RUN_ID
                        + " --pipeline-id " + PIPELINE_ID + " --autoscaling-enabled "
                },
                {
                    PYTHON_SRC + "[main_file] [main_class] [user-params] -srv [api]",
                    PYTHON_SRC + MAIN_FILE + " " + MAIN_CLASS + " --sample $" + SAMPLE + " --input-fastq $"
                        + INPUT_FASTQ + " -srv " + API_HOST
                },
                {
                    PYTHON_SRC + "[main_file] [main_class] [sample] -I [input-fastq] [sys-params]",
                    PYTHON_SRC + MAIN_FILE + " " + MAIN_CLASS + " " + SAMPLE + " -I " + INPUT_FASTQ
                        + " --api " + API_HOST + " --version " + TEST_VERSION + " --namespace " + KUBE_NAMESPACE
                        + " --parent " + POD_ID + " --pipeline-name " + PIPELINE_NAME + " --run-date " + RUN_DATE
                        + " --run-time " + RUN_TIME + " --run-id " + RUN_ID + " --pipeline-id " + PIPELINE_ID
                        + " --autoscaling-enabled "
                }
            }
        );
    }

    @Test public void testBuild() {
        Map<SystemParams, String> sysParams = matchSystemParams();
        Assert.assertEquals(expected, commandBuilder.build(constructConfiguration(), sysParams));
    }

    private PipelineConfiguration constructConfiguration() {

        PipelineConfiguration configuration = new PipelineConfiguration();
        configuration.setCmdTemplate(template);
        configuration.setMainFile(MAIN_FILE);
        configuration.setMainClass(MAIN_CLASS);
        configuration.setEnvironmentParams(
            new LinkedHashMap<String, String>(){
                {
                    put("main_class", MAIN_CLASS);
                    put("main_file", MAIN_FILE);
                    put("cmd_template", template);
                }
            }
        );

        configuration.setParameters(
            new LinkedHashMap<String, PipeConfValueVO>() {
                {
                    put("sample", new PipeConfValueVO(SAMPLE));
                    put("input-fastq", new PipeConfValueVO(INPUT_FASTQ));
                }
            }
        );
        return configuration;
    }

    private Map<SystemParams, String> matchSystemParams() {
        EnumMap<SystemParams, String> systemParamsWithValue = new EnumMap<>(SystemParams.class);
        systemParamsWithValue.put(SystemParams.API, API_HOST);
        systemParamsWithValue.put(SystemParams.VERSION, TEST_VERSION);
        systemParamsWithValue.put(SystemParams.NAMESPACE, KUBE_NAMESPACE);
        systemParamsWithValue.put(SystemParams.PARENT, POD_ID);
        systemParamsWithValue.put(SystemParams.PIPELINE_NAME, PIPELINE_NAME);
        systemParamsWithValue.put(SystemParams.RUN_DATE, RUN_DATE);
        systemParamsWithValue.put(SystemParams.RUN_TIME, RUN_TIME);
        systemParamsWithValue.put(SystemParams.RUN_ID, RUN_ID);
        systemParamsWithValue.put(SystemParams.PIPELINE_ID, PIPELINE_ID);

        systemParamsWithValue.put(SystemParams.AWS_ACCESS_KEY_ID, "AWS key");
        systemParamsWithValue.put(SystemParams.AWS_SECRET_ACCESS_KEY, "AWS secret");
        systemParamsWithValue.put(SystemParams.AWS_DEFAULT_REGION, "REGION");
        systemParamsWithValue.put(SystemParams.API_TOKEN, "Token");

        if (ENABLE_AUTOSCALING) {
            systemParamsWithValue.put(SystemParams.AUTOSCALING_ENABLED, "");
        }
        return systemParamsWithValue;
    }
}
