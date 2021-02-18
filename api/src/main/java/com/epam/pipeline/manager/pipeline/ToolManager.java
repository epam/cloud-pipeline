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

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.tool.ToolDao;
import com.epam.pipeline.dao.tool.ToolVulnerabilityDao;
import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.contextual.ContextualPreferenceExternalResource;
import com.epam.pipeline.entity.contextual.ContextualPreferenceLevel;
import com.epam.pipeline.entity.docker.ImageDescription;
import com.epam.pipeline.entity.docker.ImageHistoryLayer;
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
import com.epam.pipeline.entity.scan.ToolOSVersion;
import com.epam.pipeline.entity.scan.ToolScanResult;
import com.epam.pipeline.entity.scan.ToolVersionScanResult;
import com.epam.pipeline.entity.scan.ToolVersionScanResultView;
import com.epam.pipeline.entity.scan.Vulnerability;
import com.epam.pipeline.entity.scan.VulnerabilitySeverity;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.tool.ToolSymlinkRequest;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
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
    private static final String TOOL_DELIMETER = "/";
    private static final String LATEST_TAG = "latest";
    private static final int KB_SIZE = 1000;
    private static final long SECONDS_IN_HOUR = 3600;
    private static final String CMD_DOCKER_COMMAND = "CMD";
    private static final String ENTRYPOINT_DOCKER_COMMAND = "ENTRYPOINT";
    private static final String SHELL_FORM_PREFIX = "\"/bin/sh\" \"-c\"";

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
        tool.setLink(null);

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
                        DateUtils.now(), tag, null, digest, new HashMap<>());
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
        validateToolNotNull(loadedTool, tool.getImage());
        validateToolCanBeModified(loadedTool);

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
        loadedTool.setAllowSensitive(tool.isAllowSensitive());

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

    public Tool loadExisting(final Long id) {
        final Tool tool = toolDao.loadTool(id);
        validateToolNotNull(tool, id);
        return tool;
    }

    @Override
    public AbstractSecuredEntity changeOwner(Long id, String owner) {
        Tool tool = toolDao.loadTool(id);
        tool.setOwner(owner);
        toolDao.updateOwner(tool);
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

    /**
     * Resolves tool symlinks if there are any by the given image name.
     * 
     * @param image Image name.
     * @return Resolved tool.
     */
    public Tool resolveSymlinks(final String image) {
        final Tool tool = loadToolWithFullImageName(image);
        return tool.isNotSymlink() ? tool : loadToolWithFullImageName(tool.getLink());
    }

    private Tool loadToolWithFullImageName(final String image) {
        final Tool tool = loadByNameOrId(image);
        validateToolNotNull(tool, image);
        tool.setImage(getFullImageName(tool));
        return tool;
    }

    private Tool loadToolWithFullImageName(final Long id) {
        final Tool tool = load(id);
        validateToolNotNull(tool, id);
        tool.setImage(getFullImageName(tool));
        return tool;
    }

    private String getFullImageName(final Tool linkedTool) {
        return linkedTool.getRegistry() + TOOL_DELIMETER + linkedTool.getImage();
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
        deleteTool(tool, image, hard);
        return tool;
    }

    /**
     * Deletes a tools
     * @param tool to delete
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public Tool delete(final Tool tool) {
        deleteTool(tool, tool.getImage(), false);
        return tool;
    }

    private void deleteTool(final Tool tool, final String image, final boolean hard) {
        if (tool.isNotSymlink()) {
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
            toolVulnerabilityDao.loadAllToolVersionScans(tool.getId()).values()
                    .forEach(versionScan -> deleteToolVersionScan(tool.getId(), versionScan.getVersion()));
            toolDao.deleteToolIcon(tool.getId());
            toolVersionManager.deleteToolVersions(tool.getId());
        }
        toolDao.deleteTool(tool.getId());
    }

    private void deleteToolVersionScan(Long toolId, String version) {
        toolVulnerabilityDao.deleteToolVersionScan(toolId, version);
        toolVulnerabilityDao.deleteVulnerabilities(toolId, version);
        toolVulnerabilityDao.deleteDependencies(toolId, version);
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
        validateToolNotNull(tool, image);
        validateToolCanBeModified(tool);
        deleteToolVersionScan(tool.getId(), version);
        toolVersionManager.deleteToolVersion(tool.getId(), version);
        DockerRegistry dockerRegistry = dockerRegistryManager.load(tool.getRegistryId());
        dockerRegistryManager.deleteImage(dockerRegistry, tool.getImage(), version);
        return tool;
    }

    public List<String> loadTags(Long id) {
        Tool tool = load(id);
        validateToolNotNull(tool, id);
        return tool.isSymlink() ? loadTags(tool.getLink()) : loadTags(tool);
    }

    public List<String> loadTags(final Tool tool) {
        return dockerRegistryManager.loadImageTags(dockerRegistryManager.load(tool.getRegistryId()), tool);
    }

    public ImageDescription loadToolDescription(Long id, String tag) {
        Tool tool = load(id);
        validateToolNotNull(tool, id);
        return tool.isSymlink() ? loadToolDescription(tool.getLink(), tag) : loadToolDescription(tool, tag);
    }

    private ImageDescription loadToolDescription(final Tool tool, final String tag) {
        return dockerRegistryManager.getImageDescription(
                dockerRegistryManager.load(tool.getRegistryId()), tool.getImage(), tag);
    }

    public List<ImageHistoryLayer> loadToolHistory(final Long id, final String tag) {
        final Tool tool = load(id);
        validateToolNotNull(tool, id);
        return tool.isSymlink() ? loadToolHistory(tool.getLink(), tag) : loadToolHistory(tool, tag);
    }

    public String loadToolDefaultCommand(final Long id, final String tag) {
        final List<String> commands = loadToolHistory(id, tag)
            .stream()
            .map(ImageHistoryLayer::getCommand)
            .collect(Collectors.toList());
        final Pair<Integer, Integer> defaultsPositions = getDefaultsPositions(commands);
        final int entrypointPos = defaultsPositions.getLeft();
        final int cmdPos = defaultsPositions.getRight();

        if (entrypointPos > -1) {
            final String entrypointInstruction =
                getTrimmedInstruction(commands.get(entrypointPos));
            if (cmdPos > entrypointPos) {
                final String cmdInstruction =
                    getTrimmedInstruction(commands.get(cmdPos));
                if (!(entrypointInstruction.startsWith(SHELL_FORM_PREFIX)
                      && cmdInstruction.startsWith(SHELL_FORM_PREFIX))) {
                    return String.join(" ", entrypointInstruction, cmdInstruction);
                }
            }
            return entrypointInstruction;
        } else if (cmdPos > -1) {
            return getTrimmedInstruction(commands.get(cmdPos));
        }
        return "";
    }

    private String getTrimmedInstruction(final String command) {
        return command.substring(command.indexOf('[') + 1, command.lastIndexOf(']')).trim();
    }

    /**
     * Searches for ENTRYPOINT and CMD instructions in tool's image history.
     *
     * @param commands list, containing image's commands history
     * @return 2 long values, describing last positions of ENTRYPOINT and CMD instruction (-1 if none found)
     */
    private Pair<Integer, Integer> getDefaultsPositions(final List<String> commands) {
        int entrypointPos = -1;
        int cmdPos = -1;
        for (int i = 0; i < commands.size(); i++) {
            final String command = commands.get(i);
            if (command.toUpperCase().trim().startsWith(ENTRYPOINT_DOCKER_COMMAND)) {
                entrypointPos = i;
            } else if (command.toUpperCase().trim().startsWith(CMD_DOCKER_COMMAND)) {
                cmdPos = i;
            }
        }
        return Pair.of(entrypointPos, cmdPos);
    }

    private List<ImageHistoryLayer> loadToolHistory(final Tool tool, final String tag) {
        return dockerRegistryManager.getImageHistory(
                dockerRegistryManager.load(tool.getRegistryId()), tool.getImage(), tag);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void updateToolVulnerabilities(List<Vulnerability> vulnerabilities, long toolId, String version) {
        LOGGER.debug("Update tool with id: " + toolId + " and version "
                + version + " with " + vulnerabilities.size() + " vulnerabilities");
        final Tool tool = load(toolId);
        validateToolNotNull(tool, toolId);
        validateToolCanBeModified(tool);
        vulnerabilities.forEach(v -> v.setCreatedDate(new Date()));
        toolVulnerabilityDao.deleteVulnerabilities(toolId, version);
        toolVulnerabilityDao.createVulnerabilityRecords(vulnerabilities, toolId, version);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void updateToolDependencies(List<ToolDependency> dependencies, long toolId, String version) {
        LOGGER.debug("Update tool with id: " + toolId + " and version "
                + version + " with " + dependencies.size() + " dependencies");
        final Tool tool = load(toolId);
        validateToolNotNull(tool, toolId);
        validateToolCanBeModified(tool);
        toolVulnerabilityDao.deleteDependencies(toolId, version);
        toolVulnerabilityDao.createDependencyRecords(dependencies, toolId, version);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void updateToolVersionScanStatus(long toolId, ToolScanStatus newStatus, Date scanDate,
                                            String version, ToolOSVersion toolOSVersion,
                                            String layerRef, String digest,
                                            Map<VulnerabilitySeverity, Integer> vulnerabilityCount) {
        final Tool tool = load(toolId);
        validateToolNotNull(tool, toolId);
        validateToolCanBeModified(tool);
        Optional<ToolVersionScanResult> prev = loadToolVersionScan(tool, version);
        if(prev.isPresent()) {
            ToolVersionScanResult scanResult = prev.get();
            boolean whiteList = scanResult.isFromWhiteList();
            if (scanResult.getDigest() == null || !scanResult.getDigest().equals(digest)) {
                log.debug("Digests are not match! If version was from white list, it will be deleted!");
                whiteList = false;
            }
            toolVulnerabilityDao.updateToolVersionScan(toolId, version, toolOSVersion, layerRef, digest, newStatus,
                    scanDate, whiteList, vulnerabilityCount);
        } else {
            toolVulnerabilityDao.insertToolVersionScan(toolId, version, toolOSVersion, layerRef,
                    digest, newStatus, scanDate, vulnerabilityCount);
        }
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void updateToolVersionScanStatus(long toolId, ToolScanStatus newStatus, Date scanDate,
                                            String version, String layerRef, String digest,
                                            Map<VulnerabilitySeverity, Integer> vulnerabilityCount) {
        updateToolVersionScanStatus(toolId, newStatus, scanDate, version, null, layerRef, digest,
                vulnerabilityCount);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public ToolVersionScanResult updateWhiteListWithToolVersionStatus(long toolId, String version,
                                                                      boolean fromWhiteList) {
        final Tool tool = load(toolId);
        validateToolNotNull(tool, toolId);
        validateToolCanBeModified(tool);
        Optional<ToolVersionScanResult> toolVersionScanResult = loadToolVersionScan(tool, version);
        if (!toolVersionScanResult.isPresent()) {
            toolVulnerabilityDao.insertToolVersionScan(toolId, version, null, null, null, ToolScanStatus.NOT_SCANNED,
                    DateUtils.now(), new HashMap<>());
        }
        toolVulnerabilityDao.updateWhiteListWithToolVersion(toolId, version, fromWhiteList);
        return loadToolVersionScan(tool, version).orElse(null);
    }

    public Optional<ToolVersionScanResult> loadToolVersionScan(long toolId, String version) {
        final Tool tool = load(toolId);
        validateToolNotNull(tool, toolId);
        return tool.isSymlink() ? loadToolVersionScan(tool.getLink(), version) : loadToolVersionScan(tool, version);
    }

    private Optional<ToolVersionScanResult> loadToolVersionScan(final Tool tool, final String version) {
        final Optional<ToolVersionScanResult> result = toolVulnerabilityDao.loadToolVersionScan(tool.getId(), version);
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
        validateToolNotNull(tool, id);
        return tool.isSymlink() ? loadToolScanResult(tool.getLink()) : loadToolScan(tool);
    }

    /**
     * Loads {@link ToolScanResult} for the specified tool
     * that contains information about scan result for each tool version
     * @param tool for which {@link ToolScanResult} will be loaded
     * @return a {@link ToolScanResult}
     */
    public ToolScanResult loadToolScanResult(Tool tool) {
        return tool.isSymlink() ? loadToolScanResult(tool.getLink()) : loadToolScan(tool);
    }

    private ToolScanResult loadToolScanResult(final Long toolId) {
        Tool tool = load(toolId);
        validateToolNotNull(tool, toolId);
        return tool.isSymlink() ? loadToolScanResult(tool.getLink()) : loadToolScan(tool);
    }

    private ToolScanResult loadToolScan(final Tool tool) {
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
        validateToolNotNull(tool, toolId);
        validateToolCanBeModified(tool);
        int allowedIconSize = preferenceManager.getPreference(SystemPreferences.MISC_MAX_TOOL_ICON_SIZE_KB) * KB_SIZE;
        Assert.isTrue(image.length <= allowedIconSize,
                      messageHelper.getMessage(MessageConstants.ERROR_TOOL_ICON_TOO_LARGE, image.length,
                                               allowedIconSize));
        return toolDao.updateIcon(toolId, FilenameUtils.getName(fileName), image);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void deleteToolIcon(long toolId) {
        Tool tool = load(toolId);
        validateToolNotNull(tool, toolId);
        validateToolCanBeModified(tool);
        toolDao.deleteToolIcon(toolId);
    }

    public ToolDescription loadToolAttributes(Long toolId) {
        Tool tool = load(toolId);
        validateToolNotNull(tool, toolId);
        return tool.isSymlink() ? loadToolAttributes(tool.getLink()) : loadToolAttributes(tool);
    }

    public ToolVersionAttributes loadToolVersionAttributes(final Long toolId, final String version) {
        final Tool tool = load(toolId);
        validateToolNotNull(tool, toolId);
        return tool.isSymlink() ? loadToolVersionAttributes(tool.getLink(), version) :
                loadToolVersionAttributes(tool, version);
    }

    public boolean isToolOSVersionAllowed(final ToolOSVersion toolOSVersion) {
        final String allowedOSes = preferenceManager.getPreference(SystemPreferences.DOCKER_SECURITY_TOOL_OS);

        if (StringUtils.isEmpty(allowedOSes) || toolOSVersion == null) {
            return true;
        }

        return Arrays.stream(allowedOSes.split(",")).anyMatch(os -> {
            String[] distroVersion = os.split(":");
            // if distro name is not equals allowed return false (allowed: centos, actual: ubuntu)
            if (!distroVersion[0].equalsIgnoreCase(toolOSVersion.getDistribution())) {
                return false;
            }
            // return false only if version of allowed exists (e.g. centos:6)
            // and actual version contains allowed (e.g. : allowed centos:6, actual centos:6.10)
            return distroVersion.length != 2 || toolOSVersion.getVersion().toLowerCase()
                    .startsWith(distroVersion[1].toLowerCase());
        });
    }

    /**
     * Deletes all previously found tool vulnerabilities and packages
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void clearToolScan(final String registry, final String image, final String version) {
        Tool tool = loadTool(registry, image);
        validateToolNotNull(tool, image);
        validateToolCanBeModified(tool);
        deleteToolVersionScan(tool.getId(), version);
    }

    public long getCurrentImageSize(final String dockerImage) {
        LOGGER.info("Getting size of image {}", dockerImage);
        Tool tool = loadByNameOrId(dockerImage);
        validateToolNotNull(tool, dockerImage);
        return tool.isSymlink() ? getCurrentImageSize(tool.getLink()) : getCurrentImageSize(tool);
    }

    private long getCurrentImageSize(final Long toolId) {
        Tool tool = load(toolId);
        validateToolNotNull(tool, toolId);
        return getCurrentImageSize(tool);
    }

    private long getCurrentImageSize(final Tool tool) {
        DockerRegistry dockerRegistry = dockerRegistryManager.load(tool.getRegistryId());
        String imageWithoutTag = getImageWithoutTag(tool.getImage());
        String tag = getTagFromImageName(tool.getImage());
        DockerClient dockerClient = dockerRegistryManager.getDockerClient(dockerRegistry,
                imageWithoutTag);
        try {
            ToolVersion toolVersion = dockerClient.getVersionAttributes(dockerRegistry,
                    imageWithoutTag, tag);
            if (Objects.isNull(toolVersion) || Objects.isNull(toolVersion.getSize())) {
                LOGGER.warn(messageHelper.getMessage(MessageConstants.ERROR_TOOL_VERSION_INVALID_SIZE, 
                        tool.getImage()));
                return 0;
            }
            return toolVersion.getSize();
        } catch (IllegalArgumentException e) {
            LOGGER.error("An error occurred while getting image size: {} ", e.getMessage());
            return 0;
        }
    }

    /**
     * Creates a symlink for a tool by the given request.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public Tool symlink(final ToolSymlinkRequest request) {
        Assert.notNull(request.getToolId(), messageHelper.getMessage(
                MessageConstants.ERROR_TOOL_SYMLINK_SOURCE_TOOL_ID_MISSING));
        Assert.notNull(request.getGroupId(), messageHelper.getMessage(
                MessageConstants.ERROR_TOOL_SYMLINK_TARGET_GROUP_ID_MISSING));

        final Tool sourceTool = load(request.getToolId());
        final ToolGroup targetGroup = toolGroupManager.load(request.getGroupId());

        Assert.notNull(sourceTool, messageHelper.getMessage(MessageConstants.ERROR_TOOL_SYMLINK_SOURCE_TOOL_NOT_FOUND,
                request.getToolId()));
        Assert.notNull(targetGroup, messageHelper.getMessage(MessageConstants.ERROR_TOOL_SYMLINK_TARGET_GROUP_NOT_FOUND,
                request.getGroupId()));
        Assert.isTrue(!sourceTool.isSymlink(), messageHelper.getMessage(
                MessageConstants.ERROR_TOOL_SYMLINK_TARGET_SYMLINK));
        
        final String targetImage = getSymlinkTargetImage(sourceTool, targetGroup);

        Assert.isTrue(!Objects.equals(sourceTool.getToolGroupId(), targetGroup.getId()), messageHelper.getMessage(
                MessageConstants.ERROR_TOOL_ALREADY_EXIST, targetImage, targetGroup.getName()));
        Assert.isTrue(isToolUniqueInGroup(targetImage, targetGroup.getId()), messageHelper.getMessage(
                MessageConstants.ERROR_TOOL_ALREADY_EXIST, targetImage, targetGroup.getName()));

        final Tool tool = new Tool();
        tool.setImage(targetImage);
        tool.setParent(targetGroup);
        tool.setToolGroupId(targetGroup.getId());
        tool.setRegistryId(targetGroup.getRegistryId());
        tool.setLink(sourceTool.getId());
        tool.setOwner(authManager.getAuthorizedUser());
        tool.setCpu(sourceTool.getCpu());
        tool.setRam(sourceTool.getRam());

        toolDao.createTool(tool);

        return load(tool.getId());
    }

    private String getSymlinkTargetImage(final Tool sourceTool, final ToolGroup targetGroup) {
        return targetGroup.getName() + TOOL_DELIMETER +
                toolGroupManager.getGroupAndTool(sourceTool.getImage()).getRight();
    }

    private ToolVersionScanResult getToolVersionScanResult(Tool tool,
                                                           Map<String, ToolVersionScanResult> versionScanResults,
                                                           String tag) {
        ToolVersionScanResult versionScan = versionScanResults.getOrDefault(tag, new ToolVersionScanResult(tag));
        return setExecutePermission(tool, tag, versionScan);
    }

    public ToolVersionScanResult setExecutePermission(final Tool tool,
                                                      final String tag,
                                                      final ToolVersionScanResult versionScan) {
        versionScan.setAllowedToExecute(toolScanManager.checkScan(tool, tag, versionScan).isAllowed());
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

    private Tool fetchTool(String registry, String image) {
        Long registryId = !StringUtils.isEmpty(registry)
                ? dockerRegistryManager.loadByNameOrId(registry).getId()
                : null;
        Tool tool = toolDao.loadTool(registryId, getImageWithoutTag(image));
        validateToolNotNull(tool, image);
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

    private void validateToolNotNull(final Tool tool, final long toolId) {
        Assert.notNull(tool, messageHelper.getMessage(MessageConstants.ERROR_TOOL_NOT_FOUND, toolId));
    }

    private void validateToolNotNull(final Tool tool, final String image) {
        Assert.notNull(tool, messageHelper.getMessage(MessageConstants.ERROR_TOOL_NOT_FOUND, image));
    }

    private void validateToolCanBeModified(final Tool tool) {
        Assert.isTrue(tool.isNotSymlink(), messageHelper.getMessage(
                MessageConstants.ERROR_TOOL_SYMLINK_MODIFICATION_NOT_SUPPORTED));
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

    private ToolVersionAttributes loadToolVersionAttributes(final Tool tool, final String version) {
        final ToolVersionScanResult scanResult = toolVulnerabilityDao.loadToolVersionScan(tool.getId(), version)
                .orElseGet(() -> new ToolVersionScanResult(version));
        final ToolVersionScanResult vScanResult = setExecutePermission(tool, version, scanResult);
        return buildVersionAttributes(tool, version, vScanResult);
    }

    private ToolDescription loadToolAttributes(final Tool tool) {
        Map<String, ToolVersionScanResult> versionScanResults =
                toolVulnerabilityDao.loadAllToolVersionScans(tool.getId());
        ToolDescription toolDescription = new ToolDescription();
        toolDescription.setToolId(tool.getId());
        List<ToolVersionAttributes> versions = ListUtils
                .emptyIfNull(loadTags(tool.getId())).stream()
                .map(version -> {
                    ToolVersionScanResult vScanResult = getToolVersionScanResult(tool, versionScanResults, version);
                    return buildVersionAttributes(tool, version, vScanResult);
                })
                .collect(Collectors.toList());
        toolDescription.setVersions(versions);
        return toolDescription;
    }

    private ToolVersionAttributes buildVersionAttributes(final Tool tool,
                                                         final String version,
                                                         final ToolVersionScanResult vScanResult) {
        return ToolVersionAttributes.builder()
                .version(version)
                .attributes(getToolVersion(tool.getId(), version))
                .scanResult(ToolVersionScanResultView.from(vScanResult,
                        isToolOSVersionAllowed(vScanResult.getToolOSVersion())))
                .build();
    }

    private ToolVersion getToolVersion(Long toolId, String version) {
        return toolVersionManager.loadToolVersion(toolId, version);
    }
}
