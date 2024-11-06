/*
 * Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.docker.scan.clair.v4;

import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.pipeline.ToolScanStatus;
import com.epam.pipeline.entity.scan.ToolDependency;
import com.epam.pipeline.entity.scan.Vulnerability;
import com.epam.pipeline.entity.scan.VulnerabilitySeverity;
import com.epam.pipeline.entity.scan.clair.ClairVulnerabilities;
import com.epam.pipeline.entity.scan.clair.v4.ClairIndexReport;
import com.epam.pipeline.entity.scan.clair.v4.ClairIndexRequest;
import com.epam.pipeline.entity.scan.clair.v4.ClairIndexRequestLayer;
import com.epam.pipeline.entity.scan.clair.v4.ClairVulnerability;
import com.epam.pipeline.entity.scan.clair.v4.ClairVulnerabilityPackage;
import com.epam.pipeline.entity.scan.clair.v4.ClairVulnerabilityReport;
import com.epam.pipeline.manager.docker.scan.ToolScanRestApiUtils;
import com.epam.pipeline.manager.docker.scan.clair.ClairClient;
import com.epam.pipeline.utils.AuthorizationUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RequiredArgsConstructor
@Slf4j
public class ClairV4Client implements ClairClient {
    private static final String INDEX_FINISHED = "IndexFinished";
    private static final int POOLING_DELAY_MS = 500;
    private static final int MAX_ATTEMPTS = 20;

    private final ClairV4Api client;

    @Override
    public String scanLayers(final String image, final DockerRegistry registry, final List<String> layers,
                             final String manifestDigest, final String imageToken, final String tag)
            throws IOException {
        if (Objects.isNull(client)) {
            return null;
        }

        final ClairIndexRequest indexRequest = ClairIndexRequest.builder()
                .hash(manifestDigest)
                .layers(layers.stream()
                        .map(layerDigest -> ClairIndexRequestLayer.builder()
                                .hash(layerDigest)
                                .uri(buildLayerPath(registry.getPath(), image, layerDigest))
                                .headers(buildHeaders(registry, imageToken))
                                .build())
                        .collect(Collectors.toList()))
                .build();

        ToolScanRestApiUtils.checkExecutionStatus(
                client.scanLayer(indexRequest).execute(), image, tag, manifestDigest);

        return manifestDigest;
    }

    @Override
    public Optional<ClairVulnerabilities> getScanResult(final String manifestHash, final Long toolId, final String tag)
            throws IOException {
        if (Objects.isNull(client)) {
            return Optional.empty();
        }

        final ToolScanStatus toolScanStatus = waitForIndexReportReady(manifestHash);
        if (!ToolScanStatus.COMPLETED.equals(toolScanStatus)) {
            return Optional.empty();
        }

        final Optional<ClairVulnerabilityReport> clairScanResult = ToolScanRestApiUtils.getScanResult(
                true, () -> client.getVulnerabilitiesReport(manifestHash));

        if (!clairScanResult.isPresent()) {
            return Optional.empty();
        }

        final Map<VulnerabilitySeverity, Integer> vulnerabilitiesCount = new HashMap<>();
        final Map<String, ClairVulnerabilityPackage> packages = getPackages(clairScanResult.orElse(null));
        final List<ToolDependency> dependencies = packages.values().stream()
                .map(feature -> buildToolDependency(feature, toolId, tag))
                .collect(Collectors.toList());
        final List<Vulnerability> vulnerabilities = clairScanResult
                .map(result -> MapUtils.emptyIfNull(result.getVulnerabilities()).values().stream())
                .orElse(Stream.empty())
                .filter(Objects::nonNull)
                .map(clairVulnerability -> mapVulnerability(clairVulnerability, packages))
                .peek(vulnerability -> fillVulnerabilitiesCount(vulnerabilitiesCount, vulnerability))
                .collect(Collectors.toList());

        return Optional.of(ClairVulnerabilities.builder()
                .toolScanStatus(toolScanStatus)
                .vulnerabilitiesCount(vulnerabilitiesCount)
                .vulnerabilities(vulnerabilities)
                .dependencies(dependencies)
                .build());

    }

    private ToolScanStatus waitForIndexReportReady(final String layerDigest) throws IOException {
        for (int i = 0; i < MAX_ATTEMPTS; i++)  {
            final Optional<ClairIndexReport> indexResponse = ToolScanRestApiUtils.getScanResult(
                    true,  () -> client.getIndexReport(layerDigest));

            final Optional<String> indexState = indexResponse.flatMap(response -> {
                final String errorMessage = response.getErr();
                if (StringUtils.isNotBlank(errorMessage)) {
                    log.error("Scan process was not successful: '{}'", errorMessage);
                    return Optional.empty();
                }
                return Optional.of(response.getState());
            });
            
            if (!indexState.isPresent()) {
                return ToolScanStatus.FAILED;
            }

            if (INDEX_FINISHED.equals(indexState.get())) {
                return ToolScanStatus.COMPLETED;
            }

            log.debug("Index report not ready. Sleep for '{}' ms. '{}' attempts left.",
                    POOLING_DELAY_MS, MAX_ATTEMPTS - (i + 1));
            try {
                Thread.sleep(POOLING_DELAY_MS);
            } catch (InterruptedException e) {
                log.error(e.getMessage(), e);
                return ToolScanStatus.FAILED;
            }
        }
        log.error("Index report not ready after '{}' ms.", (MAX_ATTEMPTS * POOLING_DELAY_MS));
        return ToolScanStatus.FAILED;
    }

    private Map<String, List<String>> buildHeaders(final DockerRegistry registry, final String imageToken) {
        return registry.isPipelineAuth() ? buildBearerTokenAuthHeader(imageToken) : buildBasicAuthHeader(registry);
    }

    private Map<String, List<String>> buildBearerTokenAuthHeader(final String imageToken) {
        if (StringUtils.isNotBlank(imageToken)) {
            return buildAuthHeader(AuthorizationUtils.buildBearerTokenAuth(imageToken));
        }
        return Collections.emptyMap();
    }

    private Map<String, List<String>> buildBasicAuthHeader(final DockerRegistry registry) {
        final String userName = registry.getUserName();
        final String password = registry.getPassword();
        if (StringUtils.isBlank(userName) || StringUtils.isBlank(password)) {
            return Collections.emptyMap();
        }

        return buildAuthHeader(AuthorizationUtils.buildBasicAuth(userName, password));
    }

    private Map<String, List<String>> buildAuthHeader(final String auth) {
        return Collections.singletonMap(AuthorizationUtils.AUTH_HEADER, Collections.singletonList(auth));
    }

    private Vulnerability mapVulnerability(final ClairVulnerability clairVulnerability,
                                           final Map<String, ClairVulnerabilityPackage> packages) {
        final Vulnerability vulnerability = new Vulnerability();
        vulnerability.setName(clairVulnerability.getName());
        vulnerability.setDescription(clairVulnerability.getDescription());
        vulnerability.setFixedBy(clairVulnerability.getFixedBy());
        vulnerability.setLink(getFirstLink(clairVulnerability.getLinks()));
        vulnerability.setSeverity(parseSeverity(clairVulnerability.getSeverity()));
        final String vulnerabilityId = clairVulnerability.getId();
        packages.computeIfPresent(vulnerabilityId, (key, value) -> {
            vulnerability.setFeature(value.getName());
            vulnerability.setFeatureVersion(value.getVersion());
            return null;
        });
        return vulnerability;
    }

    private ToolDependency buildToolDependency(final ClairVulnerabilityPackage feature, final Long toolId,
                                               final String tag) {
        return new ToolDependency(toolId, tag, feature.getName(), feature.getVersion(),
                ToolDependency.Ecosystem.SYSTEM, null);
    }

    private void fillVulnerabilitiesCount(final Map<VulnerabilitySeverity, Integer> vulnerabilitiesCount,
                                          final Vulnerability vulnerability) {
        vulnerabilitiesCount.merge(vulnerability.getSeverity(), 1, (oldVal, newVal) -> oldVal + 1);
    }

    private VulnerabilitySeverity parseSeverity(final String clairSeverity) {
        if (StringUtils.isBlank(clairSeverity)) {
            return VulnerabilitySeverity.Unknown;
        }
        if (!Arrays.stream(VulnerabilitySeverity.values())
                .map(VulnerabilitySeverity::name)
                .collect(Collectors.toList())
                .contains(clairSeverity)) {
            log.debug("Unknown clair severity '{}'", clairSeverity);
            return VulnerabilitySeverity.Unknown;
        }
        return VulnerabilitySeverity.valueOf(clairSeverity);
    }

    private Map<String, String> invertMap(final Map<String, List<String>> map) {
        final Map<String, String> resultMap = new HashMap<>();
        map.forEach((key, valueList) -> valueList.forEach(value -> resultMap.putIfAbsent(value, key)));
        return resultMap;
    }

    private Map<String, ClairVulnerabilityPackage> getPackages(final ClairVulnerabilityReport results) {
        if (Objects.isNull(results)) {
            return Collections.emptyMap();
        }
        final Map<String, List<String>> vulnerabilitiesByPackage = MapUtils.emptyIfNull(
                results.getVulnerabilitiesByPackage());
        final Map<String, String> packageByVulnerability = invertMap(vulnerabilitiesByPackage);
        final Map<String, ClairVulnerabilityPackage> packages = MapUtils.emptyIfNull(results.getPackages());
        return packageByVulnerability.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry ->
                        packages.getOrDefault(entry.getValue(), null)));
    }

    private String getFirstLink(final String links) {
        if (StringUtils.isBlank(links)) {
            return StringUtils.EMPTY;
        }
        return Arrays.stream(links.split(" ")).collect(Collectors.toList()).get(0);
    }
}
