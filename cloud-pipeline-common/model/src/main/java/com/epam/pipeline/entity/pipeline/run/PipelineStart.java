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

package com.epam.pipeline.entity.pipeline.run;

import com.epam.pipeline.entity.configuration.ExecutionEnvironment;
import com.epam.pipeline.entity.configuration.PipeConfValueVO;
import com.epam.pipeline.entity.configuration.PipelineConfValuesMapDeserializer;
import com.epam.pipeline.entity.pipeline.run.parameter.RunSid;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PipelineStart {
    private Long pipelineId;
    private String version;
    private Long timeout;
    private String instanceType;
    private Integer hddSize;
    private String dockerImage;
    private String cmdTemplate;
    private Long useRunId;
    private Long parentNodeId;
    private String configurationName;
    private Integer nodeCount;
    private String workerCmd;
    private Long parentRunId;
    private Boolean isSpot;
    private List<RunSid> runSids;
    private Long cloudRegionId;
    private boolean force;
    private ExecutionEnvironment executionEnvironment;
    private String prettyUrl;
    private boolean nonPause;

    @JsonDeserialize(using = PipelineConfValuesMapDeserializer.class)
    private Map<String, PipeConfValueVO> params;
}
