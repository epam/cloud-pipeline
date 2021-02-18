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

package com.epam.pipeline.dao.tool;

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.dao.docker.DockerRegistryDao;
import com.epam.pipeline.entity.configuration.ConfigurationEntry;
import com.epam.pipeline.entity.configuration.PipeConfValueVO;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.docker.ToolVersion;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.ToolGroup;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;

@Transactional
public class ToolVersionDaoTest extends AbstractSpringTest {
    private static final String TEST_DIGEST = "sha256:aaa";
    private static final String TEST_DIGEST_2 = "sha256:bbb";
    private static final Long TEST_SIZE = 123L;
    private static final Date TEST_LAST_MODIFIED_DATE = new Date();
    private static final String TEST_VERSION = "latest";
    private static final String TEST_VERSION_2 = "2";
    private static final String TEST_OWNER = "owner";
    private static final String TEST_REGISTRY_PATH = "registry_path";
    private static final String ANOTHER_TEST_REGISTRY_PATH = "another_registry_path";
    private static final String TEST_GROUP_NAME = "group";
    private static final String TEST_IMAGE = "image";
    private static final String TEST_CPU = "3000m";
    private static final String TEST_RAM = "512MB";
    private static final String TEST_PARAMETER_NAME = "name";
    private static final String TEST_PARAMETER_VALUE = "value";
    private static final String TEST_CMD_TEMPLATE_1 = "cmd";
    private static final String TEST_CMD_TEMPLATE_2 = "cmd2";
    private static final Integer TEST_NODE_COUNT = 3;

    @Autowired
    private ToolVersionDao toolVersionDao;
    @Autowired
    private ToolDao toolDao;
    @Autowired
    private DockerRegistryDao dockerRegistryDao;
    @Autowired
    private ToolGroupDao toolGroupDao;

    private Tool tool;
    private Tool symlink;
    private ToolVersion toolVersion1;
    private ToolVersion toolVersion2;

    @Before
    public void setUp() {
        DockerRegistry dockerRegistry1 = new DockerRegistry();
        dockerRegistry1.setPath(TEST_REGISTRY_PATH);
        dockerRegistry1.setOwner(TEST_OWNER);
        dockerRegistryDao.createDockerRegistry(dockerRegistry1);
        
        DockerRegistry dockerRegistry2 = new DockerRegistry();
        dockerRegistry2.setPath(ANOTHER_TEST_REGISTRY_PATH);
        dockerRegistry2.setOwner(TEST_OWNER);
        dockerRegistryDao.createDockerRegistry(dockerRegistry2);

        ToolGroup toolGroup1 = new ToolGroup();
        toolGroup1.setName(TEST_GROUP_NAME);
        toolGroup1.setRegistryId(dockerRegistry1.getId());
        toolGroup1.setOwner(TEST_OWNER);
        toolGroupDao.createToolGroup(toolGroup1);

        ToolGroup toolGroup2 = new ToolGroup();
        toolGroup2.setName(TEST_GROUP_NAME);
        toolGroup2.setRegistryId(dockerRegistry2.getId());
        toolGroup2.setOwner(TEST_OWNER);
        toolGroupDao.createToolGroup(toolGroup2);

        tool = new Tool();
        tool.setImage(TEST_IMAGE);
        tool.setCpu(TEST_CPU);
        tool.setRam(TEST_RAM);
        tool.setRegistryId(dockerRegistry1.getId());
        tool.setOwner(TEST_OWNER);
        tool.setToolGroupId(toolGroup1.getId());
        toolDao.createTool(tool);
        
        symlink = new Tool();
        symlink.setImage(TEST_IMAGE);
        symlink.setCpu(TEST_CPU);
        symlink.setRam(TEST_RAM);
        symlink.setRegistryId(dockerRegistry2.getId());
        symlink.setOwner(TEST_OWNER);
        symlink.setToolGroupId(toolGroup2.getId());
        symlink.setLink(tool.getId());
        toolDao.createTool(symlink);

        ConfigurationEntry configurationEntry = new ConfigurationEntry();
        configurationEntry.setName(ConfigurationEntry.DEFAULT);
        toolVersion1 = ToolVersion
                .builder()
                .digest(TEST_DIGEST)
                .size(TEST_SIZE)
                .version(TEST_VERSION)
                .modificationDate(TEST_LAST_MODIFIED_DATE)
                .toolId(tool.getId())
                .settings(Collections.singletonList(configurationEntry))
                .build();
        toolVersion2 = ToolVersion
                .builder()
                .digest(TEST_DIGEST_2)
                .size(TEST_SIZE)
                .version(TEST_VERSION_2)
                .modificationDate(TEST_LAST_MODIFIED_DATE)
                .toolId(tool.getId())
                .settings(Collections.singletonList(configurationEntry))
                .build();
    }

    @Test
    @Transactional
    public void shouldCRUDForSingleVersion() {
        Long toolId = tool.getId();
        toolVersionDao.createToolVersion(toolVersion1);

        ToolVersion actual = toolVersionDao.loadToolVersion(toolId, TEST_VERSION).orElse(null);
        validateToolVersion(actual, TEST_DIGEST, TEST_SIZE, TEST_VERSION, TEST_LAST_MODIFIED_DATE, toolId);

        ToolVersion toolVersionWithSameVersion = ToolVersion
                .builder()
                .digest(TEST_DIGEST_2)
                .size(TEST_SIZE)
                .version(TEST_VERSION)
                .modificationDate(TEST_LAST_MODIFIED_DATE)
                .toolId(tool.getId())
                .id(toolVersion1.getId())
                .build();

        toolVersionDao.updateToolVersion(toolVersionWithSameVersion);
        actual = toolVersionDao.loadToolVersion(toolId, TEST_VERSION).orElse(null);
        validateToolVersion(actual, TEST_DIGEST_2, TEST_SIZE, TEST_VERSION, TEST_LAST_MODIFIED_DATE, toolId);

        toolVersionDao.deleteToolVersion(toolId, TEST_VERSION);
        actual = toolVersionDao.loadToolVersion(toolId, TEST_VERSION).orElse(null);
        assertThat(actual).isNull();
    }

    @Test
    @Transactional
    public void shouldCRUDForListOfVersions() {
        Long toolId = tool.getId();
        toolVersionDao.createToolVersion(toolVersion1);
        toolVersionDao.createToolVersion(toolVersion2);

        ToolVersion actual = toolVersionDao.loadToolVersion(toolId, TEST_VERSION).orElse(null);
        validateToolVersion(actual, TEST_DIGEST, TEST_SIZE, TEST_VERSION, TEST_LAST_MODIFIED_DATE, toolId);
        actual = toolVersionDao.loadToolVersion(toolId, TEST_VERSION_2).orElse(null);
        validateToolVersion(actual, TEST_DIGEST_2, TEST_SIZE, TEST_VERSION_2, TEST_LAST_MODIFIED_DATE, toolId);

        final Map<String, ToolVersion> versions = toolVersionDao.loadToolVersions(toolId,
                Arrays.asList(TEST_VERSION, TEST_VERSION_2));
        assertThat(versions).hasSize(2);

        toolVersionDao.deleteToolVersions(toolId);
        actual = toolVersionDao.loadToolVersion(toolId, TEST_VERSION).orElse(null);
        assertThat(actual).isNull();
        actual = toolVersionDao.loadToolVersion(toolId, TEST_VERSION_2).orElse(null);
        assertThat(actual).isNull();
    }

    @Test
    @Transactional
    public void shouldCRUDWithSettings() {
        Long toolId = tool.getId();
        PipelineConfiguration pipelineConfiguration = new PipelineConfiguration();
        pipelineConfiguration.setCmdTemplate(TEST_CMD_TEMPLATE_1);
        pipelineConfiguration.setNodeCount(TEST_NODE_COUNT);
        pipelineConfiguration.setParameters(
                Collections.singletonMap(TEST_PARAMETER_NAME, new PipeConfValueVO(TEST_PARAMETER_VALUE)));
        ConfigurationEntry configurationEntry = new ConfigurationEntry(pipelineConfiguration);
        List<ConfigurationEntry> settings = Collections.singletonList(configurationEntry);
        toolVersion1.setSettings(settings);
        toolVersionDao.createToolVersionWithSettings(toolVersion1);

        ToolVersion actual = toolVersionDao.loadToolVersionWithSettings(toolId, TEST_VERSION).orElse(null);
        validateToolVersionSettings(actual, configurationEntry, TEST_VERSION, toolId, TEST_PARAMETER_NAME);

        pipelineConfiguration.setCmdTemplate(TEST_CMD_TEMPLATE_2);
        configurationEntry.setConfiguration(pipelineConfiguration);
        settings = Collections.singletonList(configurationEntry);
        toolVersion1.setSettings(settings);
        toolVersionDao.updateToolVersionWithSettings(toolVersion1);
        actual = toolVersionDao.loadToolVersionWithSettings(toolId, TEST_VERSION).orElse(null);
        validateToolVersionSettings(actual, configurationEntry, TEST_VERSION, toolId, TEST_PARAMETER_NAME);

        toolVersionDao.deleteToolVersions(toolId);
        actual = toolVersionDao.loadToolVersion(toolId, TEST_VERSION).orElse(null);
        assertThat(actual).isNull();
    }

    @Test
    @Transactional
    public void testLoadToolVersionWorksForRegularTool() {
        final List<ToolVersion> expectedVersions = Arrays.asList(toolVersion1, toolVersion2);
        expectedVersions.forEach(toolVersionDao::createToolVersion);

        final List<ToolVersion> actualVersions = expectedVersions.stream()
                .map(version -> toolVersionDao.loadToolVersion(tool.getId(), version.getVersion()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());

        assertVersions(actualVersions, expectedVersions);
    }

    @Test
    @Transactional
    public void testLoadToolVersionWorksForSymlink() {
        final List<ToolVersion> expectedVersions = Arrays.asList(toolVersion1, toolVersion2);
        expectedVersions.forEach(toolVersionDao::createToolVersion);

        final List<ToolVersion> actualVersions = expectedVersions.stream()
                .map(version -> toolVersionDao.loadToolVersion(symlink.getId(), version.getVersion()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());

        assertVersions(actualVersions, expectedVersions);
    }

    @Test
    @Transactional
    public void testLoadToolVersionWithSettingsWorksForRegularTool() {
        final List<ToolVersion> expectedVersions = Arrays.asList(toolVersion1, toolVersion2);
        expectedVersions.forEach(toolVersionDao::createToolVersionWithSettings);

        final List<ToolVersion> actualVersions = expectedVersions.stream()
                .map(version -> toolVersionDao.loadToolVersionWithSettings(tool.getId(), version.getVersion()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());

        assertVersionsWithSettings(actualVersions, expectedVersions);
    }

    @Test
    @Transactional
    public void testLoadToolVersionWithSettingsWorksForSymlink() {
        final List<ToolVersion> expectedVersions = Arrays.asList(toolVersion1, toolVersion2);
        expectedVersions.forEach(toolVersionDao::createToolVersionWithSettings);

        final List<ToolVersion> actualVersions = expectedVersions.stream()
                .map(version -> toolVersionDao.loadToolVersionWithSettings(symlink.getId(), version.getVersion()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());

        assertVersionsWithSettings(actualVersions, expectedVersions);
    }

    @Test
    @Transactional
    public void testLoadToolWithSettingsWorksForRegularTool() {
        final List<ToolVersion> expectedVersions = Arrays.asList(toolVersion1, toolVersion2);
        expectedVersions.forEach(toolVersionDao::createToolVersionWithSettings);

        final List<ToolVersion> actualVersions = toolVersionDao.loadToolWithSettings(tool.getId());

        assertVersionsWithSettings(actualVersions, expectedVersions);
    }

    @Test
    @Transactional
    public void testLoadToolWithSettingsWorksForSymlink() {
        final List<ToolVersion> expectedVersions = Arrays.asList(toolVersion1, toolVersion2);
        expectedVersions.forEach(toolVersionDao::createToolVersionWithSettings);

        final List<ToolVersion> actualVersions = toolVersionDao.loadToolWithSettings(symlink.getId());

        assertVersionsWithSettings(actualVersions, expectedVersions);
    }

    private void assertVersions(final List<ToolVersion> actualVersions, final List<ToolVersion> expectedVersions) {
        actualVersions.sort(Comparator.comparing(ToolVersion::getId));
        expectedVersions.sort(Comparator.comparing(ToolVersion::getId));
        Assert.assertThat(actualVersions.size(), is(expectedVersions.size()));
        Assert.assertThat(actualVersions.size(), greaterThan(0));
        for (int i = 0; i < actualVersions.size(); i++) {
            final ToolVersion actualVersion = actualVersions.get(i);
            final ToolVersion expectedVersion = expectedVersions.get(i);
            assertVersions(actualVersion, expectedVersion);
        }
    }

    private void assertVersionsWithSettings(final List<ToolVersion> actualVersions, 
                                            final List<ToolVersion> expectedVersions) {
        actualVersions.sort(Comparator.comparing(ToolVersion::getId));
        expectedVersions.sort(Comparator.comparing(ToolVersion::getId));
        Assert.assertThat(actualVersions.size(), is(expectedVersions.size()));
        Assert.assertThat(actualVersions.size(), greaterThan(0));
        for (int i = 0; i < actualVersions.size(); i++) {
            final ToolVersion actualVersion = actualVersions.get(i);
            final ToolVersion expectedVersion = expectedVersions.get(i);
            assertVersionsWithSettings(actualVersion, expectedVersion);
        }
    }

    private void assertVersions(final ToolVersion actualVersion, final ToolVersion expectedVersion) {
        Assert.assertThat(actualVersion.getId(), is(expectedVersion.getId()));
        Assert.assertThat(actualVersion.getToolId(), is(expectedVersion.getToolId()));
        Assert.assertThat(actualVersion.getVersion(), is(expectedVersion.getVersion()));
    }

    private void assertVersionsWithSettings(final ToolVersion actualVersion, final ToolVersion expectedVersion) {
        assertVersions(actualVersion, expectedVersion);
        assertSettings(actualVersion.getSettings(), expectedVersion.getSettings());
    }

    private void assertSettings(final List<ConfigurationEntry> actualSettings,
                                final List<ConfigurationEntry> expectedSettings) {
        Assert.assertThat(actualSettings.size(), is(expectedSettings.size()));
        Assert.assertThat(actualSettings.size(), greaterThan(0));
        for (int i = 0; i < actualSettings.size(); i++) {
            final ConfigurationEntry actualConfiguration = actualSettings.get(i);
            final ConfigurationEntry expectedConfiguration = expectedSettings.get(i);
            Assert.assertThat(actualConfiguration.getName(), is(expectedConfiguration.getName()));
        }
    }

    private static void validateToolVersion(ToolVersion actual, String digest, Long size, String version,
                                            Date modificationDate, Long toolId) {
        assertThat(actual)
                .isNotNull()
                .hasFieldOrPropertyWithValue("digest", digest)
                .hasFieldOrPropertyWithValue("size", size)
                .hasFieldOrPropertyWithValue("version", version)
                .hasFieldOrPropertyWithValue("modificationDate", modificationDate)
                .hasFieldOrPropertyWithValue("toolId", toolId);
    }

    private static void validateToolVersionSettings(ToolVersion actual, ConfigurationEntry settings,
                                                    String version, Long toolId, String parameterName) {
        assertThat(actual)
                .isNotNull()
                .hasFieldOrPropertyWithValue("version", version)
                .hasFieldOrPropertyWithValue("toolId", toolId)
                .hasFieldOrProperty("settings");
        assertThat(actual.getSettings())
                .hasSize(1);
        assertThat(actual.getSettings().get(0))
                .isNotNull()
                .hasFieldOrProperty("configuration");
        PipelineConfiguration actualConfiguration = actual.getSettings().get(0).getConfiguration();
        PipelineConfiguration expectedConfiguration = settings.getConfiguration();
        assertThat(actualConfiguration)
                .isNotNull()
                .hasFieldOrPropertyWithValue("cmdTemplate", expectedConfiguration.getCmdTemplate())
                .hasFieldOrPropertyWithValue("nodeCount", expectedConfiguration.getNodeCount())
                .hasFieldOrProperty("parameters");
        assertThat(actualConfiguration.getParameters())
                .hasSize(1);
        assertThat(actualConfiguration.getParameters().get(parameterName))
                .isEqualToComparingFieldByFieldRecursively(settings.getConfiguration()
                        .getParameters().get(parameterName));
    }
}
