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

import com.epam.pipeline.entity.configuration.PipeConfValueVO;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import io.fabric8.kubernetes.api.model.EnvVar;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EnvVarsBuilderTest {

    private static final String CMD_TEMPLATE = "python src/[main_file] [main_class] [user-params] [sys-params]";
    private static final String NUMBER_TYPE = "Number";
    private static final String STRING_TYPE = "String";

    private static final String P1 = "P1";
    private static final String P1_VALUE = "1";

    private static final String P2 = "P2";
    private static final String P2_VALUE = "abc";

    private static final String MAIN_CLASS = "MAIN_CLASS";
    private static final String MAIN_CLASS_VALUE = "mainc";

    private static final String MAIN_FILE = "MAIN_FILE";
    private static final String MAIN_FILE_VALUE = "mainf";

    private static final String VERSION = "VERSION";
    private static final String VERSION_VALUE = "version";

    private static final String PIPELINE_ID = "PIPELINE_ID_VALUE";
    private static final String PIPELINE_ID_VALUE = "1";

    private Map<SystemParams, String> systemParams;
    private PipelineConfiguration configuration;

    @Before
    public void setUp() {
        systemParams = matchSystemParams();
        configuration = matchPipeConfig();
    }

    @Test
    public void buildEnvVarsTest() throws Exception {
        List<EnvVar> envVars = EnvVarsBuilder.buildEnvVars(new PipelineRun(), configuration, systemParams,
                null);
        Assert.assertTrue(isParameterRight(envVars, P1, P1_VALUE, NUMBER_TYPE));
        Assert.assertTrue(isParameterRight(envVars, P2, P2_VALUE, STRING_TYPE));
        Assert.assertTrue(isParameterRight(envVars, MAIN_CLASS, MAIN_CLASS_VALUE, STRING_TYPE));
        Assert.assertTrue(isParameterRight(envVars, MAIN_FILE, MAIN_FILE_VALUE, STRING_TYPE));
        Assert.assertTrue(isParameterRight(envVars, VERSION, VERSION_VALUE, STRING_TYPE));
        Assert.assertTrue(isParameterRight(envVars, PIPELINE_ID, PIPELINE_ID_VALUE, STRING_TYPE));
    }

    private boolean isParameterRight(List<EnvVar> envVars, String name, String value, String type) {
        return envVars
                .stream()
                .filter(envVar -> envVar.getName().startsWith(name))
                .allMatch(envVar -> envVar.getValue().equals(value) || envVar.getValue().equals(type));
    }

    public static Map<SystemParams, String> matchSystemParams() {
        EnumMap<SystemParams, String> systemParamsWithValue = new EnumMap<>(SystemParams.class);

        systemParamsWithValue.put(SystemParams.VERSION, VERSION_VALUE);
        systemParamsWithValue.put(SystemParams.PIPELINE_ID, PIPELINE_ID_VALUE);

        return systemParamsWithValue;
    }

    public static PipelineConfiguration matchPipeConfig() {
        PipelineConfiguration configuration = new PipelineConfiguration();
        configuration.setCmdTemplate(CMD_TEMPLATE);
        configuration.setMainClass(MAIN_CLASS_VALUE);
        configuration.setMainFile(MAIN_FILE_VALUE);
        configuration.buildEnvVariables();
        configuration.setParameters(new HashMap<String, PipeConfValueVO>(){
            {
                put(P1, new PipeConfValueVO(P1_VALUE, NUMBER_TYPE));
                put(P2, new PipeConfValueVO(P2_VALUE, STRING_TYPE));
            }
        });
        return configuration;
    }

}
