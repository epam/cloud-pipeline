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
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class PipeRunCmdBuilder {

    private static final String WHITESPACE = " ";

    private final PipelineStart runVO;
    private final PipeRunCmdStartVO startVO;
    private final List<String> cmd;

    public PipeRunCmdBuilder(final PipeRunCmdStartVO startVO) {
        this.startVO = startVO;
        this.runVO = startVO.getPipelineStart();
        this.cmd = new ArrayList<>();
        this.cmd.add("pipe run");
    }

    public String build() {
        return String.join(WHITESPACE, cmd);
    }

    public PipeRunCmdBuilder name() {
        if (StringUtils.isNotBlank(runVO.getVersion())) {
            cmd.add(String.format("%d@%s", runVO.getPipelineId(), runVO.getVersion()));
        } else {
            cmd.add(String.valueOf(runVO.getPipelineId()));
        }
        return this;
    }

    public PipeRunCmdBuilder instanceDisk() {
        buildObjectCmdArg("-id", runVO.getHddSize());
        return this;
    }

    public PipeRunCmdBuilder instanceType() {
        buildStringCmdArg("-it", runVO.getInstanceType());
        return this;
    }

    public PipeRunCmdBuilder dockerImage() {
        buildStringCmdArg("-di", runVO.getDockerImage());
        return this;
    }

    public PipeRunCmdBuilder cmdTemplate() {
        if (StringUtils.isNotBlank(runVO.getCmdTemplate())) {
            cmd.add("-cmd");
            cmd.add(quoteStringArgument(runVO.getCmdTemplate()));
        }
        return this;
    }

    public PipeRunCmdBuilder timeout() {
        buildObjectCmdArg("-t", runVO.getTimeout());
        return this;
    }

    public PipeRunCmdBuilder instanceCount() {
        buildObjectCmdArg("-ic", runVO.getNodeCount());
        return this;
    }

    public PipeRunCmdBuilder priceType() {
        cmd.add("-pt");
        if (Objects.nonNull(runVO.getIsSpot()) && !runVO.getIsSpot()) {
            cmd.add("on-demand");
        } else {
            cmd.add("spot");
        }
        return this;
    }

    public PipeRunCmdBuilder regionId() {
        buildObjectCmdArg("-r", runVO.getCloudRegionId());
        return this;
    }

    public PipeRunCmdBuilder parentNode() {
        buildObjectCmdArg("-pn", runVO.getParentNodeId());
        return this;
    }

    public PipeRunCmdBuilder config() {
        buildStringCmdArg("-c", runVO.getConfigurationName());
        return this;
    }

    public PipeRunCmdBuilder yes() {
        if (startVO.isYes()) {
            cmd.add("-y");
        }
        return this;
    }

    public PipeRunCmdBuilder quite() {
        if (startVO.isQuite()) {
            cmd.add("-q");
        }
        return this;
    }

    public PipeRunCmdBuilder sync() {
        if (startVO.isSync()) {
            cmd.add("-s");
        }
        return this;
    }

    public PipeRunCmdBuilder parameters() {
        if (startVO.isShowParams()) {
            cmd.add("-p");
        }
        return this;
    }

    public PipeRunCmdBuilder runParameters() {
        if (MapUtils.isNotEmpty(runVO.getParams())) {
            final String parametersCommand = runVO.getParams().entrySet()
                    .stream()
                    .map(this::prepareParams)
                    .collect(Collectors.joining(WHITESPACE));
            cmd.add(parametersCommand);
        }
        if (Objects.nonNull(runVO.getParentRunId())) {
            cmd.add(String.format("parent-id %d", runVO.getParentRunId()));
        }
        return this;
    }

    private String prepareParams(final Map.Entry<String, PipeConfValueVO> entry) {
        String value = entry.getValue().getValue();
        if (entry.getValue().getType().equalsIgnoreCase("string")) {
            value = quoteStringArgument(value);
        }
        return entry.getKey() + WHITESPACE + value;
    }

    private void buildObjectCmdArg(final String argumentName, final Object argumentValue) {
        if (Objects.nonNull(argumentValue)) {
            cmd.add(argumentName);
            cmd.add(String.valueOf(argumentValue));
        }
    }

    private void buildStringCmdArg(final String argumentName, final String argumentValue) {
        if (StringUtils.isNotBlank(argumentValue)) {
            cmd.add(argumentName);
            cmd.add(argumentValue);
        }
    }

    private String quoteStringArgument(final String value) {
        return String.format("'%s'", value);
    }
}
