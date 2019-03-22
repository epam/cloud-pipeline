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

package com.epam.pipeline.entity.configuration;

import com.epam.pipeline.entity.pipeline.run.PipelineStart;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class DtsRunConfigurationEntry extends AbstractRunConfigurationEntry {

    private ExecutionEnvironment executionEnvironment = ExecutionEnvironment.DTS;
    private Long dtsId;
    private Integer coresNumber;

    private Long pipelineId;
    private String pipelineVersion;

    @JsonProperty("cmd_template")
    private String cmdTemplate;
    @JsonProperty("docker_image")
    private String dockerImage;

    @JsonDeserialize(using = PipelineConfValuesMapDeserializer.class)
    private Map<String, PipeConfValueVO> parameters = new LinkedHashMap<>();

    @Override
    public boolean checkConfigComplete() {
        if (dtsId == null) {
            return false;
        }
        if (coresNumber != null && coresNumber < 0) {
            return false;
        }
        if (pipelineId == null && StringUtils.hasText(dockerImage) &&
                StringUtils.hasText(cmdTemplate)) {
            return true;
        }
        return pipelineId != null && StringUtils.hasText(pipelineVersion);
    }

    @Override
    public PipelineStart toPipelineStart() {
        PipelineStart pipelineStart = new PipelineStart();
        pipelineStart.setPipelineId(pipelineId);
        pipelineStart.setVersion(pipelineVersion);
        pipelineStart.setCmdTemplate(cmdTemplate);
        pipelineStart.setDockerImage(dockerImage);
        pipelineStart.setParams(parameters);
        return pipelineStart;
    }

    @Override
    public Integer getWorkerCount() {
        return null;
    }

}
