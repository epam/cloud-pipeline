/*
 * Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.dao.pipeline.PipelineRunDao;
import com.epam.pipeline.entity.pipeline.CommitStatus;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.scan.ToolOSVersion;
import com.epam.pipeline.entity.scan.ToolVersionScanResult;
import com.epam.pipeline.manager.docker.DockerRegistryManager;
import com.epam.pipeline.manager.docker.scan.AggregatingToolScanManager;
import com.epam.pipeline.manager.pipeline.ToolManager;
import com.epam.pipeline.manager.pipeline.ToolScanInfoManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

@RunWith(MockitoJUnitRunner.class)
public class ToolOSVersionResolverTest {

    private static final String REGISTRY = "registry:443";
    private static final Long REGISTRY_ID = 1L;
    private static final String DEFAULT_TAG = "latest";
    private static final String COMMITTED = REGISTRY + "/user/desktop:1.1";
    private static final String INTERMEDIATE = REGISTRY + "/user/desktop:1.0";
    private static final String SOURCE = REGISTRY + "/library/desktop:latest";
    private static final String COMMITTED_TOOL = "user/desktop";
    private static final String COMMITTED_TAG = "1.1";
    private static final Long SOURCE_RUN_ID = 926L;
    private static final String SOURCE_POD = "pipeline-926";
    private static final Long INTERMEDIATE_RUN_ID = 931L;
    private static final String INTERMEDIATE_POD = "pipeline-931";
    private static final Long SYMLINK_ID = 10L;
    private static final Long LINKED_TOOL_ID = 11L;
    private static final String LINKED_TOOL = "library/desktop-origin";
    private static final Long FIRST_CHAIN_RUN_ID = 100L;
    private static final String POD_PREFIX = "pipeline-";
    private static final ToolOSVersion ROCKY = new ToolOSVersion("rocky", "8.7");
    private static final String ROCKY_LABEL = "rocky:8.7";
    private static final ToolOSVersion NOT_DETERMINED = new ToolOSVersion(
            AggregatingToolScanManager.NOT_DETERMINED, AggregatingToolScanManager.NOT_DETERMINED);

    @Mock
    private ToolScanInfoManager toolScanInfoManager;
    @Mock
    private ToolManager toolManager;
    @Mock
    private DockerRegistryManager dockerRegistryManager;
    @Mock
    private PipelineRunDao pipelineRunDao;

    @InjectMocks
    private ToolOSVersionResolver resolver;

    private final DockerRegistry registry = new DockerRegistry();

    @Before
    public void setUp() {
        registry.setId(REGISTRY_ID);
        registry.setPath(REGISTRY);
        doReturn(registry).when(dockerRegistryManager).load(REGISTRY_ID);
        doReturn(Optional.empty()).when(toolScanInfoManager).loadToolVersionScanInfoByImageName(anyString());
    }

    @Test
    public void shouldReturnScannedOSVersionOfImage() {
        givenScan(COMMITTED, ROCKY);

        assertThat(resolver.resolve(COMMITTED)).isEqualTo(ROCKY);
        verifyZeroInteractions(dockerRegistryManager, pipelineRunDao);
    }

    @Test
    public void shouldReturnOSVersionOfImageCommittedFromIfImageHasNoScan() {
        givenCommittedFrom(COMMITTED, SOURCE_POD, run(SOURCE_RUN_ID, SOURCE_POD, SOURCE));
        givenScan(SOURCE, ROCKY);

        assertThat(resolver.resolve(COMMITTED)).isEqualTo(ROCKY);
    }

    @Test
    public void shouldReturnOSVersionOfImageCommittedFromIfScannedOSVersionIsNotDetermined() {
        givenScan(COMMITTED, NOT_DETERMINED);
        givenCommittedFrom(COMMITTED, SOURCE_POD, run(SOURCE_RUN_ID, SOURCE_POD, SOURCE));
        givenScan(SOURCE, ROCKY);

        assertThat(resolver.resolve(COMMITTED)).isEqualTo(ROCKY);
    }

    @Test
    public void shouldReturnOSVersionOfImageCommittedFromIfScanHasNoOSVersion() {
        givenScan(COMMITTED, null);
        givenCommittedFrom(COMMITTED, SOURCE_POD, run(SOURCE_RUN_ID, SOURCE_POD, SOURCE));
        givenScan(SOURCE, ROCKY);

        assertThat(resolver.resolve(COMMITTED)).isEqualTo(ROCKY);
    }

    @Test
    public void shouldFollowSeveralCommitsToImageWithScannedOSVersion() {
        givenCommittedFrom(COMMITTED, INTERMEDIATE_POD, run(INTERMEDIATE_RUN_ID, INTERMEDIATE_POD, INTERMEDIATE));
        givenScan(INTERMEDIATE, NOT_DETERMINED);
        givenCommittedFrom(INTERMEDIATE, SOURCE_POD, run(SOURCE_RUN_ID, SOURCE_POD, SOURCE));
        givenScan(SOURCE, ROCKY);

        assertThat(resolver.resolve(COMMITTED)).isEqualTo(ROCKY);
    }

    @Test
    public void shouldReadLabelsOfLinkedToolIfToolIsSymlink() {
        final Tool symlink = tool(SYMLINK_ID, COMMITTED_TOOL + ":" + COMMITTED_TAG);
        symlink.setLink(LINKED_TOOL_ID);
        doReturn(symlink).when(toolManager).loadByNameOrId(COMMITTED);
        doReturn(COMMITTED_TAG).when(toolManager).getTagFromImageName(COMMITTED, DEFAULT_TAG);
        doReturn(DEFAULT_TAG).when(toolManager).resolveDefaultVersion(symlink);
        doReturn(tool(LINKED_TOOL_ID, LINKED_TOOL)).when(toolManager).load(LINKED_TOOL_ID);
        doReturn(Collections.singletonMap(ToolOSVersionResolver.POD_NAME_LABEL, SOURCE_POD))
                .when(dockerRegistryManager).getImageLabels(registry, LINKED_TOOL, COMMITTED_TAG);
        doReturn(run(SOURCE_RUN_ID, SOURCE_POD, SOURCE)).when(pipelineRunDao).loadPipelineRun(SOURCE_RUN_ID);
        givenScan(SOURCE, ROCKY);

        assertThat(resolver.resolve(COMMITTED)).isEqualTo(ROCKY);
    }

    @Test
    public void shouldKeepScannedOSVersionIfImageHasNoPodLabel() {
        givenScan(COMMITTED, NOT_DETERMINED);
        givenTool(COMMITTED, COMMITTED_TOOL, COMMITTED_TAG);
        doReturn(Collections.singletonMap("maintainer", "someone"))
                .when(dockerRegistryManager).getImageLabels(registry, COMMITTED_TOOL, COMMITTED_TAG);

        assertThat(resolver.resolve(COMMITTED)).isEqualTo(NOT_DETERMINED);
        verifyZeroInteractions(pipelineRunDao);
    }

    @Test
    public void shouldIgnorePodLabelIfPodNameHasNoRunId() {
        givenCommittedFrom(COMMITTED, "some-pod", null);

        assertThat(resolver.resolve(COMMITTED)).isNull();
        verifyZeroInteractions(pipelineRunDao);
    }

    @Test
    public void shouldIgnorePodLabelIfRunHasAnotherPod() {
        givenCommittedFrom(COMMITTED, SOURCE_POD, run(SOURCE_RUN_ID, "other-pod-926", SOURCE));
        givenScan(SOURCE, ROCKY);

        assertThat(resolver.resolve(COMMITTED)).isNull();
        verify(toolScanInfoManager, never()).loadToolVersionScanInfoByImageName(SOURCE);
    }

    @Test
    public void shouldIgnorePodLabelIfRunIsNotCommitted() {
        final PipelineRun run = run(SOURCE_RUN_ID, SOURCE_POD, SOURCE);
        run.setCommitStatus(CommitStatus.NOT_COMMITTED);
        givenCommittedFrom(COMMITTED, SOURCE_POD, run);
        givenScan(SOURCE, ROCKY);

        assertThat(resolver.resolve(COMMITTED)).isNull();
        verify(toolScanInfoManager, never()).loadToolVersionScanInfoByImageName(SOURCE);
    }

    @Test
    public void shouldIgnorePodLabelIfRunIsNotFound() {
        givenCommittedFrom(COMMITTED, SOURCE_POD, null);

        assertThat(resolver.resolve(COMMITTED)).isNull();
    }

    @Test
    public void shouldStopIfImageWasCommittedFromItself() {
        givenCommittedFrom(COMMITTED, INTERMEDIATE_POD, run(INTERMEDIATE_RUN_ID, INTERMEDIATE_POD, COMMITTED));

        assertThat(resolver.resolve(COMMITTED)).isNull();
    }

    @Test
    public void shouldStopOnCommitCycle() {
        givenCommittedFrom(COMMITTED, INTERMEDIATE_POD, run(INTERMEDIATE_RUN_ID, INTERMEDIATE_POD, INTERMEDIATE));
        givenCommittedFrom(INTERMEDIATE, SOURCE_POD, run(SOURCE_RUN_ID, SOURCE_POD, COMMITTED));

        assertThat(resolver.resolve(COMMITTED)).isNull();
    }

    @Test
    public void shouldFollowNoMoreThanMaxCommits() {
        final int images = ToolOSVersionResolver.MAX_COMMITS_TO_FOLLOW + 1;
        for (int i = 0; i < images; i++) {
            final long runId = FIRST_CHAIN_RUN_ID + i;
            givenCommittedFrom(image(i), POD_PREFIX + runId, run(runId, POD_PREFIX + runId, image(i + 1)));
        }
        givenScan(image(images), ROCKY);

        assertThat(resolver.resolve(image(0))).isNull();
        verify(toolScanInfoManager, never()).loadToolVersionScanInfoByImageName(image(images));
    }

    @Test
    public void shouldFollowMaxCommits() {
        final int images = ToolOSVersionResolver.MAX_COMMITS_TO_FOLLOW;
        for (int i = 0; i < images; i++) {
            final long runId = FIRST_CHAIN_RUN_ID + i;
            givenCommittedFrom(image(i), POD_PREFIX + runId, run(runId, POD_PREFIX + runId, image(i + 1)));
        }
        givenScan(image(images), ROCKY);

        assertThat(resolver.resolve(image(0))).isEqualTo(ROCKY);
    }

    @Test
    public void shouldKeepScannedOSVersionIfRegistryFails() {
        givenScan(COMMITTED, NOT_DETERMINED);
        givenTool(COMMITTED, COMMITTED_TOOL, COMMITTED_TAG);
        doThrow(new IllegalArgumentException("Cannot get manifest"))
                .when(dockerRegistryManager).getImageLabels(any(), anyString(), anyString());

        assertThat(resolver.resolve(COMMITTED)).isEqualTo(NOT_DETERMINED);
    }

    @Test
    public void shouldStopIfToolOfImageCommittedFromIsNotFound() {
        givenCommittedFrom(COMMITTED, SOURCE_POD, run(SOURCE_RUN_ID, SOURCE_POD, SOURCE));
        doThrow(new IllegalArgumentException("Tool not found"))
                .when(toolScanInfoManager).loadToolVersionScanInfoByImageName(SOURCE);
        doThrow(new IllegalArgumentException("Tool not found")).when(toolManager).loadByNameOrId(SOURCE);

        assertThat(resolver.resolve(COMMITTED)).isNull();
        verify(pipelineRunDao, times(1)).loadPipelineRun(anyLong());
    }

    @Test
    public void shouldReturnOSVersionOfOSLabelOfImage() {
        givenScan(COMMITTED, NOT_DETERMINED);
        givenLabels(COMMITTED, labels(ROCKY_LABEL, SOURCE_POD));

        assertThat(resolver.resolve(COMMITTED)).isEqualTo(ROCKY);
        verifyZeroInteractions(pipelineRunDao);
    }

    @Test
    public void shouldReturnOSVersionOfOSLabelOfImageCommittedFrom() {
        givenCommittedFrom(COMMITTED, INTERMEDIATE_POD, run(INTERMEDIATE_RUN_ID, INTERMEDIATE_POD, INTERMEDIATE));
        givenLabels(INTERMEDIATE, labels(ROCKY_LABEL, SOURCE_POD));

        assertThat(resolver.resolve(COMMITTED)).isEqualTo(ROCKY);
        verify(pipelineRunDao, never()).loadPipelineRun(SOURCE_RUN_ID);
    }

    @Test
    public void shouldPreferScannedOSVersionOfImageCommittedFromToItsOSLabel() {
        givenCommittedFrom(COMMITTED, SOURCE_POD, run(SOURCE_RUN_ID, SOURCE_POD, SOURCE));
        givenScan(SOURCE, ROCKY);
        givenLabels(SOURCE, labels("centos:7", null));

        assertThat(resolver.resolve(COMMITTED)).isEqualTo(ROCKY);
    }

    @Test
    public void shouldIgnoreOSLabelWithoutDeterminedOSVersion() {
        givenLabels(COMMITTED, labels(AggregatingToolScanManager.NOT_DETERMINED + ":", SOURCE_POD));
        doReturn(run(SOURCE_RUN_ID, SOURCE_POD, SOURCE)).when(pipelineRunDao).loadPipelineRun(SOURCE_RUN_ID);
        givenScan(SOURCE, ROCKY);

        assertThat(resolver.resolve(COMMITTED)).isEqualTo(ROCKY);
    }

    @Test
    public void shouldIgnoreMalformedOSLabel() {
        givenLabels(COMMITTED, labels("rocky 8.7", null));

        assertThat(resolver.resolve(COMMITTED)).isNull();
    }

    @Test
    public void shouldWriteDeterminedOSVersionAsOSLabel() {
        assertThat(ToolOSVersionResolver.toOSLabel(ROCKY)).isEqualTo(ROCKY_LABEL);
        assertThat(ToolOSVersionResolver.toOSLabel(new ToolOSVersion("ubuntu", null))).isEqualTo("ubuntu:");
    }

    @Test
    public void shouldNotWriteUndeterminedOrUnsafeOSVersionAsOSLabel() {
        assertThat(ToolOSVersionResolver.toOSLabel(null)).isNull();
        assertThat(ToolOSVersionResolver.toOSLabel(NOT_DETERMINED)).isNull();
        assertThat(ToolOSVersionResolver.toOSLabel(new ToolOSVersion("rocky", "8.7 x"))).isNull();
        assertThat(ToolOSVersionResolver.toOSLabel(new ToolOSVersion("rocky';", "8"))).isNull();
    }

    private void givenScan(final String image, final ToolOSVersion osVersion) {
        final ToolVersionScanResult scan = new ToolVersionScanResult();
        scan.setToolOSVersion(osVersion);
        doReturn(Optional.of(scan)).when(toolScanInfoManager).loadToolVersionScanInfoByImageName(image);
    }

    private void givenCommittedFrom(final String image, final String podName, final PipelineRun run) {
        final String toolImage = toolImage(image);
        final String tag = image.substring(image.lastIndexOf(':') + 1);
        givenTool(image, toolImage, tag);
        doReturn(Collections.singletonMap(ToolOSVersionResolver.POD_NAME_LABEL, podName))
                .when(dockerRegistryManager).getImageLabels(registry, toolImage, tag);
        if (run != null) {
            doReturn(run).when(pipelineRunDao).loadPipelineRun(run.getId());
        }
    }

    private void givenLabels(final String image, final Map<String, String> labels) {
        final String toolImage = toolImage(image);
        final String tag = image.substring(image.lastIndexOf(':') + 1);
        givenTool(image, toolImage, tag);
        doReturn(labels).when(dockerRegistryManager).getImageLabels(registry, toolImage, tag);
    }

    private Map<String, String> labels(final String osLabel, final String podName) {
        final Map<String, String> labels = new HashMap<>();
        labels.put(ToolOSVersionResolver.OS_LABEL, osLabel);
        if (podName != null) {
            labels.put(ToolOSVersionResolver.POD_NAME_LABEL, podName);
        }
        return labels;
    }

    private void givenTool(final String image, final String toolImage, final String tag) {
        // ToolManager#loadByNameOrId returns a tool, whose image carries the tag of the image name
        final Tool tool = tool((long) image.hashCode(), toolImage + ":" + tag);
        doReturn(tool).when(toolManager).loadByNameOrId(image);
        doReturn(DEFAULT_TAG).when(toolManager).resolveDefaultVersion(tool);
        doReturn(tag).when(toolManager).getTagFromImageName(image, DEFAULT_TAG);
    }

    private Tool tool(final Long id, final String image) {
        final Tool tool = new Tool();
        tool.setId(id);
        tool.setImage(image);
        tool.setRegistryId(REGISTRY_ID);
        return tool;
    }

    private PipelineRun run(final Long id, final String podId, final String dockerImage) {
        final PipelineRun run = new PipelineRun();
        run.setId(id);
        run.setPodId(podId);
        run.setDockerImage(dockerImage);
        run.setCommitStatus(CommitStatus.SUCCESS);
        return run;
    }

    private String toolImage(final String image) {
        return image.substring(REGISTRY.length() + 1, image.lastIndexOf(':'));
    }

    private String image(final int index) {
        return REGISTRY + "/user/desktop-" + index + ":1.0";
    }
}
