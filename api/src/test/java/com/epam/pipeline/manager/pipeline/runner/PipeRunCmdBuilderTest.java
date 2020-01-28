/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.pipeline.runner;

import com.epam.pipeline.entity.configuration.PipeConfValueVO;
import com.epam.pipeline.entity.pipeline.run.PipeRunCmdStartVO;
import com.epam.pipeline.entity.pipeline.run.PipelineStart;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class PipeRunCmdBuilderTest {

    private static final String TEST_VERSION = "draft";
    private static final String TEST_PARAM_NAME_1 = "--param1";
    private static final String TEST_PARAM_NAME_2 = "--param2";
    private static final String TEST_PARAM_VALUE_1 = "string value";
    private static final String TEST_PARAM_VALUE_2 = "1";
    private static final String INSTANCE_TYPE = "type";
    private static final String DOCKER_IMAGE = "image";
    private static final String CMD_TEMPLATE = "sleep 100";

    @Test
    public void shouldGenerateLaunchCommand() {
        final Map<String, PipeConfValueVO> runParameters = new HashMap<>();
        final PipeConfValueVO pipeConfValue1 = new PipeConfValueVO(TEST_PARAM_VALUE_1, "string");
        runParameters.put(TEST_PARAM_NAME_1, pipeConfValue1);
        final PipeConfValueVO pipeConfValue2 = new PipeConfValueVO(TEST_PARAM_VALUE_2, "int");
        runParameters.put(TEST_PARAM_NAME_2, pipeConfValue2);

        final PipelineStart pipelineStart = new PipelineStart();
        pipelineStart.setPipelineId(1L);
        pipelineStart.setVersion(TEST_VERSION);
        pipelineStart.setParams(runParameters);
        pipelineStart.setInstanceType(INSTANCE_TYPE);
        pipelineStart.setHddSize(10);
        pipelineStart.setDockerImage(DOCKER_IMAGE);
        pipelineStart.setCmdTemplate(CMD_TEMPLATE);
        pipelineStart.setTimeout(10L);
        pipelineStart.setNodeCount(5);
        pipelineStart.setCloudRegionId(1L);
        pipelineStart.setParentNodeId(1L);
        pipelineStart.setParentRunId(1L);

        final PipeRunCmdStartVO pipeRunCmdStartVO = new PipeRunCmdStartVO();
        pipeRunCmdStartVO.setPipelineStart(pipelineStart);
        pipeRunCmdStartVO.setYes(true);
        pipeRunCmdStartVO.setSync(true);
        pipeRunCmdStartVO.setQuite(true);
        pipeRunCmdStartVO.setShowParams(true);

        final PipeRunCmdBuilder pipeRunCmdBuilder = new PipeRunCmdBuilder(pipeRunCmdStartVO);
        final String actualResult = pipeRunCmdBuilder
                .name()
                .config()
                .runParameters()
                .parameters()
                .yes()
                .instanceDisk()
                .instanceType()
                .dockerImage()
                .cmdTemplate()
                .timeout()
                .quite()
                .instanceCount()
                .sync()
                .priceType()
                .regionId()
                .parentNode()
                .build();
        final String expectedResult = String.format("pipe run -n 1@%s %s '%s' %s %s parent-id 1 -p -y -id 10 " +
                        "-it type -di image -cmd '%s' -t 10 -q -ic 5 -s -pt spot -r 1 -pn 1",
                TEST_VERSION, TEST_PARAM_NAME_1, TEST_PARAM_VALUE_1, TEST_PARAM_NAME_2, TEST_PARAM_VALUE_2,
                CMD_TEMPLATE);
        Assert.assertEquals(expectedResult, actualResult);
    }
}
