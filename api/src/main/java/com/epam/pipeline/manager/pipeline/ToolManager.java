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

package com.epam.pipeline.manager.pipeline;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.tool.ToolDao;
import com.epam.pipeline.dao.tool.ToolVulnerabilityDao;
import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.contextual.ContextualPreferenceExternalResource;
import com.epam.pipeline.entity.contextual.ContextualPreferenceLevel;
import com.epam.pipeline.entity.docker.ImageDescription;
import com.epam.pipeline.entity.docker.ManifestV2;
import com.epam.pipeline.entity.docker.ToolDescription;
import com.epam.pipeline.entity.docker.ToolVersion;
import com.epam.pipeline.entity.docker.ToolVersionAttributes;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.ToolGroup;
import com.epam.pipeline.entity.pipeline.ToolScanStatus;
import com.epam.pipeline.entity.pipeline.ToolWithIssuesCount;
import com.epam.pipeline.entity.scan.ToolDependency;
import com.epam.pipeline.entity.scan.ToolScanResult;
import com.epam.pipeline.entity.scan.ToolVersionScanResult;
import com.epam.pipeline.entity.scan.Vulnerability;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.exception.docker.DockerConnectionException;
import com.epam.pipeline.manager.cluster.InstanceOfferManager;
import com.epam.pipeline.manager.docker.DockerClient;
import com.epam.pipeline.manager.docker.DockerRegistryManager;
import com.epam.pipeline.manager.docker.ToolVersionManager;
import com.epam.pipeline.manager.docker.scan.ToolScanManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.security.SecuredEntityManager;
import com.epam.pipeline.manager.security.acl.AclSync;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@AclSync
public class ToolManager implements SecuredEntityManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ToolManager.class);

    private static final Pattern REPOSITORY_AND_IMAGE = Pattern.compile("^(.*)\\/(.*\\/.*)$");
    private static final String TAG_DELIMITER = ":";
    private static final String LATEST_TAG = "latest";
    private static final int KB_SIZE = 1000;
    private static final long SECONDS_IN_HOUR = 3600;

    @Autowired
    private ToolDao toolDao;

    @Autowired
    private ToolVulnerabilityDao toolVulnerabilityDao;

    @Autowired
    private MessageHelper messageHelper;

    @Autowired
    private DockerRegistryManager dockerRegistryManager;

    @Autowired
    private AuthManager authManager;

    @Autowired
    private ToolGroupManager toolGroupManager;

    @Autowired
    private ToolScanManager toolScanManager;

    @Autowired
    private PreferenceManager preferenceManager;

    @Autowired
    private InstanceOfferManager instanceOfferManager;

    @Autowired
    private ToolVersionManager toolVersionManager;

    /**
     * Creates a new Tool in the requested group
     * @param tool a tool to create
     * @return newly created group
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public Tool create(final Tool tool, final boolean checkExistence) {
        Assert.notNull(tool.getImage(), messageHelper.getMessage(MessageConstants.ERROR_PARAMETER_REQUIRED,
                "image", Tool.class.getSimpleName()));
        Assert.notNull(tool.getCpu(), messageHelper.getMessage(MessageConstants.ERROR_PARAMETER_REQUIRED,
                "cpu", Tool.class.getSimpleName()));
        Assert.notNull(tool.getRam(), messageHelper.getMessage(MessageConstants.ERROR_PARAMETER_REQUIRED,
                "ram", Tool.class.getSimpleName()));
        Assert.notNull(tool.getToolGroupId(), messageHelper.getMessage(MessageConstants.ERROR_PARAMETER_REQUIRED,
                 "toolGroupId", Tool.class.getSimpleName()));

        ToolGroup group = toolGroupManager.load(tool.getToolGroupId());
        tool.setParent(group);
        tool.setRegistryId(group.getRegistryId());
        tool.setToolGroupId(group.getId());
        if (!StringUtils.hasText(tool.getOwner())) {
            tool.setOwner(authManager.getAuthorizedUser());
        }

        Assert.isTrue(isToolUniqueInGroup(tool.getImage(), group.getId()),
                      messageHelper.getMessage(MessageConstants.ERROR_TOOL_ALREADY_EXIST, tool.getImage(),
                                               group.getName()));
        validateInstanceType(tool);
        DockerRegistry registry = dockerRegistryManager.load(group.getRegistryId());
        if (checkExistence) {
            try {
                List<String> tags = dockerRegistryManager.loadImageTags(registry, tool.getImage());
                Assert.isTrue(!CollectionUtils.isEmpty(tags),
                        messageHelper.getMessage(MessageConstants.ERROR_TOOL_IMAGE_UNAVAILABLE, tool.getImage()));
            } catch (DockerConnectionException e) {
                throw new IllegalArgumentException(
                        messageHelper.getMessage(MessageConstants.ERROR_TOOL_IMAGE_UNAVAILABLE, tool.getImage()));
            }
        }

        toolDao.createTool(tool);
        try {
            List<String> tags = dockerRegistryManager.loadImageTags(registry, tool.getImage());
            for (String tag : tags) {
                String digest = dockerRegistryManager.getDockerClient(registry, tool.getImage())
                        .getVersionAttributes(registry, tool.getImage(), tag).getDigest();
                updateToolVersionScanStatus(tool.getId(), ToolScanStatus.NOT_SCANNED,
                        DateUtils.now(), tag, null, digest);
            }
        } catch (DockerConnectionException e) {
            throw new IllegalArgumentException(
                    messageHelper.getMessage(MessageConstants.ERROR_TOOL_IMAGE_UNAVAILABLE, tool.getImage()));
        }
        return tool;
    }


    @Transactional(propagation = Propagation.REQUIRED)
    public Tool updateTool(Tool tool) {
        Tool loadedTool = toolDao.loadTool(tool.getRegistryId(), tool.getImage());
        Assert.notNull(loadedTool,
                messageHelper.getMessage(MessageConstants.ERROR_TOOL_NOT_FOUND, tool.getImage()));

        if (!StringUtils.isEmpty(tool.getCpu())) {
            loadedTool.setCpu(tool.getCpu());
        }
        if (!StringUtils.isEmpty(tool.getRam())) {
            loadedTool.setRam(tool.getRam());
        }
        validateInstanceType(tool);

        loadedTool.setInstanceType(tool.getInstanceType());
        loadedTool.setDisk(tool.getDisk());
        loadedTool.setDescription(tool.getDescription());
        loadedTool.setShortDescription(tool.getShortDescription());
        loadedTool.setLabels(tool.getLabels());
        loadedTool.setEndpoints(tool.getEndpoints());
        loadedTool.setDefaultCommand(tool.getDefaultCommand());

        toolDao.updateTool(loadedTool);
        return loadedTool;
    }


    /**
     * Loads all tools for the specified registry and labels.
     * @param registry registry name where tool is located, optional,
     *                 if it doesn't exist tools for all registries will be loaded.
     * @param labels labels of a tool, optional, if it doesn't exist filtering by labels will not applied.
     * @return list of all matched tools
     */
    @Transactional(propagation = Propagation.SUPPORTS)
    public List<Tool> loadAllTools(String registry, List<String> labels) {
        Long registryId = !StringUtils.isEmpty(registry)
                ? dockerRegistryManager.loadByNameOrId(registry).getId()
                : null;
        return toolDao.loadAllTools(registryId, labels);
    }

    @Override
    public Tool load(Long id) {
        return toolDao.loadTool(id);
    }

    @Override
    public AbstractSecuredEntity changeOwner(Long id, String owner) {
        Tool tool = toolDao.loadTool(id);
        tool.setOwner(owner);
        toolDao.updateTool(tool);
        return tool;
    }

    @Override public AclClass getSupportedClass() {
        return AclClass.TOOL;
    }

    /**
     * Loads all tools from a database.
     * @return list of all tools in database
     */
    public List<Tool> loadAllTools() {
        return loadAllTools(null, null);
    }

    public List<Tool> loadToolsByGroup(Long groupId) {
        return toolDao.loadToolsByGroup(groupId);
    }

    public List<ToolWithIssuesCount> loadToolsWithIssuesCountByGroup(Long groupId) {
        return toolDao.loadToolsWithIssuesCountByGroup(groupId);
    }

    /**
     * Tries to parse repository and image name from a string of pattern 'repo:5000/image'
     * @param identifier
     * @return
     */
    @Override
    public Tool loadByNameOrId(String identifier) {
        if (NumberUtils.isDigits(identifier)) {
            Tool imageTool = toolDao.loadTool(Long.parseLong(identifier));
            if (imageTool != null) {
                return imageTool;
            }
        }

        Matcher matcher = REPOSITORY_AND_IMAGE.matcher(identifier);
        String repository = "";
        String imageName;
        String imageWithTag;

        if (matcher.find()) {
            repository = matcher.group(1);
            imageWithTag = matcher.group(2);
            imageName = getImageWithoutTag(imageWithTag);
        } else {
            imageWithTag = identifier;
            imageName = getImageWithoutTag(identifier);
        }

        if (!imageAndTagAreValid(imageWithTag)) {
            throw new IllegalArgumentException(
                    messageHelper.getMessage(MessageConstants.ERROR_INVALID_IMAGE_REPOSITORY, repository)
            );
        }

        Tool imageTool = fetchTool(repository, imageName);

        if ((!repository.isEmpty() && !repository.equals(imageTool.getRegistry()))) {
            throw new IllegalArgumentException(
                    messageHelper.getMessage(MessageConstants.ERROR_INVALID_IMAGE_REPOSITORY, repository)
            );
        }

        //return value with tag
        imageTool.setImage(imageWithTag);
        return imageTool;
    }

    public String getExternalToolName(String toolName) {
        Matcher matcher = REPOSITORY_AND_IMAGE.matcher(toolName);
        Assert.state(matcher.find(),
                messageHelper.getMessage(MessageConstants.ERROR_TOOL_INVALID_IMAGE, toolName));
        String registryPath = matcher.group(1);
        DockerRegistry dockerRegistry = dockerRegistryManager.loadByNameOrId(registryPath);
        Assert.state(StringUtils.hasText(dockerRegistry.getExternalUrl()),
                messageHelper.getMessage(MessageConstants.ERROR_DOCKER_REGISTRY_NO_EXTERNAL,
                        dockerRegistry.getPath()));
        return toolName.replace(registryPath, dockerRegistry.getExternalUrl());
    }

    @Override
    public Integer loadTotalCount() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<Tool> loadAllWithParents(Integer page, Integer pageSize) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Tool loadWithParents(final Long id) {
        return load(id);
    }

    /**
     * Load a Tool from the database
     * @param registry registry identifier
     * @param image Tool's image
     * @return the loaded Tool entity
     */
    @Transactional(propagation = Propagation.SUPPORTS)
    public Tool loadTool(String registry, final String image) {
        if (StringUtils.isEmpty(registry)) {
            return loadByNameOrId(image);
        } else {
            return fetchTool(registry, image);
        }
    }

    /**
     * Deletes a Tool from the database and from Docker Registry
     * @param registry registry identifier
     * @param image Tool's image
     * @param hard flag determines if the real image from Docker Registry should be deleted
     * @return the deleted Tool entity
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public Tool delete(String registry, final String image, boolean hard) {
        Tool tool = loadTool(registry, image);
        deleteToolWithDependent(tool, image, hard);
        return tool;
    }

    /**
     * Deletes a tools
     * @param tool to delete
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public Tool delete(final Tool tool) {
        deleteToolWithDependent(tool, tool.getImage(), false);
        return tool;
    }

    /**
     * Deletes Tool's version (a tag) from Docker Registry
     *
     * @param registry registry identifier
     * @param image Tool's image
     * @param version tag from registry
     * @return Tool entity, which version was deleted
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public Tool deleteToolVersion(String registry, final String image, String version) {
        Tool tool = loadTool(registry, image);
        deleteToolVersionScan(tool.getId(), version);
        toolVersionManager.deleteToolVersion(tool.getId(), version);
        DockerRegistry dockerRegistry = dockerRegistryManager.load(tool.getRegistryId());
        dockerRegistryManager.deleteImage(dockerRegistry, tool.getImage(), version);
        return tool;
    }

    public List<String> loadTags(Long id) {
        Tool tool = load(id);
        Assert.notNull(tool, messageHelper.getMessage(MessageConstants.ERROR_TOOL_NOT_FOUND, id));
        return dockerRegistryManager.loadImageTags(dockerRegistryManager.load(tool.getRegistryId()), tool);
    }

    public ImageDescription loadToolDescription(Long id, String tag) {
        Tool tool = load(id);
        Assert.notNull(tool, messageHelper.getMessage(MessageConstants.ERROR_TOOL_NOT_FOUND, id));
        return dockerRegistryManager.getImageDescription(
                dockerRegistryManager.load(tool.getRegistryId()), tool.getImage(), tag);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void updateToolVulnerabilities(List<Vulnerability> vulnerabilities, long toolId, String version) {
        LOGGER.debug("Update tool with id: " + toolId + " and version "
                + version + " with " + vulnerabilities.size() + " vulnerabilities");
        vulnerabilities.forEach(v -> v.setCreatedDate(new Date()));
        toolVulnerabilityDao.deleteVulnerabilities(toolId, version);
        toolVulnerabilityDao.createVulnerabilityRecords(vulnerabilities, toolId, version);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void updateToolDependencies(List<ToolDependency> dependencies, long toolId, String version) {
        LOGGER.debug("Update tool with id: " + toolId + " and version "
                + version + " with " + dependencies.size() + " dependencies");
        toolVulnerabilityDao.deleteDependencies(toolId, version);
        toolVulnerabilityDao.createDependencyRecords(dependencies, toolId, version);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void updateToolVersionScanStatus(long toolId, ToolScanStatus newStatus, Date scanDate,
                                            String version, String layerRef, String digest) {
        Optional<ToolVersionScanResult> prev = loadToolVersionScan(toolId, version);
        if(prev.isPresent()) {
            ToolVersionScanResult scanResult = prev.get();
            boolean whiteList = scanResult.isFromWhiteList();
            if (scanResult.getDigest() == null || !scanResult.getDigest().equals(digest)) {
                log.debug("Digests are not match! If version was from white list, it will be deleted!");
                whiteList = false;
            }
            toolVulnerabilityDao.updateToolVersionScan(toolId, version, layerRef, digest, newStatus,
                    scanDate, whiteList);
        } else {
            toolVulnerabilityDao.insertToolVersionScan(toolId, version, layerRef, digest, newStatus, scanDate);
        }
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public ToolVersionScanResult updateWhiteListWithToolVersionStatus(long toolId, String version,
                                                                      boolean fromWhiteList) {
        Optional<ToolVersionScanResult> toolVersionScanResult = loadToolVersionScan(toolId, version);
        if (!toolVersionScanResult.isPresent()) {
            toolVulnerabilityDao.insertToolVersionScan(toolId, version, null, null, ToolScanStatus.NOT_SCANNED,
                    DateUtils.now());
        }
        toolVulnerabilityDao.updateWhiteListWithToolVersion(toolId, version, fromWhiteList);
        return loadToolVersionScan(toolId, version).orElse(null);
    }

    public Optional<ToolVersionScanResult> loadToolVersionScan(long toolId, String version) {
        Optional<ToolVersionScanResult> result = toolVulnerabilityDao.loadToolVersionScan(toolId, version);
        result.ifPresent(scanResult -> {
            if (scanResult.getScanDate() != null) {
                int graceHours = preferenceManager.getPreference(SystemPreferences.DOCKER_SECURITY_TOOL_GRACE_HOURS);
                scanResult.setGracePeriod(
                        Date.from(scanResult.getScanDate().toInstant().plusSeconds(graceHours * SECONDS_IN_HOUR)));
            }
        });
        return result;
    }

    /**
     * Loads persisted tool scan status and tool's vulnerabilities
     * @param registry a registry path, where tool is located
     * @param id Tool's id or image
     * @return a {@link ToolScanResult}, containing tool's scan status and vulnerabilities
     */
    public ToolScanResult loadToolScanResult(String registry, String id) {
        Tool tool = loadTool(registry, id);
        return loadToolScanResult(tool);
    }

    /**
     * Cuts tag value from a image string. f.i. : repo/image:tag, image:tag
     * @return a docker tag for a tool
     */
    public String getTagFromImageName(String image) {
        Matcher matcher = REPOSITORY_AND_IMAGE.matcher(image);
        String imageWithTag;

        if (matcher.find()) {
            imageWithTag = matcher.group(2);
            return getImageTag(imageWithTag);
        } else {
            return getImageTag(image);
        }
    }

    /**
     * Loads {@link ToolScanResult} for the specified tool
     * that contains information about scan result for each tool version
     * @param tool for which {@link ToolScanResult} will be loaded
     * @return a {@link ToolScanResult}
     */
    public ToolScanResult loadToolScanResult(Tool tool) {
        ToolScanResult result = new ToolScanResult();
        result.setToolId(tool.getId());
        Map<String, ToolVersionScanResult> versionScanResults =
                toolVulnerabilityDao.loadAllToolVersionScans(tool.getId());
        for (String tag : loadTags(tool.getId())) {
            ToolVersionScanResult versionScan = getToolVersionScanResult(tool, versionScanResults, tag);
            result.getToolVersionScanResults().put(tag, versionScan);
        }
        return result;
    }


    public boolean isToolScanningEnabled() {
        return preferenceManager.getPreference(SystemPreferences.DOCKER_SECURITY_TOOL_SCAN_ENABLED);
    }

    /**
     * Checks that tool with specified image and registry doesn't exist in a database
     * @param image image name to be checked
     * @param registry registry name to be checked
     * @throws IllegalArgumentException if tool already exists
     */
    public void assertThatToolUniqueAcrossRegistries(String image, String registry) {
        List<Tool> conflictTools = toolDao.loadWithSameImageNameFromOtherRegistries(image, registry);
        Assert.isTrue(conflictTools.isEmpty(),
                      messageHelper.getMessage(MessageConstants.ERROR_TOOL_ALREADY_EXIST_IN_REGISTRY,
                            image, conflictTools.stream().map(Tool::getRegistry).findFirst().orElse("")));
    }

    public boolean isToolUniqueInRegistry(String image, Long registryId) {
        return toolDao.loadTool(registryId, getImageWithoutTag(image)) == null;
    }

    public boolean isToolUniqueInGroup(String image, Long groupId) {
        return !toolDao.loadToolByGroupAndImage(groupId, getImageWithoutTag(image)).isPresent();
    }

    public Optional<Tool> loadToolInGroup(String image, Long groupId) {
        return toolDao.loadToolByGroupAndImage(groupId, getImageWithoutTag(image));
    }

    public Pair<String, InputStream> loadToolIcon(long toolId) {
        return toolDao.loadIcon(toolId).orElseThrow(() -> new IllegalArgumentException(messageHelper.getMessage(
            MessageConstants.ERROR_TOOL_ICON_NOT_FOUND, toolId)));
    }

    /**
     * Updates an icon of a Tool, specified by ID
     * @param toolId an ID of a Tool, which icon to update
     * @param fileName a name of an icon file
     * @param image a byte content of a file
     * @return an ID of a newly created icon
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public long updateToolIcon(long toolId, String fileName, byte[] image) {
        Tool tool = load(toolId);
        Assert.notNull(tool, messageHelper.getMessage(MessageConstants.ERROR_TOOL_NOT_FOUND, toolId));
        int allowedIconSize = preferenceManager.getPreference(SystemPreferences.MISC_MAX_TOOL_ICON_SIZE_KB) * KB_SIZE;
        Assert.isTrue(image.length <= allowedIconSize,
                      messageHelper.getMessage(MessageConstants.ERROR_TOOL_ICON_TOO_LARGE, image.length,
                                               allowedIconSize));
        return toolDao.updateIcon(toolId, FilenameUtils.getName(fileName), image);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void deleteToolIcon(long toolId) {
        Tool tool = load(toolId);
        Assert.notNull(tool, messageHelper.getMessage(MessageConstants.ERROR_TOOL_NOT_FOUND, toolId));
        toolDao.deleteToolIcon(toolId);
    }

    public ToolDescription loadToolAttributes(Long toolId) {
        Tool tool = load(toolId);
        Map<String, ToolVersionScanResult> versionScanResults =
                toolVulnerabilityDao.loadAllToolVersionScans(toolId);
        ToolDescription toolDescription = new ToolDescription();
        toolDescription.setToolId(toolId);
        List<ToolVersionAttributes> versions = ListUtils
                .emptyIfNull(loadTags(toolId)).stream()
                .map(version -> ToolVersionAttributes.builder()
                        .version(version)
                        .attributes(getToolVersion(toolId, version))
                        .scanResult(getToolVersionScanResult(tool, versionScanResults, version))
                        .build())
                .collect(Collectors.toList());
        toolDescription.setVersions(versions);
        return toolDescription;
    }

    /**
     * Deletes all previously found tool vulnerabilities and packages
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void clearToolScan(final String registry, final String image, final String version) {
        Tool tool = loadTool(registry, image);
        toolVulnerabilityDao.deleteToolVersionScan(tool.getId(), version);
        toolVulnerabilityDao.deleteDependencies(tool.getId(), version);
        toolVulnerabilityDao.deleteVulnerabilities(tool.getId(), version);
    }

    public long getCurrentImageSize(final String dockerImage) {
        LOGGER.info("Getting size of image {}", dockerImage);
        Tool tool = loadByNameOrId(dockerImage);

        DockerRegistry dockerRegistry = dockerRegistryManager.load(tool.getRegistryId());
        String imageWithoutTag = getImageWithoutTag(tool.getImage());
        String tag = getTagFromImageName(dockerImage);
        DockerClient dockerClient = dockerRegistryManager.getDockerClient(dockerRegistry,
                imageWithoutTag);
        try {
            ToolVersion toolVersion = dockerClient.getVersionAttributes(dockerRegistry,
                    imageWithoutTag, tag);
            if (Objects.isNull(toolVersion) || Objects.isNull(toolVersion.getSize())) {
                LOGGER.warn(messageHelper.getMessage(MessageConstants.ERROR_TOOL_VERSION_INVALID_SIZE, dockerImage));
                return 0;
            }
            return toolVersion.getSize();
        } catch (IllegalArgumentException e) {
            LOGGER.error("An error occurred while getting image size: {} ", e.getMessage());
            return 0;
        }
    }

    private ToolVersion getToolVersion(Long toolId, String version) {
        return toolVersionManager.loadToolVersion(toolId, version);
    }

    private ToolVersionScanResult getToolVersionScanResult(Tool tool,
                                                           Map<String, ToolVersionScanResult> versionScanResults,
                                                           String tag) {
        ToolVersionScanResult versionScan = versionScanResults.getOrDefault(tag, new ToolVersionScanResult(tag));
        versionScan.setAllowedToExecute(toolScanManager.checkTool(tool, tag));
        if (versionScan.getScanDate() != null) {
            int graceHours = preferenceManager.getPreference(SystemPreferences.DOCKER_SECURITY_TOOL_GRACE_HOURS);
            versionScan.setGracePeriod(
                    Date.from(versionScan.getScanDate().toInstant().plusSeconds(graceHours * SECONDS_IN_HOUR)));
        }
        return versionScan;
    }

    private boolean imageAndTagAreValid(final String imageWithTag) {
        final String[] imageWithTagArray = imageWithTag.split(TAG_DELIMITER);
        final int numberOfElements = imageWithTagArray.length;
        return !imageWithTag.endsWith(TAG_DELIMITER) && numberOfElements <= 2;
    }

    private void deleteToolVersionScan(Long toolId, String version) {
        toolVulnerabilityDao.deleteToolVersionScan(toolId, version);
        toolVulnerabilityDao.deleteVulnerabilities(toolId, version);
        toolVulnerabilityDao.deleteDependencies(toolId, version);
    }

    private Tool fetchTool(String registry, String image) {
        Long registryId = !StringUtils.isEmpty(registry)
                ? Optional.ofNullable(dockerRegistryManager.loadByNameOrId(registry))
                .orElseThrow(() -> new IllegalArgumentException(
                        messageHelper.getMessage(MessageConstants.ERROR_DOCKER_REGISTRY_NOT_FOUND, registry))
                ).getId()
                : null;
        Tool tool = toolDao.loadTool(registryId, getImageWithoutTag(image));
        Assert.notNull(tool, messageHelper.getMessage(MessageConstants.ERROR_TOOL_NOT_FOUND, image));
        return tool;
    }

    private String getImageWithoutTag(String imageWithTag) {
        return imageWithTag.split(TAG_DELIMITER)[0];
    }

    private String getImageTag(String imageWithTag) {
        String[] nameAndTag = imageWithTag.split(TAG_DELIMITER);
        if (nameAndTag.length == 2) {
            return nameAndTag[1];
        } else {
            return LATEST_TAG;
        }
    }

    private void validateInstanceType(final Tool tool) {
        Assert.isTrue(isInstanceTypeAllowed(tool.getId(), tool.getInstanceType()),
                messageHelper.getMessage(MessageConstants.ERROR_INSTANCE_TYPE_IS_NOT_ALLOWED,
                        tool.getInstanceType()));
    }

    private boolean isInstanceTypeAllowed(final Long toolId, final String instanceType) {
        final ContextualPreferenceExternalResource resource =
                toolId != null
                        ? new ContextualPreferenceExternalResource(ContextualPreferenceLevel.TOOL, toolId.toString())
                        : null;
        return !StringUtils.hasText(instanceType)
                || instanceOfferManager.isToolInstanceAllowedInAnyRegion(instanceType, resource);
    }

    private void deleteToolWithDependent(final Tool tool, final String image, final boolean hard) {
        if (hard) {
            DockerRegistry dockerRegistry = dockerRegistryManager.load(tool.getRegistryId());
            List<String> tags = dockerRegistryManager.loadImageTags(dockerRegistry, image);

            for (String tag : tags) {
                Optional<ManifestV2> manifestOpt =
                        dockerRegistryManager.deleteImage(dockerRegistry, tool.getImage(), tag);
                manifestOpt.ifPresent(manifest -> {
                    dockerRegistryManager.deleteLayer(dockerRegistry, image, manifest.getConfig().getDigest());

                    Collections.reverse(manifest.getLayers());
                    for (ManifestV2.Config layer : manifest.getLayers()) {
                        dockerRegistryManager.deleteLayer(dockerRegistry, image, layer.getDigest());
                    }
                });
            }
        }
        toolVulnerabilityDao.loadAllToolVersionScans(tool.getId())
                .values()
                .forEach(versionScan -> deleteToolVersionScan(tool.getId(), versionScan.getVersion()));
        toolDao.deleteToolIcon(tool.getId());
        toolVersionManager.deleteToolVersions(tool.getId());
        toolDao.deleteTool(tool.getId());
    }


}
