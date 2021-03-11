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

package com.epam.pipeline.manager.docker.scan;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.preference.PreferenceDao;
import com.epam.pipeline.entity.docker.ManifestV2;
import com.epam.pipeline.entity.docker.ToolVersion;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.preference.Preference;
import com.epam.pipeline.entity.scan.*;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.exception.ToolScanExternalServiceException;
import com.epam.pipeline.acl.datastorage.DataStorageApiService;
import com.epam.pipeline.manager.docker.DockerClient;
import com.epam.pipeline.manager.docker.DockerClientFactory;
import com.epam.pipeline.manager.docker.DockerRegistryManager;
import com.epam.pipeline.manager.docker.ToolVersionManager;
import com.epam.pipeline.manager.docker.scan.clair.ClairScanRequest;
import com.epam.pipeline.manager.docker.scan.clair.ClairScanResult;
import com.epam.pipeline.manager.docker.scan.clair.ClairService;
import com.epam.pipeline.manager.docker.scan.dockercompscan.DockerComponentLayerScanResult;
import com.epam.pipeline.manager.docker.scan.dockercompscan.DockerComponentScanRequest;
import com.epam.pipeline.manager.docker.scan.dockercompscan.DockerComponentScanResult;
import com.epam.pipeline.manager.docker.scan.dockercompscan.DockerComponentScanService;
import com.epam.pipeline.manager.pipeline.PipelineConfigurationManager;
import com.epam.pipeline.manager.pipeline.PipelineVersionManager;
import com.epam.pipeline.manager.pipeline.ToolManager;
import com.epam.pipeline.manager.pipeline.ToolScanInfoManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.util.TestUtils;
import okhttp3.Request;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.internal.util.reflection.Whitebox;
import org.mockito.stubbing.Answer;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.util.*;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

public class AggregatingToolScanManagerTest {

    private static final boolean DENY_NOT_SCANNED = true;
    private static final int MAX_CRITICAL_VULNERABILITIES = 2;
    private static final int MAX_HIGH_VULNERABILITIES = 3;
    private static final int MAX_MEDIUM_VULNERABILITIES = -1;
    private static final String TEST_IMAGE = "testImage";
    private static final String LATEST_VERSION = "latest";
    private static final String ACTUAL_SCANNED_VERSION = "actual";
    private static final String TEST_VULNERABILITY_NAME = "testVulnerability";
    private static final String TEST_VULNERABILITY_DESCRIPTION = "testDescription";
    public static final String DIGEST_1 = "digest1";
    public static final String DIGEST_2 = "digest2";
    public static final String DIGEST_3 = "digest3";

    @InjectMocks
    private AggregatingToolScanManager aggregatingToolScanManager = new AggregatingToolScanManager();

    @Spy
    @InjectMocks
    private PipelineConfigurationManager pipelineConfigurationManager = new PipelineConfigurationManager();

    @Mock
    private DockerRegistryManager dockerRegistryManager;

    @Mock
    private DockerClientFactory dockerClientFactory;

    @Mock
    private DockerClient mockDockerClient;

    @Mock
    private ClairService clairService;

    @Mock
    private DockerComponentScanService compScanService;

    @Mock
    private MessageHelper messageHelper;

    @Mock
    private ToolManager toolManager;

    @Mock
    private ToolVersionManager toolVersionManager;

    @Mock
    private PipelineVersionManager versionManager;

    @Mock
    private DataStorageApiService dataStorageApiService;

    @Mock
    private AuthManager authManager;

    @Mock
    private PreferenceManager preferenceManager;

    @Mock
    private ToolScanInfoManager toolScanInfoManager;

    private ToolVersionScanResult toolScanResult = new ToolVersionScanResult();
    private ToolVersionScanResult actual = new ToolVersionScanResult();

    private PipelineUser testUser = new PipelineUser();
    private Tool testTool;
    private ClairScanResult.ClairVulnerability clairVulnerability;
    private ClairScanResult.ClairFeature feature;
    private ToolDependency testDependency;

    @Before
    public void setUp() throws Exception {

        MockitoAnnotations.initMocks(this);

        Whitebox.setInternalState(aggregatingToolScanManager, "preferenceManager", preferenceManager);
        when(preferenceManager.getPreference(SystemPreferences.DOCKER_SECURITY_TOOL_POLICY_DENY_NOT_SCANNED))
                .thenReturn(DENY_NOT_SCANNED);
        when(preferenceManager
                .getPreference(SystemPreferences.DOCKER_SECURITY_TOOL_POLICY_MAX_CRITICAL_VULNERABILITIES))
                .thenReturn(MAX_CRITICAL_VULNERABILITIES);
        when(preferenceManager.getPreference(SystemPreferences.DOCKER_SECURITY_TOOL_POLICY_MAX_HIGH_VULNERABILITIES))
                .thenReturn(MAX_HIGH_VULNERABILITIES);
        when(preferenceManager.getPreference(SystemPreferences.DOCKER_SECURITY_TOOL_POLICY_MAX_MEDIUM_VULNERABILITIES))
                .thenReturn(MAX_MEDIUM_VULNERABILITIES);
        when(preferenceManager.getPreference(SystemPreferences.DOCKER_SECURITY_TOOL_GRACE_HOURS))
                .thenReturn(0);

        Assert.assertNotNull(pipelineConfigurationManager); // Dummy line, to shut up PMD

        testUser.setAdmin(false);

        DockerRegistry testRegistry = new DockerRegistry();

        testTool = new Tool();
        testTool.setId(1L);
        testTool.setImage(TEST_IMAGE);

        ManifestV2 testManifest = new ManifestV2();
        testManifest.setLayers(Arrays.asList(new ManifestV2.Config(DIGEST_1, null),
                                             new ManifestV2.Config(DIGEST_2, null),
                                             new ManifestV2.Config(DIGEST_3, null)));

        toolScanResult.setLastLayerRef(DIGEST_1);
        toolScanResult.setScanDate(DateUtils.now());
        toolScanResult.setVulnerabilities(Collections.emptyList());
        ToolVersion attributes = new ToolVersion();
        attributes.setVersion(LATEST_VERSION);
        attributes.setDigest(DIGEST_3);

        ToolVersion actualAttr = new ToolVersion();
        actualAttr.setVersion(ACTUAL_SCANNED_VERSION);
        actualAttr.setDigest(DIGEST_3);

        actual.setLastLayerRef(aggregatingToolScanManager.getLayerName(TEST_IMAGE, ACTUAL_SCANNED_VERSION));
        actual.setScanDate(DateUtils.now());
        actual.setSuccessScanDate(DateUtils.now());
        actual.setDigest(DIGEST_3);

        ClairScanResult testScanResult = new ClairScanResult();
        feature = new ClairScanResult.ClairFeature();
        feature.setName("test");
        feature.setVersion("test1");


        clairVulnerability = new ClairScanResult.ClairVulnerability();
        clairVulnerability.setSeverity(VulnerabilitySeverity.Critical);
        clairVulnerability.setName(TEST_VULNERABILITY_NAME);
        clairVulnerability.setDescription(TEST_VULNERABILITY_DESCRIPTION);
        feature.setVulnerabilities(Collections.singletonList(clairVulnerability));
        testScanResult.setFeatures(Collections.singletonList(feature));

        DockerComponentScanResult dockerComponentScanResult = new DockerComponentScanResult();
        DockerComponentLayerScanResult layerScanResult = new DockerComponentLayerScanResult();
        testDependency = new ToolDependency(
                1, "latest", "test", "1.0", ToolDependency.Ecosystem.R_PKG, "R Package");
        layerScanResult.setDependencies(Collections.singletonList(testDependency));
        dockerComponentScanResult.setLayers(Collections.singletonList(layerScanResult));

        when(dataStorageApiService.getDataStorages()).thenReturn(Collections.emptyList());
        when(versionManager.getValidDockerImage(TEST_IMAGE)).thenReturn(TEST_IMAGE);
        when(authManager.getCurrentUser()).thenReturn(testUser);
        when(dockerRegistryManager.load(testTool.getRegistryId())).thenReturn(testRegistry);
        when(dockerClientFactory.getDockerClient(eq(testRegistry), anyString())).thenReturn(mockDockerClient);

        when(mockDockerClient.getManifest(any(), Mockito.anyString(), Mockito.anyString()))
            .thenReturn(Optional.of(testManifest));

        when(mockDockerClient.getVersionAttributes(any(), eq(TEST_IMAGE), eq(LATEST_VERSION)))
                .thenReturn(attributes);
        when(mockDockerClient.getVersionAttributes(any(), eq(TEST_IMAGE), eq(ACTUAL_SCANNED_VERSION)))
                .thenReturn(actualAttr);

        when(clairService.scanLayer(any(ClairScanRequest.class)))
            .then((Answer<MockCall<ClairScanRequest>>) invocation ->
                new MockCall<>((ClairScanRequest) invocation.getArguments()[0]));
        when(clairService.getScanResult(Mockito.anyString())).thenReturn(new MockCall<>(testScanResult));

        when(compScanService.scanLayer(any(DockerComponentScanRequest.class)))
                .then((Answer<MockCall<DockerComponentScanRequest>>) invocation ->
                        new MockCall<>((DockerComponentScanRequest) invocation.getArguments()[0]));
        when(compScanService.getScanResult(Mockito.anyString())).thenReturn(new MockCall<>(dockerComponentScanResult));

        when(messageHelper.getMessage(Mockito.anyString(), Mockito.any())).thenReturn("testMessage");
        when(messageHelper.getMessage(any(), any())).thenReturn("testMessage");

        when(toolManager.loadByNameOrId(TEST_IMAGE)).thenReturn(testTool);
        when(toolManager.loadToolVersionScan(testTool.getId(), LATEST_VERSION)).thenReturn(Optional.of(toolScanResult));
        when(toolManager.loadToolVersionScan(testTool.getId(), ACTUAL_SCANNED_VERSION)).thenReturn(Optional.of(actual));
        when(toolScanInfoManager.loadToolVersionScanInfo(testTool.getId(), LATEST_VERSION))
                .thenReturn(Optional.of(toolScanResult));
        when(toolScanInfoManager.loadToolVersionScanInfo(testTool.getId(), ACTUAL_SCANNED_VERSION))
                .thenReturn(Optional.of(actual));

        ToolVersion actual = new ToolVersion();
        actual.setDigest(DIGEST_3);
        when(toolVersionManager.loadToolVersion(testTool.getId(), ACTUAL_SCANNED_VERSION)).thenReturn(actual);

        ToolVersion old = new ToolVersion();
        old.setDigest(DIGEST_2);
        when(toolVersionManager.loadToolVersion(testTool.getId(), LATEST_VERSION)).thenReturn(old);

        when(toolManager.getTagFromImageName(Mockito.anyString())).thenReturn(LATEST_VERSION);
    }

    @Test
    public void testThatScanToolFilterDependencies() throws ToolScanExternalServiceException {
        DockerComponentLayerScanResult layerScanResult = new DockerComponentLayerScanResult();
        layerScanResult.setDependencies(Collections.singletonList(testDependency));
        // mock that Component Scan Service will return 2 identical dependencies
        when(compScanService.getScanResult(Mockito.anyString())).thenReturn(new MockCall<>(
                new DockerComponentScanResult("test", Arrays.asList(layerScanResult, layerScanResult))));
        when(clairService.getScanResult(Mockito.anyString())).thenReturn(new MockCall<>(new ClairScanResult()));

        ToolVersionScanResult result = aggregatingToolScanManager.scanTool(testTool, LATEST_VERSION, false);

        List<ToolDependency> dependencies = result.getDependencies();
        //check that dependencies are filtered and only one pass the filter
        Assert.assertEquals(1, dependencies.size());
    }

    @Test
    public void testScanTool() throws ToolScanExternalServiceException {
        ToolVersionScanResult result = aggregatingToolScanManager.scanTool(testTool, LATEST_VERSION, false);

        Assert.assertFalse(result.getVulnerabilities().isEmpty());

        Vulnerability loadedVulnerability = result.getVulnerabilities().get(0);
        Assert.assertEquals(clairVulnerability.getName(), loadedVulnerability.getName());
        Assert.assertEquals(clairVulnerability.getDescription(), loadedVulnerability.getDescription());
        Assert.assertEquals(clairVulnerability.getSeverity(), loadedVulnerability.getSeverity());
        Assert.assertEquals(feature.getName(), loadedVulnerability.getFeature());
        Assert.assertEquals(feature.getVersion(), loadedVulnerability.getFeatureVersion());

        List<ToolDependency> dependencies = result.getDependencies();
        Assert.assertEquals(2, dependencies.size());

        ToolDependency loadedDependency = dependencies.get(0);
        Assert.assertEquals(testDependency.getName(), loadedDependency.getName());
        Assert.assertEquals(testDependency.getEcosystem(), loadedDependency.getEcosystem());
        Assert.assertEquals(testDependency.getVersion(), loadedDependency.getVersion());
        Assert.assertEquals(testDependency.getDescription(), loadedDependency.getDescription());

        loadedDependency = dependencies.get(1);
        Assert.assertEquals(feature.getName(), loadedDependency.getName());
        Assert.assertEquals(ToolDependency.Ecosystem.SYSTEM, loadedDependency.getEcosystem());
        Assert.assertEquals(feature.getVersion(), loadedDependency.getVersion());

        //check that rescan works
        ToolVersionScanResult rescan = aggregatingToolScanManager.scanTool(testTool, LATEST_VERSION, true);

        Assert.assertNotEquals(rescan.getScanDate(), result.getScanDate());
    }

    @Test
    public void testGetSecurityPolicy() {
        ToolScanPolicy policy = aggregatingToolScanManager.getPolicy();
        Assert.assertEquals(MAX_CRITICAL_VULNERABILITIES, policy.getMaxCriticalVulnerabilities());
        Assert.assertEquals(MAX_HIGH_VULNERABILITIES, policy.getMaxHighVulnerabilities());
        Assert.assertEquals(MAX_MEDIUM_VULNERABILITIES, policy.getMaxMediumVulnerabilities());
        Assert.assertEquals(DENY_NOT_SCANNED, policy.isDenyNotScanned());
    }


    @Test
    public void testDenyNotScanned() {
        Assert.assertFalse(aggregatingToolScanManager.checkTool(testTool, LATEST_VERSION).isAllowed());
    }

    @Test
    public void testGracePeriodWorks() {
        when(preferenceManager.getPreference(SystemPreferences.DOCKER_SECURITY_TOOL_GRACE_HOURS))
                .thenReturn(2);
        ToolVersionScanResult scanResult = new ToolVersionScanResult(LATEST_VERSION);
        scanResult.setScanDate(DateUtils.now());
        when(toolManager.loadToolVersionScan(testTool.getId(), LATEST_VERSION)).thenReturn(Optional.of(scanResult));
        Assert.assertTrue(aggregatingToolScanManager.checkTool(testTool, LATEST_VERSION).isAllowed());
    }

    @Test
    public void testWhiteListWorks() {
        ToolVersionScanResult scanResult = new ToolVersionScanResult();
        scanResult.setLastLayerRef(DIGEST_1);
        scanResult.setFromWhiteList(true);
        scanResult.setScanDate(DateUtils.now());
        scanResult.setVulnerabilities(Collections.emptyList());
        when(toolScanInfoManager.loadToolVersionScanInfo(testTool.getId(), LATEST_VERSION))
                .thenReturn(Optional.of(scanResult));
        Assert.assertTrue(aggregatingToolScanManager.checkTool(testTool, LATEST_VERSION).isAllowed());
    }

    @Test
    public void testDenyOnCritical() {
        TestUtils.generateScanResult(MAX_CRITICAL_VULNERABILITIES + 1, MAX_HIGH_VULNERABILITIES,
                1, toolScanResult);
        Assert.assertFalse(aggregatingToolScanManager.checkTool(testTool, LATEST_VERSION).isAllowed());
    }

    @Test
    public void testDenyOnNotAllowedOS() {
        when(preferenceManager.getPreference(SystemPreferences.DOCKER_SECURITY_TOOL_OS))
                .thenReturn("centos:6");
        TestUtils.generateScanResult(MAX_CRITICAL_VULNERABILITIES + 1, MAX_HIGH_VULNERABILITIES,
                1, toolScanResult, new ToolOSVersion("ubuntu", "14"));
        Assert.assertFalse(aggregatingToolScanManager.checkTool(testTool, LATEST_VERSION).isAllowed());
    }

    @Test
    public void testDenyOnNotAllowedOSVersion() {
        when(preferenceManager.getPreference(SystemPreferences.DOCKER_SECURITY_TOOL_OS))
                .thenReturn("centos:6");
        TestUtils.generateScanResult(MAX_CRITICAL_VULNERABILITIES + 1, MAX_HIGH_VULNERABILITIES,
                1, toolScanResult, new ToolOSVersion("centos", "7"));
        Assert.assertFalse(aggregatingToolScanManager.checkTool(testTool, LATEST_VERSION).isAllowed());
    }

    @Test
    public void testAllowOnAllowedOSVersion() {
        when(preferenceManager.getPreference(SystemPreferences.DOCKER_SECURITY_TOOL_OS))
                .thenReturn("centos");
        TestUtils.generateScanResult(MAX_CRITICAL_VULNERABILITIES + 1, MAX_HIGH_VULNERABILITIES,
                1, toolScanResult, new ToolOSVersion("centos", "7"));
        Assert.assertFalse(aggregatingToolScanManager.checkTool(testTool, LATEST_VERSION).isAllowed());
    }

    @Test
    public void testDenyOnHigh() {
        TestUtils.generateScanResult(MAX_CRITICAL_VULNERABILITIES, MAX_HIGH_VULNERABILITIES + 1,
                1, toolScanResult);
        Assert.assertFalse(aggregatingToolScanManager.checkTool(testTool, LATEST_VERSION).isAllowed());
    }

    @Test
    public void testDenyOnMedium() {
        when(preferenceManager.getPreference(SystemPreferences.DOCKER_SECURITY_TOOL_POLICY_MAX_MEDIUM_VULNERABILITIES))
                .thenReturn(0);
        TestUtils.generateScanResult(MAX_CRITICAL_VULNERABILITIES, MAX_HIGH_VULNERABILITIES,
                1, toolScanResult);
        Assert.assertFalse(aggregatingToolScanManager.checkTool(testTool, LATEST_VERSION).isAllowed());
    }

    @Test
    public void testAllow() {
        TestUtils.generateScanResult(MAX_CRITICAL_VULNERABILITIES, MAX_HIGH_VULNERABILITIES,
                1, toolScanResult);
        Assert.assertTrue(aggregatingToolScanManager.checkTool(testTool, LATEST_VERSION).isAllowed());
    }

    @Test
    public void testNotSendLayersIfScanned() throws ToolScanExternalServiceException {
        ToolVersionScanResult result = aggregatingToolScanManager.scanTool(testTool, ACTUAL_SCANNED_VERSION, false);
        //check that code just get result and don't call scan process
        Assert.assertEquals(actual.getScanDate(), result.getScanDate());
    }

    @Test
    public void testInit() {
        AggregatingToolScanManager toolScanManager = new AggregatingToolScanManager();
        PreferenceManager preferenceManager = new PreferenceManager();
        PreferenceDao preferenceDao = Mockito.mock(PreferenceDao.class);
        SystemPreferences systemPreferences = Mockito.mock(SystemPreferences.class);
        Whitebox.setInternalState(preferenceManager, "preferenceDao", preferenceDao);
        Whitebox.setInternalState(preferenceManager, "messageHelper", messageHelper);
        Whitebox.setInternalState(preferenceManager, "systemPreferences", systemPreferences);
        Whitebox.setInternalState(toolScanManager, "preferenceManager", preferenceManager);

        toolScanManager.init();

        ClairService service = (ClairService) Whitebox.getInternalState(toolScanManager, "clairService");
        Assert.assertNull(service);

        Preference toolScanEnabled = SystemPreferences.DOCKER_SECURITY_TOOL_SCAN_ENABLED.toPreference();
        toolScanEnabled.setValue("true");
        when(preferenceDao.loadPreferenceByName(toolScanEnabled.getName())).thenReturn(toolScanEnabled);

        Preference clairRootUrl = SystemPreferences.DOCKER_SECURITY_TOOL_SCAN_CLAIR_ROOT_URL.toPreference();
        clairRootUrl.setValue("http://localhost:9000/");
        when(preferenceDao.loadPreferenceByName(clairRootUrl.getName())).thenReturn(clairRootUrl);

        preferenceManager.update(Arrays.asList(toolScanEnabled, clairRootUrl));
        service = (ClairService) Whitebox.getInternalState(toolScanManager, "clairService");
        Assert.assertNotNull(service);
    }

    private final class MockCall<T> implements Call<T> {
        private T payload;

        private MockCall(T payload) {
            this.payload = payload;
        }

        @Override
        public Response<T> execute() {
            return Response.success(payload);
        }

        @Override
        public void enqueue(Callback<T> callback) {
            // no-op
        }

        @Override
        public boolean isExecuted() {
            return true;
        }

        @Override
        public void cancel() {
            // no-op
        }

        @Override
        public boolean isCanceled() {
            return false;
        }

        @Override
        public Call<T> clone() {
            return new MockCall<>(payload);
        }

        @Override
        public Request request() {
            return null;
        }
    }
}
