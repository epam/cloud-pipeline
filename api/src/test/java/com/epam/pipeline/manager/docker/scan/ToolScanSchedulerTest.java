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

package com.epam.pipeline.manager.docker.scan;

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.docker.DockerRegistryDao;
import com.epam.pipeline.dao.tool.ToolDaoTest;
import com.epam.pipeline.dao.tool.ToolGroupDao;
import com.epam.pipeline.entity.docker.ToolVersion;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.ToolGroup;
import com.epam.pipeline.entity.pipeline.ToolScanStatus;
import com.epam.pipeline.entity.scan.*;
import com.epam.pipeline.manager.docker.DockerClient;
import com.epam.pipeline.manager.docker.DockerClientFactory;
import com.epam.pipeline.manager.docker.DockerRegistryManager;
import com.epam.pipeline.manager.docker.ToolVersionManager;
import com.epam.pipeline.manager.pipeline.ToolManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.util.TestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.internal.util.reflection.Whitebox;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ToolScanSchedulerTest extends AbstractSpringTest {
    private static final String TEST_REPO = "repository";
    private static final String TEST_USER = "test";
    private static final String LATEST_VERSION = "latest";
    private static final String TEST_LAYER_REF = "testRef";
    private static final String TEST_LAYER_DIGEST = "testDigest";
    public static final long DOCKER_SIZE = 123456L;

    @InjectMocks
    private ToolScanScheduler toolScanScheduler = new ToolScanScheduler();

    @Autowired
    private DockerRegistryDao dockerRegistryDao;
    @Autowired
    private ToolGroupDao toolGroupDao;

    @Autowired
    private ToolManager toolManager;

    @Autowired
    private MessageHelper messageHelper;

    @Autowired
    private AuthManager authManager;

    @Autowired
    private TaskScheduler taskScheduler;

    @Mock
    private ToolScanManager toolScanManager;
    @MockBean
    private DockerClientFactory dockerClientFactory;
    @Mock
    private DockerClient mockClient;
    @Mock
    private ToolVersionManager toolVersionManager;
    @Mock
    private DockerRegistryManager dockerRegistryManager;

    ObjectMapper objectMapper = new ObjectMapper();

    private final DockerRegistry registry = new DockerRegistry();
    private final ToolGroup toolGroup = new ToolGroup();
    private final Tool tool = ToolDaoTest.generateTool();

    private Vulnerability vulnerability;
    private ToolDependency dependency;


    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        Whitebox.setInternalState(toolScanScheduler, "dockerRegistryDao", dockerRegistryDao);
        Whitebox.setInternalState(toolScanScheduler, "toolManager", toolManager);
        Whitebox.setInternalState(toolScanScheduler, "messageHelper", messageHelper);
        Whitebox.setInternalState(toolScanScheduler, "authManager", authManager);
        Whitebox.setInternalState(toolScanScheduler, "scheduler", taskScheduler);

        when(dockerClientFactory.getDockerClient(Mockito.any(DockerRegistry.class), Mockito.anyString()))
            .thenReturn(mockClient);
        when(mockClient.getImageTags(Mockito.anyString(), Mockito.anyString()))
            .thenReturn(Collections.singletonList(LATEST_VERSION));

        ToolVersion toolVersion = new ToolVersion();
        toolVersion.setDigest("test_digest");
        toolVersion.setSize(DOCKER_SIZE);
        toolVersion.setVersion("test_version");
        when(mockClient.getVersionAttributes(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(toolVersion);

        vulnerability = new Vulnerability();
        vulnerability.setName("testVulnerability");
        vulnerability.setFixedBy("testFix");
        vulnerability.setFeature("testFeature");
        vulnerability.setSeverity(VulnerabilitySeverity.Critical);

        dependency = new ToolDependency(1, "latest", "test", "1.0",
                ToolDependency.Ecosystem.SYSTEM, null);

        when(toolScanManager.scanTool(Mockito.any(Tool.class), Mockito.anyString(), Mockito.anyBoolean()))
            .thenReturn(new ToolVersionScanResult(LATEST_VERSION, new ToolOSVersion("test", "0.1"),
                    Collections.singletonList(vulnerability),
                    Collections.singletonList(dependency),
                    ToolScanStatus.COMPLETED, TEST_LAYER_REF, TEST_LAYER_DIGEST));

        doNothing().when(toolVersionManager).updateOrCreateToolVersion(Mockito.anyLong(), Mockito.anyString(),
                Mockito.anyString(), Mockito.any(DockerRegistry.class), Mockito.any(DockerClient.class));
        when(dockerRegistryManager.getImageToken(Mockito.any(DockerRegistry.class), Mockito.anyString()))
                .thenReturn("token");
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void testScheduledToolScan() {
        PreferenceManager preferenceManager = mock(PreferenceManager.class);
        Whitebox.setInternalState(toolScanScheduler, "preferenceManager", preferenceManager);
        when(preferenceManager.getPreference(SystemPreferences.DOCKER_SECURITY_TOOL_SCAN_ENABLED)).thenReturn(true);
        when(preferenceManager.getPreference(SystemPreferences.DOCKER_SECURITY_TOOL_SCAN_ALL_REGISTRIES))
            .thenReturn(false);

        registry.setPath(TEST_REPO);
        registry.setOwner(TEST_USER);
        registry.setSecurityScanEnabled(true);
        dockerRegistryDao.createDockerRegistry(registry);

        toolGroup.setName("testGroup");
        toolGroup.setRegistryId(registry.getId());
        toolGroup.setOwner(TEST_USER);
        toolGroupDao.createToolGroup(toolGroup);

        tool.setId(1L);
        tool.setRegistryId(registry.getId());
        tool.setToolGroupId(toolGroup.getId());
        toolManager.create(tool, false);

        toolScanScheduler.scheduledToolScan();

        ToolVersionScanResult versionScanResult = toolManager.loadToolVersionScan(tool.getId(), LATEST_VERSION).get();
        Assert.assertNotNull(versionScanResult);
        Assert.assertEquals(ToolScanStatus.COMPLETED, versionScanResult.getStatus());
        Assert.assertNotNull(versionScanResult.getScanDate());

        Vulnerability loaded = versionScanResult.getVulnerabilities().get(0);
        TestUtils.checkEquals(vulnerability, loaded, objectMapper);

        Optional<String> loadedRef = toolManager.loadToolVersionScan(tool.getId(), LATEST_VERSION)
                .map(ToolVersionScanResult::getLastLayerRef);
        Assert.assertTrue(loadedRef.isPresent());
        Assert.assertEquals(TEST_LAYER_REF, loadedRef.get());
    }

    @Test
    @Transactional(propagation = Propagation.NEVER, rollbackFor = Throwable.class)
    public void testForceScheduleToolScan() throws ExecutionException, InterruptedException {
        PreferenceManager preferenceManager = mock(PreferenceManager.class);
        Whitebox.setInternalState(toolScanScheduler, "preferenceManager", preferenceManager);
        when(preferenceManager.getPreference(SystemPreferences.DOCKER_SECURITY_TOOL_SCAN_ENABLED)).thenReturn(true);
        when(preferenceManager.getPreference(SystemPreferences.DOCKER_SECURITY_TOOL_SCAN_SCHEDULE_CRON)).thenReturn(
                SystemPreferences.DOCKER_SECURITY_TOOL_SCAN_SCHEDULE_CRON.getDefaultValue());
        Subject<String> subject = PublishSubject.create();
        when(preferenceManager.getObservablePreference(SystemPreferences.DOCKER_SECURITY_TOOL_SCAN_SCHEDULE_CRON))
                .thenReturn(subject);

        toolScanScheduler.init();

        try {

            ToolManagerMock toolManagerMock = new ToolManagerMock(tool);
            Whitebox.setInternalState(toolScanScheduler, "toolManager", toolManagerMock);

            Future<ToolVersionScanResult> result = toolScanScheduler.forceScheduleScanTool(
                    null, tool.getImage(), LATEST_VERSION, false);
            result.get(); // wait for execution to complete

            ToolScanResult toolScanResult = toolManagerMock.loadToolScanResult(null, tool.getImage());
            Assert.assertFalse(toolScanResult.getToolVersionScanResults().isEmpty());
            Assert.assertEquals(ToolScanStatus.COMPLETED, toolScanResult.getToolVersionScanResults()
                    .get(LATEST_VERSION).getStatus());
            Assert.assertNotNull(toolScanResult.getToolVersionScanResults().get(LATEST_VERSION).getScanDate());

            Vulnerability loaded = toolScanResult.getToolVersionScanResults()
                    .get(LATEST_VERSION).getVulnerabilities().get(0);
            TestUtils.checkEquals(vulnerability, loaded, objectMapper);

            ToolVersionScanResult versionScan = toolManagerMock.loadToolVersionScan(tool.getId(), LATEST_VERSION).get();
            Assert.assertNotNull(versionScan);
            Assert.assertNotNull(versionScan.getScanDate());
            Assert.assertNotNull(versionScan.getSuccessScanDate());
            Assert.assertEquals(TEST_LAYER_REF, versionScan.getLastLayerRef());
        } finally {
            toolScanScheduler.shutDown();
        }
    }

    /**
     * A mocking implementation of ToolManager to store data in memory, used because
     * ToolScanScheduler::forceScheduleScanTool uses multithreading and so cannot be tested using a transactional
     * context
     */
    private final class ToolManagerMock extends ToolManager {
        Map<String, Tool> toolsByImage = new HashMap<>();
        Map<Long, Map<String, ToolVersionScanResult>> toolsVersionScanByToolId = new HashMap<>();
        Map<Long, Tool> toolsById = new HashMap<>();
        Map<Long, Map<String, List<Vulnerability>>> vulnerabilityMap = new HashMap<>();
        Map<Long, Map<String, List<ToolDependency>>> dependencyMap = new HashMap<>();
        Map<Long, String> imageRefByToolId = new HashMap<>();

        private ToolManagerMock(Tool... tools) {
            for (Tool tool : tools) {
                Random random = new Random();
                tool.setId(random.nextLong());
                toolsByImage.put(tool.getImage(), tool);
                toolsById.put(tool.getId(), tool);
            }
        }

        @Override
        public Tool loadTool(String registry, String image) {
            return toolsByImage.get(image);
        }

        @Override
        public void updateToolVersionScanStatus(long toolId, ToolScanStatus newStatus, Date scanDate, String version,
                                                ToolOSVersion toolOSVersion, String layerRef, String digest) {
            Assert.assertNotNull(version);

            ToolVersionScanResult versionScan = new ToolVersionScanResult();
            versionScan.setToolId(toolId);
            versionScan.setVersion(version);
            versionScan.setLastLayerRef(layerRef);
            versionScan.setDigest(digest);
            versionScan.setStatus(newStatus);
            versionScan.setScanDate(scanDate);

            if (newStatus == ToolScanStatus.COMPLETED) {
                versionScan.setSuccessScanDate(scanDate);
            }

            Map<String, ToolVersionScanResult> versionScanMap = toolsVersionScanByToolId
                    .computeIfAbsent(toolId, key -> new HashMap<>());
            versionScanMap.put(version, versionScan); // Rewrite it
            imageRefByToolId.put(toolId, layerRef);
        }

        @Override
        public void updateToolVersionScanStatus(long toolId, ToolScanStatus newStatus, Date scanDate, String version,
                                                String layerRef, String digest) {
            updateToolVersionScanStatus(toolId, newStatus, scanDate, version, null, layerRef, digest);
        }

        @Override
        public void updateToolVulnerabilities(List<Vulnerability> vulnerabilities, long toolId, String version) {
            if (!vulnerabilityMap.containsKey(toolId)) {
                vulnerabilityMap.put(toolId, new HashMap<>());
            }
            vulnerabilityMap.get(toolId).put(version, vulnerabilities);
        }

        @Override
        public void updateToolDependencies(List<ToolDependency> dependencies, long toolId, String version) {
            if (!dependencyMap.containsKey(toolId)) {
                dependencyMap.put(toolId, new HashMap<>());
            }
            dependencyMap.get(toolId).put(version, dependencies);
        }

        @Override
        public ToolScanResult loadToolScanResult(String registry, String id) {
            ToolScanResult result = new ToolScanResult();
            Tool tool = toolsByImage.get(id);
            Optional<ToolVersionScanResult> toolVersionScanOp = loadToolVersionScan(tool.getId(), LATEST_VERSION);
            if (toolVersionScanOp.isPresent()) {
                ToolVersionScanResult toolVersionScan = toolVersionScanOp.get();
                toolVersionScan.setVulnerabilities(vulnerabilityMap.get(tool.getId()).get(LATEST_VERSION));
                result.getToolVersionScanResults().put(LATEST_VERSION, toolVersionScan);
            }
            return result;
        }

        @Override
        public Optional<ToolVersionScanResult> loadToolVersionScan(long toolId, String version) {
            Map<String, ToolVersionScanResult> versionScanMap = toolsVersionScanByToolId
                    .computeIfAbsent(toolId, key -> new HashMap<>());
            return Optional.ofNullable(versionScanMap.get(version));
        }

    }
}