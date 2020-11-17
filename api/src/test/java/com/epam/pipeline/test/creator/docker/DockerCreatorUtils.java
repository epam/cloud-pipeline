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

package com.epam.pipeline.test.creator.docker;

import com.epam.pipeline.controller.Result;
import com.epam.pipeline.controller.vo.docker.DockerRegistryVO;
import com.epam.pipeline.entity.docker.DockerRegistryList;
import com.epam.pipeline.entity.docker.ImageDescription;
import com.epam.pipeline.entity.docker.ImageHistoryLayer;
import com.epam.pipeline.entity.docker.ToolDescription;
import com.epam.pipeline.entity.docker.ToolVersion;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.pipeline.DockerRegistryEventEnvelope;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.ToolGroup;
import com.epam.pipeline.entity.pipeline.ToolGroupWithIssues;
import com.epam.pipeline.entity.scan.ToolScanPolicy;
import com.epam.pipeline.entity.scan.ToolScanResultView;
import com.epam.pipeline.entity.scan.ToolVersionScanResult;
import com.epam.pipeline.entity.scan.ToolVersionScanResultView;
import com.epam.pipeline.entity.tool.ToolSymlinkRequest;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_INT;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;

public final class DockerCreatorUtils {

    public static final TypeReference<Result<ImageDescription>> IMAGE_DESCRIPTION_INSTANCE_TYPE =
            new TypeReference<Result<ImageDescription>>() {};
    public static final TypeReference<Result<ToolDescription>> TOOL_DESCRIPTION_INSTANCE_TYPE =
            new TypeReference<Result<ToolDescription>>() {};
    public static final TypeReference<Result<ToolVersion>> TOOL_VERSION_INSTANCE_TYPE =
            new TypeReference<Result<ToolVersion>>() {};
    public static final TypeReference<Result<List<ImageHistoryLayer>>> IMAGE_HISTORY_LIST_INSTANCE_TYPE =
            new TypeReference<Result<List<ImageHistoryLayer>>>() {};
    public static final TypeReference<Result<List<ToolVersion>>> TOOL_VERSION_LIST_INSTANCE_TYPE =
            new TypeReference<Result<List<ToolVersion>>>() {};
    public static final TypeReference<Result<ToolVersionScanResult>> TOOL_VERSION_SCAN_INSTANCE_TYPE =
            new TypeReference<Result<ToolVersionScanResult>>() {};
    public static final TypeReference<Result<ToolScanResultView>> SCAN_RESULT_VIEW_INSTANCE_TYPE =
            new TypeReference<Result<ToolScanResultView>>() {};
    public static final TypeReference<Result<ToolScanPolicy>> TOOL_SCAN_POLICY_INSTANCE_TYPE =
            new TypeReference<Result<ToolScanPolicy>>() {};
    public static final TypeReference<Result<Tool>> TOOL_INSTANCE_TYPE = new TypeReference<Result<Tool>>() {};
    public static final TypeReference<Result<List<String>>> LIST_STRING_INSTANCE_TYPE =
            new TypeReference<Result<List<String>>>() {};
    public static final TypeReference<Result<ToolGroup>> TOOL_GROUP_TYPE =
            new TypeReference<Result<ToolGroup>>() {};
    public static final TypeReference<Result<ToolGroupWithIssues>> TOOL_GROUP_WITH_ISSUES_TYPE =
            new TypeReference<Result<ToolGroupWithIssues>>() {};
    public static final TypeReference<Result<List<ToolGroup>>> TOOL_GROUP_LIST_TYPE =
            new TypeReference<Result<List<ToolGroup>>>() {};
    public static final TypeReference<Result<DockerRegistry>> DOCKER_REGISTRY_INSTANCE_TYPE =
            new TypeReference<Result<DockerRegistry>>() {};
    public static final TypeReference<Result<DockerRegistryList>> DOCKER_REGISTRY_LIST_INSTANCE_TYPE =
            new TypeReference<Result<DockerRegistryList>>() {};
    public static final TypeReference<Result<List<Tool>>> TOOL_LIST_INSTANCE_TYPE =
            new TypeReference<Result<List<Tool>>>() {};

    private DockerCreatorUtils() {

    }

    public static ImageDescription getImageDescription() {
        return new ImageDescription();
    }

    public static ImageHistoryLayer getImageHistoryLayer() {
        return new ImageHistoryLayer();
    }

    public static ToolDescription getToolDescription() {
        return new ToolDescription();
    }

    public static ToolVersion getToolVersion() {
        return new ToolVersion();
    }

    public static ToolVersionScanResult getToolVersionScanResult() {
        final ToolVersionScanResult toolVersionScanResult = new ToolVersionScanResult();
        toolVersionScanResult.setToolId(ID);
        toolVersionScanResult.setVersion(TEST_STRING);
        toolVersionScanResult.setFromWhiteList(true);
        return toolVersionScanResult;
    }

    public static ToolScanResultView getToolScanResultView() {
        return new ToolScanResultView(ID, Collections.singletonMap(TEST_STRING, getToolVersionScanResultView()));
    }

    public static ToolVersionScanResultView getToolVersionScanResultView() {
        return ToolVersionScanResultView.builder().build();
    }

    public static ToolScanPolicy getToolScanPolicy() {
        return new ToolScanPolicy();
    }

    public static Tool getTool() {
        final Tool tool = new Tool();
        tool.setId(ID);
        tool.setImage(TEST_STRING);
        tool.setCpu(TEST_STRING);
        tool.setRam(TEST_STRING);
        tool.setInstanceType(TEST_STRING);
        tool.setDisk(TEST_INT);
        return tool;
    }

    public static ToolSymlinkRequest getToolSymlinkRequest() {
        return new ToolSymlinkRequest(ID, ID);
    }

    public static ToolGroup getToolGroup() {
        return new ToolGroup();
    }

    public static ToolGroupWithIssues getToolGroupWithIssues() {
        return new ToolGroupWithIssues();
    }

    public static DockerRegistry getDockerRegistry() {
        final DockerRegistry dockerRegistry = new DockerRegistry();
        dockerRegistry.setPath(TEST_STRING);
        dockerRegistry.setDescription(TEST_STRING);
        dockerRegistry.setSecretName(TEST_STRING);
        dockerRegistry.setUserName(TEST_STRING);
        dockerRegistry.setPassword(TEST_STRING);
        dockerRegistry.setCaCert(TEST_STRING);
        return dockerRegistry;
    }

    public static DockerRegistryVO getDockerRegistryVO() {
        final DockerRegistryVO dockerRegistryVO = new DockerRegistryVO();
        dockerRegistryVO.setId(ID);
        dockerRegistryVO.setPath(TEST_STRING);
        dockerRegistryVO.setDescription(TEST_STRING);
        dockerRegistryVO.setUserName(TEST_STRING);
        dockerRegistryVO.setPassword(TEST_STRING);
        dockerRegistryVO.setCaCert(TEST_STRING);
        dockerRegistryVO.setPipelineAuth(true);
        dockerRegistryVO.setExternalUrl(TEST_STRING);
        dockerRegistryVO.setSecurityScanEnabled(true);
        return dockerRegistryVO;
    }

    public static DockerRegistryList getDockerRegistryList() {
        return new DockerRegistryList(Collections.singletonList(getDockerRegistry()));
    }

    public static DockerRegistryEventEnvelope getDockerRegistryEventEnvelope() {
        return new DockerRegistryEventEnvelope();
    }
}
