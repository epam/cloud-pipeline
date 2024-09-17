/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.docker.ImageHistoryLayer;
import com.epam.pipeline.entity.docker.ManifestV2;
import com.epam.pipeline.entity.docker.ToolVersion;
import com.epam.pipeline.entity.docker.ToolVersionAttributes;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.ToolScanStatus;
import com.epam.pipeline.entity.scan.ToolDependency;
import com.epam.pipeline.entity.scan.ToolExecutionCheckStatus;
import com.epam.pipeline.entity.scan.ToolOSVersion;
import com.epam.pipeline.entity.scan.ToolScanPolicy;
import com.epam.pipeline.entity.scan.ToolVersionScanResult;
import com.epam.pipeline.entity.scan.Vulnerability;
import com.epam.pipeline.entity.scan.VulnerabilitySeverity;
import com.epam.pipeline.entity.scan.clair.ClairVulnerabilities;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.exception.ToolScanExternalServiceException;
import com.epam.pipeline.manager.docker.DockerClient;
import com.epam.pipeline.manager.docker.DockerClientFactory;
import com.epam.pipeline.manager.docker.DockerRegistryManager;
import com.epam.pipeline.manager.docker.ToolVersionManager;
import com.epam.pipeline.manager.docker.scan.clair.ClairClient;
import com.epam.pipeline.manager.docker.scan.clair.v2.ClairV2Api;
import com.epam.pipeline.manager.docker.scan.clair.v2.ClairV2Client;
import com.epam.pipeline.manager.docker.scan.clair.v4.ClairV4Api;
import com.epam.pipeline.manager.docker.scan.clair.v4.ClairV4Client;
import com.epam.pipeline.manager.docker.scan.dockercompscan.DockerComponentScanRequest;
import com.epam.pipeline.manager.docker.scan.dockercompscan.DockerComponentScanResult;
import com.epam.pipeline.manager.docker.scan.dockercompscan.DockerComponentScanService;
import com.epam.pipeline.manager.pipeline.ToolManager;
import com.epam.pipeline.manager.pipeline.ToolScanInfoManager;
import com.epam.pipeline.manager.preference.AbstractSystemPreference;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.utils.URLUtils;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.epam.pipeline.manager.preference.AbstractSystemPreference.StringPreference;

/**
 * An implementation of {@link ToolScanManager}, that connects to Clair System (https://github.com/coreos/clair).
 * It uses Retrofit2 to connect to Clair's REST API
 */
@Service
@Slf4j
public class AggregatingToolScanManager implements ToolScanManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(AggregatingToolScanManager.class);

    private static final int DISABLED = -1;
    private static final long SECONDS_IN_HOUR = 3600;
    private static final String WINDOWS_PLATFORM = "Windows";
    private static final String CLAIR_V2 = "v2";
    private static final String CLAIR_V4 = "v4";
    public static final String NOT_DETERMINED = "NotDetermined";

    @Autowired
    private PreferenceManager preferenceManager;

    @Autowired
    private ToolManager toolManager;

    @Autowired
    private DockerRegistryManager dockerRegistryManager;

    @Autowired
    private DockerClientFactory dockerClientFactory;

    @Autowired
    private MessageHelper messageHelper;

    @Autowired
    private ToolScanInfoManager toolScanInfoManager;

    @Autowired
    private ToolVersionManager toolVersionManager;

    private ClairClient clairService;
    private DockerComponentScanService dockerComponentService;

    @PostConstruct
    public void init() {
        initClients();
        Arrays.asList(
                SystemPreferences.DOCKER_SECURITY_TOOL_SCAN_ENABLED,
                SystemPreferences.DOCKER_SECURITY_TOOL_SCAN_CLAIR_ROOT_URL,
                SystemPreferences.DOCKER_SECURITY_TOOL_SCAN_CLAIR_CONNECT_TIMEOUT,
                SystemPreferences.DOCKER_SECURITY_TOOL_SCAN_CLAIR_READ_TIMEOUT,
                SystemPreferences.DOCKER_SECURITY_TOOL_SCAN_CLAIR_VERSION,
                SystemPreferences.DOCKER_COMP_SCAN_ROOT_URL)
                .forEach(this::subscribePreference);
    }

    private void subscribePreference(final AbstractSystemPreference<?> preference) {
        preferenceManager.getObservablePreference(preference)
                .subscribe(ignored -> initClients());
    }

    private void initClients() {
        clairService = initClairClient();
        dockerComponentService = initClient(
                SystemPreferences.DOCKER_COMP_SCAN_ROOT_URL, DockerComponentScanService.class);

    }

    private <T> T initClient(final StringPreference clientUrl, final Class<T> serviceType) {
        String baseUrl = preferenceManager.getPreference(clientUrl);

        T service = null;

        if (preferenceManager.getPreference(SystemPreferences.DOCKER_SECURITY_TOOL_SCAN_ENABLED)
            && StringUtils.isNotBlank(baseUrl)) {
            Integer connectTimeout = preferenceManager.getPreference(
                SystemPreferences.DOCKER_SECURITY_TOOL_SCAN_CLAIR_CONNECT_TIMEOUT);
            Integer readTimeout = preferenceManager.getPreference(
                SystemPreferences.DOCKER_SECURITY_TOOL_SCAN_CLAIR_READ_TIMEOUT);


            LOGGER.info("Initializing " + serviceType.getName() +
                            " with base URL: {}, connect_timeout: {}, read_timeout: {}", baseUrl,
                        connectTimeout, readTimeout);

            OkHttpClient client = new OkHttpClient.Builder()
                .readTimeout(readTimeout, TimeUnit.SECONDS)
                .connectTimeout(connectTimeout, TimeUnit.SECONDS)
                .build();

            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(URLUtils.getUrlWithTrailingSlash(baseUrl))
                .addConverterFactory(JacksonConverterFactory.create(objectMapper))
                .client(client)
                .build();

            service = retrofit.create(serviceType);
            LOGGER.info("Successfully initialize client " + serviceType.getName() + " with base URL: {}", baseUrl);
        }
        return service;
    }

    public ToolVersionScanResult scanTool(final Tool tool, final String tag, final Boolean rescan)
            throws ToolScanExternalServiceException {
        DockerRegistry registry = dockerRegistryManager.load(tool.getRegistryId());
        Optional<ToolVersionScanResult> actualScan = rescan ? Optional.empty() : getActualScan(tool, tag, registry);
        return actualScan.isPresent() ? actualScan.get() : doScan(tool, tag, registry);
    }

    public ToolVersionScanResult scanTool(final Long toolId, final String tag, final Boolean rescan)
            throws ToolScanExternalServiceException {
        Tool tool = toolManager.load(toolId);
        return scanTool(tool, tag, rescan);
    }

    public ToolExecutionCheckStatus checkTool(final Tool tool, final String tag) {
        final Optional<ToolVersionScanResult> versionScanOp =
                toolScanInfoManager.loadToolVersionScanInfo(tool.getId(), tag);
        return checkStatus(tool, tag, versionScanOp);
    }

    public ToolExecutionCheckStatus checkScan(final Tool tool, final String tag, final ToolVersionScanResult scan) {
        return checkStatus(tool, tag, Optional.ofNullable(scan));
    }

    private ToolExecutionCheckStatus checkStatus(final Tool tool, final String tag,
                                                 final Optional<ToolVersionScanResult> versionScanOp) {
        final boolean isWindowsTool = toolVersionManager.findToolVersion(tool.getId(), tag)
            .map(ToolVersion::getPlatform)
            .filter(WINDOWS_PLATFORM::equalsIgnoreCase)
            .isPresent();
        if (isWindowsTool) {
            LOGGER.debug("Tool [id={}, version={}] is Windows-based, proceed with running.", tool.getId(), tag);
            return ToolExecutionCheckStatus.success();
        }

        int graceHours = preferenceManager.getPreference(SystemPreferences.DOCKER_SECURITY_TOOL_GRACE_HOURS);

        boolean isGracePeriodOrWhiteList = versionScanOp.isPresent() &&
                (gracePeriodIsActive(versionScanOp.get(), graceHours) || versionScanOp.get().isFromWhiteList());

        if (isGracePeriodOrWhiteList) {
            LOGGER.debug("Tool: " + tool.getId() + " version: " + tag +
                    " is from White list or Grace period still active! Proceed with running!");
            return ToolExecutionCheckStatus.success();
        }

        boolean denyNotScanned = preferenceManager.getPreference(
                SystemPreferences.DOCKER_SECURITY_TOOL_POLICY_DENY_NOT_SCANNED);
        if (denyNotScanned && (!versionScanOp.isPresent()
                || versionScanOp.get().getStatus() == ToolScanStatus.NOT_SCANNED
                || versionScanOp.get().getSuccessScanDate() == null)) {
            return ToolExecutionCheckStatus.fail("Tool is not scanned.");
        }

        if (versionScanOp.isPresent()) {
            ToolVersionScanResult toolVersionScanResult = versionScanOp.get();
            Map<VulnerabilitySeverity, Integer> severityCounters = toolVersionScanResult.getVulnerabilitiesCount();
            int maxCriticalVulnerabilities = preferenceManager.getPreference(
                    SystemPreferences.DOCKER_SECURITY_TOOL_POLICY_MAX_CRITICAL_VULNERABILITIES);
            if (maxCriticalVulnerabilities != DISABLED &&
                    maxCriticalVulnerabilities < severityCounters.getOrDefault(VulnerabilitySeverity.Critical, 0)) {
                return ToolExecutionCheckStatus.fail("Max number of CRITICAL vulnerabilities is reached");
            }
            int maxHighVulnerabilities = preferenceManager.getPreference(
                    SystemPreferences.DOCKER_SECURITY_TOOL_POLICY_MAX_HIGH_VULNERABILITIES);
            if (maxHighVulnerabilities != DISABLED &&
                    maxHighVulnerabilities < severityCounters.getOrDefault(VulnerabilitySeverity.High, 0)) {
                return ToolExecutionCheckStatus.fail("Max number of HIGH vulnerabilities is reached");
            }
            int maxMediumVulnerabilities = preferenceManager.getPreference(
                    SystemPreferences.DOCKER_SECURITY_TOOL_POLICY_MAX_MEDIUM_VULNERABILITIES);
            if (maxMediumVulnerabilities != DISABLED &&
                    maxMediumVulnerabilities < severityCounters.getOrDefault(VulnerabilitySeverity.Medium, 0)) {
                return ToolExecutionCheckStatus.fail("Max number of MEDIUM vulnerabilities is reached");
            }

            LOGGER.debug("Tool: {} version: {} Check tool os version.", tool.getId(), tag);
            if (toolVersionScanResult.getToolOSVersion() != null
                    && !toolManager.isToolOSVersionAllowed(toolVersionScanResult.getToolOSVersion())) {
                LOGGER.warn("Tool: {} version: {}. Tool os version isn't allowed, check preference {} ! Cancel run.",
                        tool.getId(), tag, SystemPreferences.DOCKER_SECURITY_TOOL_OS.getKey());
                return ToolExecutionCheckStatus.fail("This type of OS is not supported.");
            }
        }
        return ToolExecutionCheckStatus.success();
    }

    private boolean gracePeriodIsActive(ToolVersionScanResult versionScan, int graceHours) {
        if (versionScan.getScanDate() == null) {
            return false;
        }
        Instant now = DateUtils.now().toInstant();
        Instant gracePeriodEnd = versionScan.getScanDate().toInstant().plusSeconds(graceHours * SECONDS_IN_HOUR);
        boolean result = gracePeriodEnd.isAfter(now);
        LOGGER.debug("Tool: "+ versionScan.getToolId() + " grace period end: " + gracePeriodEnd + " Now: " + now);
        return result;
    }

    @Override
    public ToolScanPolicy getPolicy() {
        return new ToolScanPolicy(
                preferenceManager.getPreference(
                        SystemPreferences.DOCKER_SECURITY_TOOL_POLICY_DENY_NOT_SCANNED),
                preferenceManager.getPreference(
                        SystemPreferences.DOCKER_SECURITY_TOOL_POLICY_MAX_CRITICAL_VULNERABILITIES),
                preferenceManager.getPreference(
                        SystemPreferences.DOCKER_SECURITY_TOOL_POLICY_MAX_HIGH_VULNERABILITIES),
                preferenceManager.getPreference(
                        SystemPreferences.DOCKER_SECURITY_TOOL_POLICY_MAX_MEDIUM_VULNERABILITIES));
    }

    String getLayerName(String image, String tag) throws UnsupportedEncodingException {
        return URLEncoder.encode(image, "UTF-8") + ":" + URLEncoder.encode(tag, "UTF-8")
                + ":" + UUID.randomUUID();
    }

    private ToolVersionScanResult doScan(final Tool tool, final String tag, final DockerRegistry registry)
            throws ToolScanExternalServiceException {
        try {
            final ManifestV2 manifest = getManifest(tool, tag, registry);
            final List<String> layers = fetchLayers(manifest);
            final Optional<ClairVulnerabilities> clairResults = getClairScanResults(tool, tag, registry, layers,
                    manifest);
            final String lastLayerRef = scanDockerComp(tool, tag, registry, layers);
            final DockerClient client = getDockerClient(tool.getImage(), registry);
            final String digest = client.getVersionAttributes(registry, tool.getImage(), tag).getDigest();
            final List<ImageHistoryLayer> imageHistory = client.getImageHistory(registry, tool.getImage(), tag);
            final boolean hasNvidiaMark = hasNvidiaMark(client, registry, tool.getImage(), tag);
            final String defaultCmd = toolManager.loadToolDefaultCommand(imageHistory);
            return gatherResults(tool, tag, lastLayerRef, digest, defaultCmd, imageHistory.size(), hasNvidiaMark,
                    clairResults);
        } catch (IOException e) {
            throw new ToolScanExternalServiceException(tool, e);
        }
    }

    private Optional<ToolVersionScanResult> getActualScan(Tool tool, String tag, DockerRegistry registry) {
        Optional<ToolVersionScanResult> versionScanResult = toolManager.loadToolVersionScan(tool.getId(), tag);
        if (versionScanResult.isPresent() && versionScanResult.get().getLastLayerRef() != null
                && versionScanResult.get().getStatus() != ToolScanStatus.FAILED) {
            ToolVersionScanResult vs = versionScanResult.get();
            LOGGER.info(messageHelper.getMessage(MessageConstants.INFO_TOOL_SCAN_ALREADY_SCANNED, tool.getImage()));
            DockerClient dockerClient = getDockerClient(tool.getImage(), registry);
            String dockerRef = dockerClient.getVersionAttributes(registry, tool.getImage(), tag).getDigest();
            boolean isActual = vs.getDigest() != null && dockerRef.equals(vs.getDigest());

            if (isActual) {
                vs.setScanDate(DateUtils.now());
                return Optional.of(vs);
            } else {
                LOGGER.info(messageHelper.getMessage(MessageConstants.INFO_TOOL_SCAN_NEW_LAYERS,
                        tool.getImage(), tag, vs.getDigest(), dockerRef));
            }
        }
        return Optional.empty();
    }

    private String scanDockerComp(final Tool tool, final String tag, final DockerRegistry registry,
                                  final List<String> layers) throws IOException {
        String lastLayer = null;
        for (int i = 0; i < layers.size(); i++) {
            log.debug("Scanning docker component {}:{}, started for {} of {} layers", tool.getImage(), tag, i + 1,
                    layers.size());
            final String layerDigest = layers.get(i);
            final String layerRef = getLayerName(tool.getImage(), tag);

            executeDockerCompLayerScan(tool, tag, registry, lastLayer, layerDigest, layerRef);

            lastLayer = layerRef;
        }
        log.debug("Scanning docker components {}:{} done.", tool.getImage(), tag);
        return lastLayer;
    }

    private void executeDockerCompLayerScan(final Tool tool, final String tag, final DockerRegistry registry,
                                            final String lastLayer, final String layerDigest, final String layerRef)
            throws IOException {
        if (dockerComponentService == null) {
            return;
        }
        final DockerComponentScanRequest dockerComponentScanRequest;
        if (registry.isPipelineAuth()) {
            dockerComponentScanRequest = new DockerComponentScanRequest(layerRef, layerDigest, registry.getPath(),
                    tool.getImage(), lastLayer, dockerRegistryManager.getImageToken(registry, tool.getImage()));
        } else {
            dockerComponentScanRequest = new DockerComponentScanRequest(layerRef, layerDigest, registry.getPath(),
                    tool.getImage(), lastLayer, registry.getUserName(), registry.getPassword());
        }
        ToolScanRestApiUtils.checkExecutionStatus(
                dockerComponentService.scanLayer(dockerComponentScanRequest).execute(), tool.getImage(), tag, layerRef);

    }

    private ManifestV2 getManifest(final Tool tool, final String tag, final DockerRegistry registry)
            throws ToolScanExternalServiceException {
        final DockerClient dockerClient = getDockerClient(tool.getImage(), registry);
        return dockerClient.getManifest(registry, tool.getImage(), tag)
                .orElseThrow(() -> new ToolScanExternalServiceException(tool, messageHelper.getMessage(
                        MessageConstants.ERROR_REGISTRY_COULD_NOT_GET_MANIFEST, tool.getImage())));
    }

    private List<String> fetchLayers(final ManifestV2 manifest) {
        return manifest.getLayers()
            .stream()
            .map(ManifestV2.Config::getDigest)
            .collect(Collectors.toList());
    }

    private DockerClient getDockerClient(String tool, DockerRegistry registry) {
        String token = dockerRegistryManager.getImageToken(registry, tool);
        return dockerClientFactory.getDockerClient(registry, token);
    }

    private ToolVersionScanResult gatherResults(final Tool tool, final String tag,
                                                final String lastLayerRef, final String digest,
                                                final String defaultCmd, final int layersCount,
                                                final boolean hasNvidiaMark,
                                                final Optional<ClairVulnerabilities> clairResults)
            throws IOException {
        ToolScanStatus toolScanStatus = ToolScanStatus.COMPLETED;
        if (!clairResults.isPresent()) {
            toolScanStatus = ToolScanStatus.FAILED;
        }

        final Map<VulnerabilitySeverity, Integer> vulnerabilitiesCount = clairResults
                .map(ClairVulnerabilities::getVulnerabilitiesCount)
                .map(MapUtils::emptyIfNull)
                .orElse(Collections.emptyMap());
        final List<Vulnerability> vulnerabilities = clairResults
                .map(ClairVulnerabilities::getVulnerabilities)
                .map(ListUtils::emptyIfNull)
                .orElse(Collections.emptyList());

        LOGGER.debug("Found: " + vulnerabilities.size() + " vulnerabilities for " + tool.getImage() + ":" + tag);

        final Optional<DockerComponentScanResult> compScanResult = ToolScanRestApiUtils.getScanResult(
                dockerComponentService != null, () -> dockerComponentService.getScanResult(lastLayerRef));

        if (!compScanResult.isPresent()) {
            toolScanStatus = ToolScanStatus.FAILED;
        }

        //Concat dependencies from Clair and DockerCompScan
        final List<ToolDependency> dependencies = Stream.concat(
                compScanResult.map(result ->
                                ListUtils.emptyIfNull(result.getLayers()).stream())
                        .orElse(Stream.empty())
                        .flatMap(l -> l.getDependencies().stream().peek(dependency -> {
                            dependency.setToolVersion(tag);
                            dependency.setToolId(tool.getId());
                        })),
                clairResults
                        .map(ClairVulnerabilities::getDependencies)
                        .map(ListUtils::emptyIfNull)
                        .orElse(Collections.emptyList()).stream()
        ).distinct().collect(Collectors.toList());

        LOGGER.debug("Found: " + dependencies.size() + " dependencies for " + tool.getImage() + ":" + tag);

        final ToolOSVersion osVersion = dependencies.stream()
                .filter(td -> td.getEcosystem() == ToolDependency.Ecosystem.OS)
                .findFirst().map(td -> new ToolOSVersion(td.getName(), td.getVersion()))
                .orElseGet(() -> createEmptyToolOsVersion(tool, tag));

        final boolean cudaAvailable = hasNvidiaMark && hasNvidiaInstalled(dependencies, tool, tag);

        final ToolVersionScanResult result = new ToolVersionScanResult(tag, osVersion, vulnerabilities,
                dependencies, toolScanStatus, lastLayerRef, digest, defaultCmd, layersCount);
        result.setVulnerabilitiesCount(vulnerabilitiesCount);
        result.setCudaAvailable(cudaAvailable);
        return result;
    }

    private ToolOSVersion createEmptyToolOsVersion(final Tool tool, final String tag) {
        return Optional.of(toolManager.loadToolVersionAttributes(tool.getId(), tag))
            .map(ToolVersionAttributes::getAttributes)
            .map(ToolVersion::getPlatform)
            .filter(WINDOWS_PLATFORM::equalsIgnoreCase)
            .map(platform -> new ToolOSVersion(WINDOWS_PLATFORM, StringUtils.EMPTY))
            .orElse(new ToolOSVersion(NOT_DETERMINED, NOT_DETERMINED));
    }

    private boolean hasNvidiaMark(final DockerClient client, final DockerRegistry registry, final String image,
                                  final String tag) {
        final Set<String> nvidiaLabels = preferenceManager.getPreference(
                SystemPreferences.DOCKER_SECURITY_CUDNN_VERSION_LABEL);
        if (CollectionUtils.isEmpty(nvidiaLabels)) {
            return true;
        }
        final Map<String, String> imageLabels = MapUtils.emptyIfNull(client.getImageLabels(registry, image, tag));
        if (MapUtils.isEmpty(imageLabels)) {
            return false;
        }
        final boolean hasNvidia = Stream.concat(SetUtils.emptyIfNull(imageLabels.keySet()).stream(),
                        CollectionUtils.emptyIfNull(imageLabels.values()).stream())
                .anyMatch(imageLabel -> nvidiaLabels.stream()
                        .anyMatch(nvidiaLabel -> StringUtils.containsIgnoreCase(imageLabel, nvidiaLabel)));
        if (hasNvidia) {
            LOGGER.debug("Found nvidia label for tool '{}:{}'", image, tag);
        }
        return hasNvidia;
    }

    private boolean hasNvidiaInstalled(final List<ToolDependency> dependencies, final Tool tool, final String tag) {
        final boolean nvidiaInstalled = dependencies.stream()
                .anyMatch(toolDependency -> ToolDependency.Ecosystem.NVIDIA.equals(toolDependency.getEcosystem()));
        if (nvidiaInstalled) {
            LOGGER.debug("Found nvidia version file for tool '{}:{}'", tool.getImage(), tag);
        }
        return nvidiaInstalled;
    }

    private ClairClient initClairClient() {
        final String clairVersion = preferenceManager.getPreference(
                SystemPreferences.DOCKER_SECURITY_TOOL_SCAN_CLAIR_VERSION);
        if (StringUtils.isBlank(clairVersion)) {
            return null;
        }
        if (CLAIR_V2.equalsIgnoreCase(clairVersion)) {
            final ClairV2Api client = initClient(
                    SystemPreferences.DOCKER_SECURITY_TOOL_SCAN_CLAIR_ROOT_URL, ClairV2Api.class);
            return Objects.isNull(client) ? null : new ClairV2Client(client);
        }
        if (CLAIR_V4.equalsIgnoreCase(clairVersion)) {
            final ClairV4Api client = initClient(
                    SystemPreferences.DOCKER_SECURITY_TOOL_SCAN_CLAIR_ROOT_URL, ClairV4Api.class);
            return Objects.isNull(client) ? null : new ClairV4Client(client);
        }
        log.warn("Unsupported Clair version: '{}'", clairVersion);
        return null;
    }

    private Optional<ClairVulnerabilities> getClairScanResults(final Tool tool, final String tag,
                                                               final DockerRegistry registry, final List<String> layers,
                                                               final ManifestV2 manifest) throws IOException {
        if (Objects.isNull(clairService)) {
            return Optional.empty();
        }

        final String imageToken = registry.isPipelineAuth()
                ? dockerRegistryManager.getImageToken(registry, tool.getImage())
                : null;

        final String resultsMark = clairService.scanLayers(tool.getImage(), registry, layers, manifest.getDigest(),
                imageToken, tag);
        return Objects.isNull(resultsMark)
                ? Optional.empty()
                : clairService.getScanResult(resultsMark, tool.getId(), tag);
    }
}
