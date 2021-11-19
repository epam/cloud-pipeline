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
import com.epam.pipeline.entity.pipeline.run.OsType;
import com.epam.pipeline.entity.pipeline.run.PipeRunCmdStartVO;
import com.epam.pipeline.entity.pipeline.run.PipelineStart;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@SuppressWarnings("PMD.AvoidDuplicateLiterals")
public class PipeRunCmdBuilderTest {

    public static final String PARAM_WTH_TYPE_TEMPLATE = "%s?%s";
    private static final String PARAM_INT_TYPE = "int";
    private static final String PARAM_INPUT_TYPE = "input";
    private static final String PARAM_OUTPUT_TYPE = "output";
    private static final String PARAM_COMMON_TYPE = "common";
    private static final String PARAM_PATH_TYPE = "path";
    private static final String TEST_VERSION = "draft";
    private static final String TEST_PARAM_NAME_1 = "--param1";
    private static final String TEST_PARAM_NAME_2 = "--param2";
    private static final String TEST_PARAM_NAME_3 = "--param3";
    private static final String TEST_PARAM_NAME_4 = "--param4";
    private static final String TEST_PARAM_NAME_5 = "--param5";
    private static final String TEST_PARAM_NAME_6 = "--param6";
    private static final String TEST_PARAM_NAME_7 = "--param7";
    private static final String TEST_PARAM_VALUE_1 = "string value";
    private static final String TEST_PARAM_VALUE_2 = "1";
    private static final String TEST_PARAM_VALUE_MULTIPLE_PATHS = "/bucket1/, /bucket2/";
    private static final String INSTANCE_TYPE = "type";
    private static final String DOCKER_IMAGE = "image";
    private static final String CMD_TEMPLATE = "sleep 100";
    private static final String CMD_TEMPLATE_WITH_DOUBLE_QUOTES = "do \"command\"";
    private static final String FIRST_PARAMETER = "-- %s '%s'";
    private static final String QUOTED_PARAMETER = "%s '%s'";
    private static final String LINUX_NEW_LINE_INDICATOR = "\\\n";
    private static final String WINDOWS_NEW_LINE_INDICATOR = "^\n";
    private static final String PARAM_BOOLEAN_TYPE = "boolean";

    @Test
    public void shouldGenerateLaunchCommand() {
        final Map<String, PipeConfValueVO> runParameters = new HashMap<>();
        final PipeConfValueVO pipeConfValue1 = new PipeConfValueVO(TEST_PARAM_VALUE_1, "string");
        runParameters.put(TEST_PARAM_NAME_1, pipeConfValue1);
        final PipeConfValueVO pipeConfValue2 = new PipeConfValueVO(TEST_PARAM_VALUE_2, PARAM_INT_TYPE);
        runParameters.put(TEST_PARAM_NAME_2, pipeConfValue2);
        final PipeConfValueVO pipeConfValue3 = new PipeConfValueVO(TEST_PARAM_VALUE_MULTIPLE_PATHS, PARAM_INPUT_TYPE);
        runParameters.put(TEST_PARAM_NAME_3, pipeConfValue3);
        final PipeConfValueVO pipeConfValue4 = new PipeConfValueVO(TEST_PARAM_VALUE_MULTIPLE_PATHS, PARAM_OUTPUT_TYPE);
        runParameters.put(TEST_PARAM_NAME_4, pipeConfValue4);
        final PipeConfValueVO pipeConfValue5 = new PipeConfValueVO(TEST_PARAM_VALUE_MULTIPLE_PATHS, PARAM_PATH_TYPE);
        runParameters.put(TEST_PARAM_NAME_5, pipeConfValue5);
        final PipeConfValueVO pipeConfValue6 = new PipeConfValueVO(TEST_PARAM_VALUE_MULTIPLE_PATHS, PARAM_COMMON_TYPE);
        runParameters.put(TEST_PARAM_NAME_6, pipeConfValue6);
        final PipeConfValueVO pipeConfValue7 = new PipeConfValueVO(Boolean.TRUE.toString(), PARAM_BOOLEAN_TYPE);
        runParameters.put(TEST_PARAM_NAME_7, pipeConfValue7);
        final Map<String, PipeConfValueVO> sortedParameters = runParameters.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e2, LinkedHashMap::new));
        final PipeRunCmdStartVO pipeRunCmdStartVO = getPipeRunCmdStartVO(sortedParameters);

        final PipeRunCmdBuilder pipeRunCmdBuilder = new PipeRunCmdBuilder(pipeRunCmdStartVO);
        final String actualResult = buildCmd(pipeRunCmdBuilder);
        final String expectedResult = String.format(buildExpectedForLinux("pipe run", "-n 1@%s", "-p", "-y",
                "-id 10", "-it type", "-di image", "-cmd '%s'", "-t 10", "-q", "-ic 5", "-s", "-pt spot", "-r 1",
                "-pn 1",
                FIRST_PARAMETER,
                QUOTED_PARAMETER,
                QUOTED_PARAMETER,
                QUOTED_PARAMETER,
                QUOTED_PARAMETER,
                QUOTED_PARAMETER,
                QUOTED_PARAMETER,
                "parent-id 1"),
                TEST_VERSION, CMD_TEMPLATE,
                TEST_PARAM_NAME_1, TEST_PARAM_VALUE_1,
                TEST_PARAM_NAME_2, String.format(PARAM_WTH_TYPE_TEMPLATE, PARAM_INT_TYPE, TEST_PARAM_VALUE_2),
                TEST_PARAM_NAME_3, String.format(PARAM_WTH_TYPE_TEMPLATE, PARAM_INPUT_TYPE,
                        TEST_PARAM_VALUE_MULTIPLE_PATHS),
                TEST_PARAM_NAME_4, String.format(PARAM_WTH_TYPE_TEMPLATE, PARAM_OUTPUT_TYPE,
                        TEST_PARAM_VALUE_MULTIPLE_PATHS),
                TEST_PARAM_NAME_5, String.format(PARAM_WTH_TYPE_TEMPLATE, PARAM_PATH_TYPE,
                        TEST_PARAM_VALUE_MULTIPLE_PATHS),
                TEST_PARAM_NAME_6, String.format(PARAM_WTH_TYPE_TEMPLATE, PARAM_COMMON_TYPE,
                        TEST_PARAM_VALUE_MULTIPLE_PATHS),
                TEST_PARAM_NAME_7, String.format(PARAM_WTH_TYPE_TEMPLATE, PARAM_BOOLEAN_TYPE, true));
        Assert.assertEquals(expectedResult, actualResult);
    }

    @Test
    public void shouldGenerateWindowsLaunchCommand() {
        final PipeRunCmdStartVO pipeRunCmdStartVO = getPipeRunCmdStartVO(Collections.emptyMap());
        pipeRunCmdStartVO.setRunStartCmdExecutionEnvironment(OsType.WINDOWS);

        final PipeRunCmdBuilder pipeRunCmdBuilder = new PipeRunCmdBuilder(pipeRunCmdStartVO);
        final String actualResult = buildCmd(pipeRunCmdBuilder);
        final String expectedResult = String.format(buildExpectedForWindows("pipe run",
                "-n 1@%s", "-p", "-y", "-id 10", "-it type", "-di image", "-cmd \"%s\"",
                "-t 10", "-q", "-ic 5", "-s", "-pt spot", "-r 1", "-pn 1", "-- parent-id 1"),
                TEST_VERSION, CMD_TEMPLATE);
        Assert.assertEquals(expectedResult, actualResult);
    }

    @Test
    public void shouldGenerateWindowsLaunchCommandWithInnerQuotes() {
        final PipeRunCmdStartVO pipeRunCmdStartVO = getPipeRunCmdStartVO(Collections.emptyMap());
        pipeRunCmdStartVO.getPipelineStart().setCmdTemplate(CMD_TEMPLATE_WITH_DOUBLE_QUOTES);
        pipeRunCmdStartVO.setRunStartCmdExecutionEnvironment(OsType.WINDOWS);

        final PipeRunCmdBuilder pipeRunCmdBuilder = new PipeRunCmdBuilder(pipeRunCmdStartVO);
        final String actualResult = buildCmd(pipeRunCmdBuilder);
        final String expectedResult = String.format(buildExpectedForWindows("pipe run", "-n 1@%s",
                "-p", "-y", "-id 10", "-it type", "-di image", "-cmd \"do \\\"command\\\"\"",
                "-t 10", "-q", "-ic 5", "-s", "-pt spot", "-r 1", "-pn 1", "-- parent-id 1"), TEST_VERSION);
        Assert.assertEquals(expectedResult, actualResult);
    }

    @Test
    public void shouldIgnoreInstanceCountOptionIf0() {
        final PipelineStart pipelineStart = new PipelineStart();
        pipelineStart.setPipelineId(1L);
        pipelineStart.setNodeCount(0);

        final PipeRunCmdStartVO pipeRunCmdStartVO = new PipeRunCmdStartVO();
        pipeRunCmdStartVO.setPipelineStart(pipelineStart);

        final PipeRunCmdBuilder pipeRunCmdBuilder = new PipeRunCmdBuilder(pipeRunCmdStartVO);
        final String actualResult = buildCmd(pipeRunCmdBuilder);
        final String expected = buildExpectedForLinux("pipe run", "-n 1", "-pt spot");
        Assert.assertEquals(expected, actualResult);
    }

    @Test
    public void shouldAddNonPauseOption() {
        final PipelineStart pipelineStart = new PipelineStart();
        pipelineStart.setPipelineId(1L);
        pipelineStart.setIsSpot(false);
        pipelineStart.setNonPause(true);

        final PipeRunCmdStartVO pipeRunCmdStartVO = new PipeRunCmdStartVO();
        pipeRunCmdStartVO.setPipelineStart(pipelineStart);

        final PipeRunCmdBuilder pipeRunCmdBuilder = new PipeRunCmdBuilder(pipeRunCmdStartVO);
        final String actualResult = buildCmd(pipeRunCmdBuilder);
        final String expected = buildExpectedForLinux("pipe run", "-n 1", "-pt on-demand", "-np");
        Assert.assertEquals(expected, actualResult);
    }

    @Test
    public void shouldIgnoreNonPauseOptionIfSpot() {
        final PipelineStart pipelineStart = new PipelineStart();
        pipelineStart.setPipelineId(1L);
        pipelineStart.setIsSpot(true);
        pipelineStart.setNonPause(true);

        final PipeRunCmdStartVO pipeRunCmdStartVO = new PipeRunCmdStartVO();
        pipeRunCmdStartVO.setPipelineStart(pipelineStart);

        final PipeRunCmdBuilder pipeRunCmdBuilder = new PipeRunCmdBuilder(pipeRunCmdStartVO);
        final String actualResult = buildCmd(pipeRunCmdBuilder);
        final String expected = buildExpectedForLinux("pipe run", "-n 1", "-pt spot");
        Assert.assertEquals(expected, actualResult);
    }


    private String buildCmd(final PipeRunCmdBuilder pipeRunCmdBuilder) {
        return pipeRunCmdBuilder
                .name()
                .config()
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
                .nonPause()
                .runParameters()
                .build();
    }

    private PipeRunCmdStartVO getPipeRunCmdStartVO(final Map<String, PipeConfValueVO> runParameters) {
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
        return pipeRunCmdStartVO;
    }

    private String buildExpectedForLinux(final String... values) {
        return String.join(" " + LINUX_NEW_LINE_INDICATOR + " ", values);
    }

    private String buildExpectedForWindows(final String... values) {
        return String.join(" " + WINDOWS_NEW_LINE_INDICATOR  + " ", values);
    }
}
