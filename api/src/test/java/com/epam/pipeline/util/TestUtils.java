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

package com.epam.pipeline.util;

import com.epam.pipeline.entity.docker.ManifestV2;
import com.epam.pipeline.entity.docker.ToolVersion;
import com.epam.pipeline.entity.pipeline.CommitStatus;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.ToolScanStatus;
import com.epam.pipeline.entity.pipeline.run.parameter.RunSid;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.entity.scan.ToolVersionScanResult;
import com.epam.pipeline.entity.scan.Vulnerability;
import com.epam.pipeline.entity.scan.VulnerabilitySeverity;
import com.epam.pipeline.manager.docker.DockerClient;
import com.epam.pipeline.manager.docker.DockerClientFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;

/**
 * Utility class for testing help methods
 */
public final class TestUtils {
    private static final String TEST_DIGEST = "digest";
    public static final String LATEST_TAG = "latest";
    public static final long DOCKER_SIZE = 123456L;

    private TestUtils() {
        // No-op
    }

    /**
     * Helper method for mocking DockerClient functionality
     * @param dockerClientMock a {@link DockerClient} mock object
     * @param dockerClientFactoryMock a {@link DockerClientFactory} mock object
     * @return a mocking ManifestV2
     */
    public static ManifestV2 configureDockerClientMock(DockerClient dockerClientMock,
                                                       DockerClientFactory dockerClientFactoryMock) {
        ManifestV2 mockManifest = new ManifestV2();
        mockManifest.setConfig(new ManifestV2.Config(TEST_DIGEST, null));
        mockManifest.setDigest(TEST_DIGEST);
        mockManifest.setLayers(Collections.singletonList(new ManifestV2.Config(TEST_DIGEST, null)));

        Mockito.doReturn(dockerClientMock).when(dockerClientFactoryMock).getDockerClient(any());
        Mockito.doReturn(dockerClientMock).when(dockerClientFactoryMock).getDockerClient(any(), any());
        Mockito.doReturn(Collections.singletonList(LATEST_TAG)).when(dockerClientMock).getImageTags(any(), anyString());
        Mockito.doReturn(Optional.of(mockManifest)).when(dockerClientMock).deleteImage(any(), any(), any());

        ToolVersion toolVersion = new ToolVersion();
        toolVersion.setDigest("test_digest");
        toolVersion.setSize(DOCKER_SIZE);
        toolVersion.setVersion("test_version");
        Mockito.doReturn(toolVersion).when(dockerClientMock).getVersionAttributes(any(), any(), any());

        return mockManifest;
    }

    public static <T> void checkEquals(T origin, T loaded, ObjectMapper objectMapper) {
        Map<String, Object> originProperties = objectMapper.convertValue(origin, objectMapper.getTypeFactory()
            .constructParametricType(Map.class, String.class, Object.class));

        Map<String, Object> loadedProperties = objectMapper.convertValue(loaded, objectMapper.getTypeFactory()
            .constructParametricType(Map.class, String.class, Object.class));

        originProperties.forEach((key, value) -> Assert.assertEquals(value, loadedProperties.get(key)));
    }

    public static void generateScanResult(int criticalVulnerabilitiesCount, int highVulnerabilitiesCount,
                                    int mediumVulnerabilitiesCount, ToolVersionScanResult versionScanResult) {
        List<Vulnerability> testVulnerabilities = IntStream
            .range(0, criticalVulnerabilitiesCount)
            .mapToObj(i -> createVulnerability(VulnerabilitySeverity.Critical))
            .collect(Collectors.toList());
        testVulnerabilities.addAll(IntStream.range(0, highVulnerabilitiesCount)
                                       .mapToObj(i -> createVulnerability(VulnerabilitySeverity.High))
                                       .collect(Collectors.toList()));
        testVulnerabilities.addAll(IntStream.range(0, mediumVulnerabilitiesCount)
                                       .mapToObj(i -> createVulnerability(VulnerabilitySeverity.Medium))
                                       .collect(Collectors.toList()));
        versionScanResult.setVulnerabilities(testVulnerabilities);
        versionScanResult.setScanDate(new Date());
        versionScanResult.setSuccessScanDate(new Date());
        versionScanResult.setStatus(ToolScanStatus.COMPLETED);
    }

    public static ToolVersionScanResult generateScanResult(int criticalVulnerabilitiesCount,
                                                           int highVulnerabilitiesCount,
                                                           int mediumVulnerabilitiesCount) {
        ToolVersionScanResult result = new ToolVersionScanResult();
        generateScanResult(criticalVulnerabilitiesCount, highVulnerabilitiesCount, mediumVulnerabilitiesCount, result);
        return result;
    }

    public static Vulnerability createVulnerability(VulnerabilitySeverity severity) {
        Vulnerability v = new Vulnerability();
        v.setSeverity(severity);
        return v;
    }

    public static PipelineRun createPipelineRun(Long pipelineId, String params, TaskStatus status, String owner,
                                                Long parentRunId, Long entitiesId, Boolean isSpot, Long configurationId,
                                                List<RunSid> runSids, String podId, Long regionId) {
        PipelineRun run = new PipelineRun();
        run.setPipelineId(pipelineId);
        run.setStartDate(new Date());
        run.setEndDate(new Date());
        run.setStatus(status);
        run.setCommitStatus(CommitStatus.NOT_COMMITTED);
        run.setLastChangeCommitTime(new Date());
        run.setPodId(podId);
        run.setParams(params);
        run.setOwner(owner);
        run.setParentRunId(parentRunId);
        run.setRunSids(runSids);

        RunInstance instance = new RunInstance();
        instance.setCloudRegionId(regionId);
        instance.setCloudProvider(CloudProvider.AWS);
        instance.setSpot(isSpot);
        instance.setNodeId("1");
        run.setInstance(instance);
        run.setEntitiesIds(Collections.singletonList(entitiesId));
        run.setConfigurationId(configurationId);
        return run;
    }
}
