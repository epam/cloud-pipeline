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
package com.epam.pipeline.elasticsearchagent;

import com.epam.pipeline.elasticsearchagent.model.PermissionsContainer;
import com.epam.pipeline.entity.configuration.RunConfiguration;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.issue.Issue;
import com.epam.pipeline.entity.metadata.MetadataEntity;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.RunLog;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.ToolGroup;
import com.epam.pipeline.entity.pipeline.run.RunStatus;
import com.epam.pipeline.entity.pipeline.run.parameter.PipelineRunParameter;
import com.epam.pipeline.entity.user.PipelineUser;

import java.util.List;

import static com.epam.pipeline.elasticsearchagent.TestConstants.GROUP_NAME;
import static com.epam.pipeline.elasticsearchagent.TestConstants.USER;
import static com.epam.pipeline.elasticsearchagent.TestConstants.USER_NAME;
import static com.epam.pipeline.elasticsearchagent.VerificationUtils.verifyArray;
import static com.epam.pipeline.elasticsearchagent.VerificationUtils.verifyStringArray;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings({"PMD.TooManyStaticImports", "unchecked"})
public final class LoaderVerificationUtils {

    public static void verifyPipelineUser(final PipelineUser actual) {
        assertNotNull(actual);
        assertEquals(actual.getUserName(), USER.getUserName());
        assertEquals(actual.getId(), USER.getId());
        assertThat(actual.getGroups())
                .hasSize(1)
                .containsOnly(GROUP_NAME);
        assertTrue(actual.getAttributes().containsKey("Name"));
        assertEquals(USER_NAME, actual.getAttributes().get("Name"));
    }

    public static void verifyPermissions(final PermissionsContainer expected, final PermissionsContainer actual) {
        assertNotNull(actual);

        verifyStringArray(expected.getAllowedUsers(), actual.getAllowedUsers());
        verifyStringArray(expected.getDeniedUsers(), actual.getDeniedUsers());
        verifyStringArray(expected.getAllowedGroups(), actual.getAllowedGroups());
        verifyStringArray(expected.getDeniedGroups(), actual.getDeniedGroups());
    }

    public static void verifyMetadata(final List<String> expected, final List<String> actual) {
        verifyStringArray(expected, actual);
    }

    public static void verifyPipelineRun(final PipelineRun expectedPipelineRun, final PipelineRun actualPipelineRun) {
        assertAll("pipelineRun",
            () -> assertEquals(expectedPipelineRun.getId(), actualPipelineRun.getId()),
            () -> assertEquals(expectedPipelineRun.getName(), actualPipelineRun.getName()),
            () -> assertEquals(expectedPipelineRun.getCreatedDate(), actualPipelineRun.getCreatedDate()),
            () -> assertEquals(expectedPipelineRun.getPipelineName(), actualPipelineRun.getPipelineName()),
            () -> assertEquals(expectedPipelineRun.getStatus(), actualPipelineRun.getStatus()),
            () -> assertEquals(expectedPipelineRun.getVersion(), actualPipelineRun.getVersion()),
            () -> assertEquals(expectedPipelineRun.getPricePerHour(), actualPipelineRun.getPricePerHour()),
            () -> assertEquals(expectedPipelineRun.getPricePerHour(), actualPipelineRun.getPricePerHour()));
    }

    public static void verifyRunParameters(final List<PipelineRunParameter> expectedRunParameters,
                                           final List<PipelineRunParameter> actualRunParameters) {
        verifyArray(expectedRunParameters, actualRunParameters);
        PipelineRunParameter expectedRunParameter = expectedRunParameters.get(0);
        PipelineRunParameter actualRunParameter = actualRunParameters.get(0);
        assertAll("runParameter",
            () -> assertEquals(expectedRunParameter.getName(), actualRunParameter.getName()),
            () -> assertEquals(expectedRunParameter.getValue(), actualRunParameter.getValue()));
    }

    public static void verifyRunStatuses(final List<RunStatus> expectedRunStatuses,
                                         final List<RunStatus> actualRunStatuses) {
        verifyArray(expectedRunStatuses, actualRunStatuses);
        RunStatus expectedRunStatus = expectedRunStatuses.get(0);
        RunStatus actualRunStatus = actualRunStatuses.get(0);
        assertAll("runStatus",
            () -> assertEquals(expectedRunStatus.getRunId(), actualRunStatus.getRunId()),
            () -> assertEquals(expectedRunStatus.getStatus(), actualRunStatus.getStatus()));
    }

    public static void verifyRunLogs(final List<RunLog> expected, final List<RunLog> actual) {
        verifyArray(expected, actual);
        RunLog expectedRunLog = expected.get(0);
        RunLog actualRunLog = actual.get(0);
        assertAll("runLog",
            () -> assertEquals(expectedRunLog.getLogText(), actualRunLog.getLogText()),
            () -> assertEquals(expectedRunLog.getStatus(), actualRunLog.getStatus()),
            () -> assertEquals(expectedRunLog.getTask(), actualRunLog.getTask()));
    }

    public static void verifyRunInstance(final RunInstance expectedRunInstance, final RunInstance actualRunInstance) {
        assertAll("runInstance",
            () -> assertEquals(expectedRunInstance.getNodeId(), actualRunInstance.getNodeId()),
            () -> assertEquals(expectedRunInstance.getNodeDisk(), actualRunInstance.getNodeDisk()),
            () -> assertEquals(expectedRunInstance.getNodeImage(), actualRunInstance.getNodeImage()),
            () -> assertEquals(expectedRunInstance.getNodeName(), actualRunInstance.getNodeName()),
            () -> assertEquals(expectedRunInstance.getSpot(), actualRunInstance.getSpot()),
            () -> assertEquals(expectedRunInstance.getNodeType(), actualRunInstance.getNodeType()),
            () -> assertEquals(expectedRunInstance.getAwsRegionId(), actualRunInstance.getAwsRegionId()));
    }

    public static void verifyTool(final Tool expectedTool, final Tool actualTool) {
        assertAll("tool",
            () -> assertEquals(expectedTool.getId(), actualTool.getId()),
            () -> assertEquals(expectedTool.getRegistry(), actualTool.getRegistry()),
            () -> assertEquals(expectedTool.getRegistryId(), actualTool.getRegistryId()),
            () -> assertEquals(expectedTool.getImage(), actualTool.getImage()),
            () -> assertEquals(expectedTool.getDescription(), actualTool.getDescription()),
            () -> assertEquals(expectedTool.getDefaultCommand(), actualTool.getDefaultCommand()),
            () -> assertEquals(expectedTool.getToolGroupId(), actualTool.getToolGroupId()));
        verifyArray(expectedTool.getLabels(), actualTool.getLabels());
        assertEquals(expectedTool.getLabels().get(0), actualTool.getLabels().get(0));
    }

    public static void verifyRegistry(final DockerRegistry expectedRegistry, final DockerRegistry actualRegistry) {
        assertAll("registry",
            () -> assertEquals(expectedRegistry.getId(), actualRegistry.getId()),
            () -> assertEquals(expectedRegistry.getName(), actualRegistry.getName()),
            () -> assertEquals(expectedRegistry.getPath(), actualRegistry.getPath()),
            () -> assertEquals(expectedRegistry.getUserName(), actualRegistry.getUserName()));
    }

    public static void verifyConfiguration(final RunConfiguration expectedConfiguration,
                                           final RunConfiguration actualConfiguration) {
        assertAll("configuration",
            () -> assertEquals(expectedConfiguration.getId(), actualConfiguration.getId()),
            () -> assertEquals(expectedConfiguration.getName(), actualConfiguration.getName()),
            () -> assertEquals(expectedConfiguration.getDescription(), actualConfiguration.getDescription()),
            () -> assertEquals(expectedConfiguration.getOwner(), actualConfiguration.getOwner()));
    }

    public static void verifyPipeline(final List<Pipeline> expected, final List<Pipeline> actual) {
        verifyArray(expected, actual);
        Pipeline expectedPipeline = expected.get(0);
        Pipeline actualPipeline = actual.get(0);
        assertAll("pipeline",
            () -> assertEquals(expectedPipeline.getId(), actualPipeline.getId()),
            () -> assertEquals(expectedPipeline.getName(), actualPipeline.getName()));
    }

    public static void verifyIssue(final Issue expected, final Issue actual) {
        assertAll("issue",
            () -> assertEquals(expected.getId(), actual.getId()),
            () -> assertEquals(expected.getName(), actual.getName()),
            () -> assertEquals(expected.getText(), actual.getText()),
            () -> assertEquals(expected.getStatus(), actual.getStatus()),
            () -> assertEquals(expected.getLabels(), actual.getLabels()),
            () -> assertEquals(expected.getEntity(), actual.getEntity()));
        verifyArray(expected.getAttachments(), actual.getAttachments());
        verifyArray(expected.getComments(), actual.getComments());
    }

    public static void verifyMetadataEntity(final MetadataEntity expected, final MetadataEntity actual) {
        assertAll("metadataEntity",
            () -> assertEquals(expected.getId(), actual.getId()),
            () -> assertEquals(expected.getName(), actual.getName()),
            () -> assertEquals(expected.getClassEntity(), actual.getClassEntity()),
            () -> assertEquals(expected.getExternalId(), actual.getExternalId()),
            () -> assertEquals(expected.getParent(), actual.getParent()),
            () -> assertEquals(expected.getOwner(), actual.getOwner()),
            () -> assertEquals(expected.getData(), actual.getData()));
    }

    public static void verifyDataStorage(final AbstractDataStorage expected, final AbstractDataStorage actual) {
        assertAll("dataStorage",
            () -> assertEquals(expected.getId(), actual.getId()),
            () -> assertEquals(expected.getName(), actual.getName()),
            () -> assertEquals(expected.getParentFolderId(), actual.getParentFolderId()),
            () -> assertEquals(expected.getPath(), actual.getPath()),
            () -> assertEquals(expected.getStoragePolicy(), actual.getStoragePolicy()),
            () -> assertEquals(expected.getOwner(), actual.getOwner()));
    }

    public static void verifyToolGroup(final ToolGroup expected, final ToolGroup actual) {
        assertAll("toolGroup",
            () -> assertEquals(expected.getId(), actual.getId()),
            () -> assertEquals(expected.getName(), actual.getName()),
            () -> assertEquals(expected.getRegistryId(), actual.getRegistryId()),
            () -> assertEquals(expected.getDescription(), actual.getDescription()));
    }

    private LoaderVerificationUtils() {
        // no-op
    }
}
