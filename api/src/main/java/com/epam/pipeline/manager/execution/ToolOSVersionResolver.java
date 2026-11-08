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
import com.epam.pipeline.manager.pipeline.ToolUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves the OS of a docker image, which picks the pod launch command of a run, see
 * {@link com.epam.pipeline.manager.preference.SystemPreferences#LAUNCH_POD_CMD_TEMPLATE_LINUX}.
 *
 * It is the OS that the security scan found for the image. A version committed from a run may have none: its
 * scan may fail, or find no OS. Then:
 * <ul>
 *     <li>the {@link #OS_LABEL} label of the image is used. A commit sets it to the OS of the image of the run,
 *     and the later commits keep it, as docker keeps the labels of a container in an image committed from it</li>
 *     <li>for an image committed without that label, the {@link #POD_NAME_LABEL} label names the pod of the run
 *     it was committed from, and the OS of the image of that run is used</li>
 * </ul>
 *
 * Its dependencies are injected into fields: {@link ToolManager} leads back to {@link PipelineLauncher} through
 * the run manager, and a constructor would make that cycle unresolvable.
 */
@Slf4j
@Service
public class ToolOSVersionResolver {

    public static final String OS_LABEL = "com.epam.cloud-pipeline.os";
    static final String POD_NAME_LABEL = "io.kubernetes.pod.name";
    static final int MAX_COMMITS_TO_FOLLOW = 10;

    private static final Pattern POD_NAME_RUN_ID = Pattern.compile("-(\\d+)$");
    private static final Pattern OS_LABEL_VALUE = Pattern.compile("^([A-Za-z0-9._-]+):([A-Za-z0-9._-]*)$");
    private static final String OS_LABEL_DELIMITER = ":";

    @Autowired
    private ToolScanInfoManager toolScanInfoManager;
    @Autowired
    private ToolManager toolManager;
    @Autowired
    private DockerRegistryManager dockerRegistryManager;
    @Autowired
    private PipelineRunDao pipelineRunDao;

    /**
     * @return the value of {@link #OS_LABEL} for the OS, e.g. {@code rocky:8.7}, or {@code null} if the OS is not
     * determined, or cannot be written as a label value
     */
    public static String toOSLabel(final ToolOSVersion osVersion) {
        if (!isDetermined(osVersion)) {
            return null;
        }
        final String value = osVersion.getDistribution() + OS_LABEL_DELIMITER
                + StringUtils.defaultString(osVersion.getVersion());
        return OS_LABEL_VALUE.matcher(value).matches() ? value : null;
    }

    /**
     * @param image an image a run is launched with, e.g. {@code registry/group/tool:version}
     * @return the scanned OS of the image. If it has no determined one - the OS of its {@link #OS_LABEL} label,
     * or the OS of the nearest image it was committed from. Otherwise the scanned OS of the image as is, or
     * {@code null} if there is none.
     */
    public ToolOSVersion resolve(final String image) {
        final ToolOSVersion imageOSVersion = toolScanInfoManager.loadToolVersionScanInfoByImageName(image)
                .map(ToolVersionScanResult::getToolOSVersion)
                .orElse(null);
        if (isDetermined(imageOSVersion)) {
            return imageOSVersion;
        }
        final Set<String> visited = new HashSet<>();
        visited.add(image);
        String current = image;
        int commits = 0;
        while (current != null) {
            final Map<String, String> labels = findImageLabels(current);
            final ToolOSVersion labelOSVersion = fromOSLabel(labels.get(OS_LABEL));
            if (isDetermined(labelOSVersion)) {
                log.info("Image {} has no scanned OS. The OS {} of the {} label of the image {} is used "
                        + "to pick the launch command.", image, labels.get(OS_LABEL), OS_LABEL, current);
                return labelOSVersion;
            }
            if (commits == MAX_COMMITS_TO_FOLLOW) {
                break;
            }
            commits++;
            current = findRunImage(current, labels.get(POD_NAME_LABEL));
            if (current == null || !visited.add(current)) {
                break;
            }
            final ToolOSVersion sourceOSVersion = findScannedOSVersion(current);
            if (isDetermined(sourceOSVersion)) {
                log.info("Image {} has no scanned OS. The OS {} of the image {}, which it was committed from, "
                        + "is used to pick the launch command.", image, toOSLabel(sourceOSVersion), current);
                return sourceOSVersion;
            }
        }
        return imageOSVersion;
    }

    private static boolean isDetermined(final ToolOSVersion osVersion) {
        return osVersion != null && StringUtils.isNotBlank(osVersion.getDistribution())
                && !AggregatingToolScanManager.NOT_DETERMINED.equalsIgnoreCase(osVersion.getDistribution());
    }

    private ToolOSVersion fromOSLabel(final String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        final Matcher osVersion = OS_LABEL_VALUE.matcher(value.trim());
        return osVersion.matches() ? new ToolOSVersion(osVersion.group(1), osVersion.group(2)) : null;
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private ToolOSVersion findScannedOSVersion(final String image) {
        try {
            return toolScanInfoManager.loadToolVersionScanInfoByImageName(image)
                    .map(ToolVersionScanResult::getToolOSVersion)
                    .orElse(null);
        } catch (RuntimeException e) {
            log.debug("Cannot load the scan of the image {}: {}", image, e.getMessage());
            return null;
        }
    }

    /**
     * A failure here shall never fail the launch: the run then gets the default launch command.
     */
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private Map<String, String> findImageLabels(final String image) {
        try {
            final Tool tool = toolManager.loadByNameOrId(image);
            final String tag = toolManager.getTagFromImageName(image, toolManager.resolveDefaultVersion(tool));
            final Tool imageTool = tool.isSymlink() ? toolManager.load(tool.getLink()) : tool;
            // A tool loaded by an image name carries the tag in its image
            final String imageName = ToolUtils.getImageWithoutTag(imageTool.getImage());
            final DockerRegistry registry = dockerRegistryManager.load(imageTool.getRegistryId());
            return MapUtils.emptyIfNull(dockerRegistryManager.getImageLabels(registry, imageName, tag));
        } catch (RuntimeException e) {
            log.warn("Cannot read the labels of the image {}: {}", image, e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * @return the image of the run, whose pod the image was committed from, or {@code null} if it is not found
     */
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private String findRunImage(final String image, final String podName) {
        if (StringUtils.isBlank(podName)) {
            return null;
        }
        final Matcher runId = POD_NAME_RUN_ID.matcher(podName);
        if (!runId.find()) {
            return null;
        }
        try {
            final PipelineRun run = pipelineRunDao.loadPipelineRun(Long.parseLong(runId.group(1)));
            // A committed run only: the pod name may be of a run of another deployment, where the image was committed
            if (run == null || !podName.equals(run.getPodId())
                    || run.getCommitStatus() == null || run.getCommitStatus() == CommitStatus.NOT_COMMITTED) {
                log.debug("Image {} names pod {}, which is not a pod of a known committed run", image, podName);
                return null;
            }
            return run.getDockerImage();
        } catch (RuntimeException e) {
            log.warn("Cannot load the run of the pod {}, which the image {} was committed from: {}",
                    podName, image, e.getMessage());
            return null;
        }
    }
}
