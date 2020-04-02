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

package com.epam.pipeline.manager.pipeline;

import java.util.*;

import com.epam.pipeline.controller.vo.region.AWSRegionDTO;
import com.epam.pipeline.dao.docker.DockerRegistryDao;
import com.epam.pipeline.entity.docker.ToolVersion;
import com.epam.pipeline.entity.pipeline.*;
import com.epam.pipeline.entity.preference.Preference;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.entity.scan.ToolOSVersion;
import com.epam.pipeline.entity.scan.ToolScanResult;
import com.epam.pipeline.entity.scan.ToolVersionScanResult;
import com.epam.pipeline.entity.cluster.InstanceType;
import com.epam.pipeline.manager.cluster.InstanceOfferManager;
import com.epam.pipeline.manager.AbstractManagerTest;
import com.epam.pipeline.manager.docker.DockerClient;
import com.epam.pipeline.manager.docker.DockerClientFactory;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.epam.pipeline.util.TestUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;

public class ToolManagerTest extends AbstractManagerTest {

    private static final String TEST = "test";
    private static final String TEST_USER = TEST;
    private static final String TEST_IMAGE = "library/image";
    private static final String TEST_CPU = "500m";
    private static final String CHANGED_TEST_CPU = "500m";
    private static final String TEST_RAM = "1Gi";
    private static final String CHANGED_TEST_RAM = "1Gi";
    private static final String TEST_REPO = "repository";
    private static final String TEST_REPO_2 = "repository2";
    private static final List<String> LABELS = new ArrayList<>();
    private static final List<String> ENDPOINTS = new ArrayList<>();
    private static final String LABEL_1 = "label1";
    private static final String LABEL_2 = "label2";
    private static final String TEST_IMAGE_2 = "image2";
    private static final String TEST_GROUP_NAME = TEST;
    private static final String TEST_GROUP_ID1 = "repository/test";
    private static final String TEST_GROUP_ID2 = "repository2/test";
    private static final String TEST_ALLOWED_INSTANCE_TYPE = "m5.large";
    private static final String TEST_ANOTHER_REGION_ALLOWED_INSTANCE_TYPE = "r4.xlarge";
    private static final String TEST_NOT_ALLOWED_INSTANCE_TYPE = "notAllowedInstanceType";
    private static final String LATEST_TAG = "latest";
    private static final String TAG = "tag";
    private static final String LAYER_REF = "layerRef";
    private static final String DIGEST = "layerDigest";
    private static final Long DOCKER_SIZE = 123456L;
    private static final String REGION_NAME = "region";
    private static final String REGION_CODE = "us-east-1";
    private static final String ON_DEMAND = "OnDemand";
    public static final String CENTOS = "centos";
    public static final String CENTOS_VERSION = "6.10";

    @Autowired
    private DockerRegistryDao registryDao;

    @InjectMocks
    @Autowired
    private ToolManager toolManager;

    @MockBean
    private DockerClientFactory dockerClientFactory;

    @Autowired
    private InstanceOfferManager instanceOfferManager;

    @Autowired
    private CloudRegionManager cloudRegionManager;

    @Mock
    private DockerClient dockerClient;

    @Autowired
    private ToolGroupManager toolGroupManager;

    @Autowired
    private PreferenceManager preferenceManager;

    private DockerRegistry firstRegistry;
    private DockerRegistry secondRegistry;
    private ToolGroup firstToolGroup;
    private ToolGroup secondToolGroup;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        TestUtils.configureDockerClientMock(dockerClient, dockerClientFactory);

        AWSRegionDTO regionVO = new AWSRegionDTO();
        regionVO.setName(REGION_NAME);
        regionVO.setRegionCode(REGION_CODE);
        regionVO.setProvider(CloudProvider.AWS);

        final AbstractCloudRegion region = cloudRegionManager.create(regionVO);
        final AbstractCloudRegion anotherRegion = cloudRegionManager.create(regionVO);

        firstRegistry = new DockerRegistry();
        firstRegistry.setPath(TEST_REPO);
        firstRegistry.setOwner(TEST_USER);
        registryDao.createDockerRegistry(firstRegistry);

        secondRegistry = new DockerRegistry();
        secondRegistry.setPath(TEST_REPO_2);
        secondRegistry.setOwner(TEST_USER);
        registryDao.createDockerRegistry(secondRegistry);
        LABELS.add(LABEL_1);
        LABELS.add(LABEL_2);
        ENDPOINTS.add("{\"nginx\" : {\"port\" : 8080}}");
        ENDPOINTS.add("9080");

        firstToolGroup = new ToolGroup();
        firstToolGroup.setName(TEST_GROUP_NAME);
        firstToolGroup.setRegistryId(firstRegistry.getId());
        toolGroupManager.create(firstToolGroup);

        secondToolGroup = new ToolGroup();
        secondToolGroup.setName(TEST_GROUP_NAME);
        secondToolGroup.setRegistryId(secondRegistry.getId());
        toolGroupManager.create(secondToolGroup);

        ToolVersion toolVersion = new ToolVersion();
        toolVersion.setDigest("test_digest");
        toolVersion.setSize(DOCKER_SIZE);
        toolVersion.setVersion("test_version");
        Mockito.when(dockerClient.getVersionAttributes(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(toolVersion);

        InstanceType instanceType = new InstanceType();
        instanceType.setName(TEST_ALLOWED_INSTANCE_TYPE);
        instanceType.setRegionId(region.getId());
        instanceType.setTermType(ON_DEMAND);
        InstanceType anotherRegionInstanceType = new InstanceType();
        anotherRegionInstanceType.setName(TEST_ANOTHER_REGION_ALLOWED_INSTANCE_TYPE);
        anotherRegionInstanceType.setTermType(ON_DEMAND);
        anotherRegionInstanceType.setRegionId(anotherRegion.getId());
        List<InstanceType> instanceTypes = Arrays.asList(instanceType, anotherRegionInstanceType);
        instanceOfferManager.updateOfferedInstanceTypes(instanceTypes);
    }

    @After
    public void tearDown() {
        instanceOfferManager.updateOfferedInstanceTypes(Collections.emptyList());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void createToolShouldRegisterNewTool() {
        Tool tool = generateTool(TEST_GROUP_ID1);

        tool.setToolGroupId(firstToolGroup.getId());
        toolManager.create(tool, true);

        Tool loaded = toolManager.loadTool(tool.getRegistry(), tool.getImage());

        Assert.assertEquals(tool.getImage(), loaded.getImage());
        Assert.assertEquals(TEST_REPO, loaded.getRegistry());
        Assert.assertEquals(tool.getCpu(), loaded.getCpu());
        Assert.assertEquals(tool.getRam(), loaded.getRam());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void createToolShouldValidateToolInstanceType() {
        final Tool tool = generateTool(TEST_GROUP_ID1);
        tool.setToolGroupId(firstToolGroup.getId());

        toolManager.create(tool, true);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void createToolShouldValidateToolInstanceTypeAvailableForAnyRegion() {
        final Tool tool = generateTool(TEST_GROUP_ID1);
        tool.setInstanceType(TEST_ANOTHER_REGION_ALLOWED_INSTANCE_TYPE);
        tool.setToolGroupId(firstToolGroup.getId());

        toolManager.create(tool, true);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void createToolShouldNotValidateToolInstanceTypeIfItIsNotSpecified() {
        final Tool tool = generateTool(TEST_GROUP_ID1);
        tool.setInstanceType(null);
        tool.setToolGroupId(firstToolGroup.getId());

        toolManager.create(tool, true);
    }

    @Test(expected = IllegalArgumentException.class)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void createToolShouldThrowIfInstanceTypeIsNotAllowed() {
        final Tool tool = generateTool(TEST_GROUP_ID1);
        tool.setToolGroupId(firstToolGroup.getId());
        tool.setInstanceType(TEST_NOT_ALLOWED_INSTANCE_TYPE);

        toolManager.create(tool, true);
    }

    @Test(expected = IllegalArgumentException.class)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void createToolShouldThrownExceptionIfToolAlreadyExists() {
        Tool tool = generateTool(TEST_GROUP_ID1);

        tool.setRegistry(firstRegistry.getPath());
        toolManager.create(tool, true);
        toolManager.create(tool, true);
    }

    @Test(expected = IllegalArgumentException.class)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void createToolShouldThrownExceptionIfToolAlreadyExistsInAnotherRegistry() {
        Tool tool = generateTool(TEST_GROUP_ID1);

        tool.setRegistry(firstRegistry.getPath());
        toolManager.create(tool, true);
        tool.setRegistry(secondRegistry.getPath());
        toolManager.create(tool, true);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void updateToolShouldUpdateToolParams() {
        Tool tool = generateTool(TEST_GROUP_ID1);

        tool.setToolGroupId(firstToolGroup.getId());
        tool.setRegistry(firstRegistry.getPath());
        toolManager.create(tool, true);

        tool.setRam(CHANGED_TEST_RAM);
        tool.setCpu(CHANGED_TEST_CPU);
        tool.setLabels(LABELS);
        tool.setEndpoints(ENDPOINTS);

        toolManager.updateTool(tool);
        Tool loaded = toolManager.loadTool(tool.getRegistry(), tool.getImage());

        Assert.assertEquals(TEST_IMAGE, loaded.getImage());
        Assert.assertEquals(firstRegistry.getPath(), loaded.getRegistry());
        Assert.assertEquals(CHANGED_TEST_CPU, loaded.getCpu());
        Assert.assertEquals(CHANGED_TEST_RAM, loaded.getRam());
        Assert.assertEquals(LABELS, loaded.getLabels());
        Assert.assertEquals(ENDPOINTS, loaded.getEndpoints());

    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void updateToolShouldValidateToolInstanceType() {
        final Tool tool = generateTool(TEST_GROUP_ID1);
        tool.setInstanceType(null);
        tool.setToolGroupId(firstToolGroup.getId());
        final Tool createdTool = toolManager.create(tool, true);

        createdTool.setInstanceType(TEST_ALLOWED_INSTANCE_TYPE);
        toolManager.updateTool(createdTool);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void updateToolShouldValidateToolInstanceTypeAvailableForAnyRegion() {
        final Tool tool = generateTool(TEST_GROUP_ID1);
        tool.setInstanceType(null);
        tool.setToolGroupId(firstToolGroup.getId());
        final Tool createdTool = toolManager.create(tool, true);

        createdTool.setInstanceType(TEST_ANOTHER_REGION_ALLOWED_INSTANCE_TYPE);
        toolManager.updateTool(createdTool);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void updateToolShouldNotValidateToolInstanceTypeIfItIsNotSpecified() {
        final Tool tool = generateTool(TEST_GROUP_ID1);
        tool.setToolGroupId(firstToolGroup.getId());
        final Tool createdTool = toolManager.create(tool, true);

        createdTool.setInstanceType(null);
        toolManager.updateTool(createdTool);
    }

    @Test(expected = IllegalArgumentException.class)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void updateToolShouldThrowIfInstanceTypeIsNotAllowed() {
        final Tool tool = generateTool(TEST_GROUP_ID1);
        tool.setToolGroupId(firstToolGroup.getId());
        final Tool createdTool = toolManager.create(tool, true);

        createdTool.setInstanceType(TEST_NOT_ALLOWED_INSTANCE_TYPE);
        toolManager.updateTool(createdTool);
    }

    @Test(expected = IllegalArgumentException.class)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void assertThatToolUniqueAcrossRegistriesShouldThrowsWhenToolAlreadyExistInOtherReg() {
        Tool tool = generateTool(TEST_GROUP_ID1);

        tool.setRegistry(firstRegistry.getPath());
        tool.setToolGroupId(firstToolGroup.getId());
        toolManager.create(tool, true);

        toolManager.assertThatToolUniqueAcrossRegistries(TEST_IMAGE, secondRegistry.getPath());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void assertThatToolUniqueAcrossRegistriesShouldPass() {
        Tool tool = generateTool(TEST_GROUP_ID1);

        tool.setRegistry(firstRegistry.getPath());
        tool.setToolGroupId(firstToolGroup.getId());
        toolManager.create(tool, true);

        toolManager.assertThatToolUniqueAcrossRegistries(TEST_IMAGE, firstRegistry.getPath());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void assertThatToolUniqueAcrossRegistriesShouldPass2() {
        toolManager.assertThatToolUniqueAcrossRegistries(TEST_IMAGE, firstRegistry.getPath());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void loadAllToolsShouldLoadAllToolsWithoutFilters()  {
        Tool tool = generateTool(TEST_GROUP_ID1);

        tool.setRegistry(firstRegistry.getPath());
        tool.setToolGroupId(firstToolGroup.getId());
        toolManager.create(tool, true);

        tool.setRegistry(secondRegistry.getPath());
        tool.setImage(TEST_IMAGE_2);
        tool.setToolGroup(TEST_GROUP_ID2);
        tool.setToolGroupId(secondToolGroup.getId());
        toolManager.create(tool, true);

        // without filter by registry
        List<Tool> tools = toolManager.loadAllTools(null, null);
        Assert.assertEquals(2, tools.size());

        // with filter by registry
        tools = toolManager.loadAllTools(firstRegistry.getPath(), null);
        Assert.assertEquals(1, tools.size());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void loadAllToolsShouldLoadAllToolsWithFiltersCorrectly()  {
        Tool tool = generateTool(TEST_GROUP_ID1);

        tool.setToolGroupId(firstToolGroup.getId());
        tool.setLabels(generateLabels(LABEL_1));
        toolManager.create(tool, true);

        tool.setToolGroup(TEST_GROUP_ID2);
        tool.setToolGroupId(secondToolGroup.getId());
        tool.setLabels(generateLabels(LABEL_2));
        tool.setImage(TEST_IMAGE_2);
        toolManager.create(tool, true);

        // without filters
        List<Tool> tools = toolManager.loadAllTools();
        Assert.assertEquals(2, tools.size());

        // with filter by labels and registry (1 filters - 1 result)
        tools = toolManager.loadAllTools(secondRegistry.getPath(), generateLabels(LABEL_2));
        Assert.assertEquals(1, tools.size());

        // with filter by labels and registry (2 filters - 1 result)
        tools = toolManager.loadAllTools(secondRegistry.getPath(), generateLabels(LABEL_1, LABEL_2));
        Assert.assertEquals(1, tools.size());

        // with filter by labels and registry (wrong filter - no result)
        tools = toolManager.loadAllTools(secondRegistry.getPath(), generateLabels(LABEL_1));
        Assert.assertEquals(0, tools.size());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void deleteToolShouldDeleteToolByRegistryAndImage()  {
        Tool tool = generateTool(TEST_GROUP_ID1);

        tool.setToolGroupId(firstToolGroup.getId());
        toolManager.create(tool, true);

        Assert.assertEquals(1, toolManager.loadAllTools().size());
        toolManager.delete(firstRegistry.getPath(), tool.getImage(), true);
        Assert.assertEquals(0, toolManager.loadAllTools().size());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void deleteToolShouldDeleteToolByOnlyImageIfItContainsOnlyOneToolWithImage()  {
        Tool tool = generateTool(TEST_GROUP_ID1);

        tool.setToolGroupId(firstToolGroup.getId());
        toolManager.create(tool, true);

        Assert.assertEquals(1, toolManager.loadAllTools().size());
        toolManager.delete(null, tool.getImage(), true);
        Assert.assertEquals(0, toolManager.loadAllTools().size());
    }

    @Test(expected = IllegalArgumentException.class)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void deleteToolShouldThrownExceptionIfRegistryWasntProvidedAndItHasTwoToolsWithImage()  {
        Tool tool = generateTool(TEST_GROUP_ID1);

        tool.setRegistry(firstRegistry.getPath());
        toolManager.create(tool, true);

        tool.setRegistry(secondRegistry.getPath());
        toolManager.create(tool, true);

        Assert.assertEquals(2, toolManager.loadAllTools().size());
        toolManager.delete(null, tool.getImage(), true);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void deleteToolShouldWorkIfToolHasScannedVersion()  {
        Tool tool = generateTool(TEST_GROUP_ID1);
        tool.setToolGroupId(firstToolGroup.getId());
        tool.setRegistry(firstRegistry.getPath());

        toolManager.create(tool, true);

        toolManager.updateToolVersionScanStatus(
                tool.getId(), ToolScanStatus.COMPLETED, new Date(), LATEST_TAG,
                new ToolOSVersion(TEST, TEST), LAYER_REF, DIGEST
        );
        toolManager.updateToolVulnerabilities(Collections.emptyList(), tool.getId(), LATEST_TAG);
        toolManager.updateToolDependencies(Collections.emptyList(), tool.getId(), LATEST_TAG);
        Assert.assertNotNull(toolManager.load(tool.getId()));
        toolManager.delete(null, tool.getImage(), true);
        Assert.assertNull(toolManager.load(tool.getId()));
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void loadToolShouldAcceptOptionalTag() {
        Tool tool = generateTool(TEST_GROUP_ID1);

        tool.setToolGroupId(firstToolGroup.getId());
        toolManager.create(tool, true);

        Tool loaded = toolManager.loadByNameOrId(firstRegistry.getPath() + "/" + tool.getImage() + ":" + "tag");

        Assert.assertEquals(tool.getImage() + ":" + "tag", loaded.getImage());
        Assert.assertEquals(TEST_REPO, loaded.getRegistry());
    }

    @Test(expected = IllegalArgumentException.class)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void loadToolShouldThrowExceptionWhenTwoTagsGiven() {
        Tool tool = generateTool(TEST_GROUP_ID1);

        tool.setRegistry(firstRegistry.getPath());
        toolManager.create(tool, true);

        toolManager.loadByNameOrId(firstRegistry.getPath() + "/" + tool.getImage() + ":tag1" + ":tag2");
    }

    @Test()
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testUpdateToolScanStatus() {
        Tool tool = generateTool(TEST_GROUP_ID1);

        tool.setToolGroupId(firstToolGroup.getId());
        toolManager.create(tool, true);

        String layerRef = "layerref";
        String digest = "digest";
        Date now = new Date();
        ToolScanStatus status = ToolScanStatus.COMPLETED;

        toolManager.updateToolVersionScanStatus(tool.getId(), status, now, LATEST_TAG, new ToolOSVersion(TEST, TEST),
                layerRef, digest);
        toolManager.updateWhiteListWithToolVersionStatus(tool.getId(), LATEST_TAG, true);
        ToolVersionScanResult versionScan = toolManager.loadToolVersionScan(
                tool.getId(), LATEST_TAG).get();
        Assert.assertEquals(status, versionScan.getStatus());
        Assert.assertEquals(now, versionScan.getScanDate());
        Assert.assertEquals(now, versionScan.getSuccessScanDate());
        Assert.assertEquals(layerRef, versionScan.getLastLayerRef());

        layerRef = "newlayerref";
        digest = "newdigest";
        now = new Date();

        toolManager.updateToolVersionScanStatus(tool.getId(), status, now, LATEST_TAG, new ToolOSVersion(TEST, TEST),
                layerRef, digest);
        Assert.assertEquals(1, toolManager.loadToolScanResult(tool).getToolVersionScanResults().values().size());
        versionScan = toolManager.loadToolVersionScan(tool.getId(), LATEST_TAG).get();
        Assert.assertEquals(now, versionScan.getScanDate());
        Assert.assertEquals(now, versionScan.getSuccessScanDate());
        Assert.assertEquals(layerRef, versionScan.getLastLayerRef());
        Assert.assertFalse(versionScan.isFromWhiteList());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void loadToolVesrionScanShouldCorrectCalculateIfToolOSVersionIsAllowed() {
        Preference toolOSPref = SystemPreferences.DOCKER_SECURITY_TOOL_OS.toPreference();
        toolOSPref.setValue("centos:6,ubuntu:14.04");
        preferenceManager.update(Collections.singletonList(toolOSPref));

        String latestVersion = LATEST_TAG, testRef = "testRef", prevVersion = "prev";
        Date scanDate = new Date();

        Mockito.doReturn(Arrays.asList(latestVersion, prevVersion)).when(dockerClient).getImageTags(any(), anyString());

        Tool tool = generateTool(TEST_GROUP_ID1);

        tool.setToolGroupId(firstToolGroup.getId());
        toolManager.create(tool, true);

        toolManager.updateToolVersionScanStatus(tool.getId(), ToolScanStatus.COMPLETED, scanDate,
                latestVersion, new ToolOSVersion(CENTOS, CENTOS_VERSION), testRef, testRef);

        ToolScanResult loaded = toolManager.loadToolScanResult(tool);
        ToolOSVersion toolOSVersion = loaded.getToolVersionScanResults().get(LATEST_TAG).getToolOSVersion();
        Assert.assertNotNull(toolOSVersion);
        Assert.assertEquals(CENTOS, toolOSVersion.getDistribution());
        Assert.assertEquals(CENTOS_VERSION, toolOSVersion.getVersion());
        Assert.assertTrue(toolOSVersion.getIsAllowed());

        toolOSPref = SystemPreferences.DOCKER_SECURITY_TOOL_OS.toPreference();
        toolOSPref.setValue("ubuntu:14.04");
        preferenceManager.update(Collections.singletonList(toolOSPref));
        loaded = toolManager.loadToolScanResult(tool);
        toolOSVersion = loaded.getToolVersionScanResults().get(LATEST_TAG).getToolOSVersion();
        Assert.assertNotNull(toolOSVersion);
        Assert.assertEquals(CENTOS, toolOSVersion.getDistribution());
        Assert.assertEquals(CENTOS_VERSION, toolOSVersion.getVersion());
        Assert.assertFalse(toolOSVersion.getIsAllowed());
    }

    @Test()
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testLoadToolScanResult() {
        String latestVersion = LATEST_TAG, testRef = "testRef", prevVersion = "prev";
        Date scanDate = new Date();

        Mockito.doReturn(Arrays.asList(latestVersion, prevVersion)).when(dockerClient).getImageTags(any(), anyString());

        Tool tool = generateTool(TEST_GROUP_ID1);

        tool.setToolGroupId(firstToolGroup.getId());
        toolManager.create(tool, true);

        toolManager.updateToolVersionScanStatus(tool.getId(), ToolScanStatus.COMPLETED, scanDate,
                latestVersion, new ToolOSVersion(TEST, TEST), testRef, testRef);

        ToolScanResult loaded = toolManager.loadToolScanResult(tool);
        Assert.assertEquals(
                ToolScanStatus.COMPLETED,
                loaded.getToolVersionScanResults().get(latestVersion).getStatus()
        );

        Assert.assertEquals(scanDate, loaded.getToolVersionScanResults().get(latestVersion).getSuccessScanDate());
        Assert.assertEquals(scanDate, loaded.getToolVersionScanResults().get(latestVersion).getScanDate());

        Optional<String> loadedRef = toolManager.loadToolVersionScan(tool.getId(), latestVersion)
                .map(ToolVersionScanResult::getLastLayerRef);
        Assert.assertTrue(loadedRef.isPresent());
        Assert.assertEquals(testRef, loadedRef.get());

        Optional<String> loadedDigest = toolManager.loadToolVersionScan(tool.getId(), latestVersion)
                .map(ToolVersionScanResult::getDigest);
        Assert.assertTrue(loadedDigest.isPresent());
        Assert.assertEquals(testRef, loadedDigest.get());

        // check that we will get empty ToolVersionScanResult for not scanned version
        Assert.assertEquals(
                ToolScanStatus.NOT_SCANNED,
                loaded.getToolVersionScanResults().get(prevVersion).getStatus()
        );
        Assert.assertEquals(null, loaded.getToolVersionScanResults().get(prevVersion).getSuccessScanDate());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void getTagFromImageNameTest() {
        String repoImageTag = "repo/image:tag";
        String repoImage = "repo/image";
        String imageTag = "image:tag";
        String image = "image";
        Assert.assertEquals(TAG, toolManager.getTagFromImageName(repoImageTag));
        Assert.assertEquals(LATEST_TAG, toolManager.getTagFromImageName(repoImage));
        Assert.assertEquals(TAG, toolManager.getTagFromImageName(imageTag));
        Assert.assertEquals(LATEST_TAG, toolManager.getTagFromImageName(image));
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void testDeleteToolWithIcon() {
        Tool tool = generateTool(TEST_GROUP_ID1);

        tool.setToolGroupId(firstToolGroup.getId());
        tool.setRegistry(firstRegistry.getPath());
        toolManager.create(tool, true);

        byte[] randomBytes = new byte[10];
        new Random().nextBytes(randomBytes);
        String testFileName = TEST;

        toolManager.updateToolIcon(tool.getId(), testFileName, randomBytes);
        Assert.assertNotNull(toolManager.loadToolIcon(tool.getId()));

        toolManager.delete(tool.getRegistry(), tool.getImage(), false);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void testInsertToolScanStatusFailed() {

        Tool tool = generateTool(TEST_GROUP_ID1);

        tool.setToolGroupId(firstToolGroup.getId());
        toolManager.create(tool, true);

        String layerRef = "layerref";
        String digest = "digest";
        Date now = new Date();
        ToolScanStatus status = ToolScanStatus.FAILED;

        toolManager.updateToolVersionScanStatus(tool.getId(), status, now, LATEST_TAG, layerRef, digest);
        ToolVersionScanResult versionScan = toolManager.loadToolVersionScan(
                tool.getId(), LATEST_TAG).get();
        Assert.assertEquals(status, versionScan.getStatus());
        Assert.assertEquals(now, versionScan.getScanDate());
        Assert.assertEquals(null, versionScan.getSuccessScanDate());
        Assert.assertEquals(layerRef, versionScan.getLastLayerRef());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void testUpdateToolScanStatusWithFail() {

        Tool tool = generateTool(TEST_GROUP_ID1);

        tool.setToolGroupId(firstToolGroup.getId());
        toolManager.create(tool, true);

        String layerRef = "layerref";
        String digest = "digest";
        Date scanDate = new Date();
        ToolScanStatus status = ToolScanStatus.COMPLETED;

        toolManager.updateToolVersionScanStatus(
                tool.getId(), status, scanDate, LATEST_TAG, new ToolOSVersion(TEST, TEST), layerRef, digest);
        ToolVersionScanResult versionScan =
                toolManager.loadToolVersionScan(tool.getId(), LATEST_TAG).get();
        Assert.assertEquals(status, versionScan.getStatus());
        Assert.assertEquals(scanDate, versionScan.getScanDate());
        Assert.assertEquals(scanDate, versionScan.getSuccessScanDate());

        status = ToolScanStatus.FAILED;
        layerRef = "newlayerref";
        digest = "newdigest";
        Date newScanDate = new Date();

        toolManager.updateToolVersionScanStatus(
                tool.getId(), status, newScanDate, LATEST_TAG, layerRef, digest);
        Assert.assertEquals(1, toolManager.loadToolScanResult(tool).getToolVersionScanResults().values().size());
        versionScan = toolManager.loadToolVersionScan(tool.getId(), LATEST_TAG).get();
        Assert.assertEquals(newScanDate, versionScan.getScanDate());
        Assert.assertEquals(scanDate, versionScan.getSuccessScanDate());
    }

    private List<String> generateLabels(String... labels) {
        List<String> list = new ArrayList<>();
        list.addAll(Arrays.asList(labels));
        return list;
    }

    private Tool generateTool(String toolGroupName) {
        Tool tool = new Tool();
        tool.setImage(TEST_IMAGE);
        tool.setRam(TEST_RAM);
        tool.setCpu(TEST_CPU);
        tool.setOwner(TEST_USER);
        tool.setInstanceType(TEST_ALLOWED_INSTANCE_TYPE);
        tool.setToolGroup(toolGroupName);
        return tool;
    }

}
