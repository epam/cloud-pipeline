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

package com.epam.pipeline.manager.docker.scan.clair.v2;

import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.pipeline.ToolScanStatus;
import com.epam.pipeline.entity.scan.ToolDependency;
import com.epam.pipeline.entity.scan.Vulnerability;
import com.epam.pipeline.entity.scan.VulnerabilitySeverity;
import com.epam.pipeline.manager.docker.scan.ToolScanRestApiUtils;
import com.epam.pipeline.manager.docker.scan.clair.ClairClient;
import com.epam.pipeline.manager.docker.scan.clair.ClairScanRequest;
import com.epam.pipeline.manager.docker.scan.clair.ClairScanResult;
import com.epam.pipeline.entity.scan.clair.ClairVulnerabilities;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@RequiredArgsConstructor
public class ClairV2Client implements ClairClient {

    private final ClairV2Api client;

    @Override
    public String scanLayers(final String image, final DockerRegistry registry, final List<String> layers,
                             final String manifestDigest, final String imageToken, final String tag)
            throws IOException {
        if (Objects.isNull(client)) {
            return null;
        }

        String lastLayer = null;
        for (int i = 0; i < layers.size(); i++) {
            log.debug("Scanning clair {}:{}, started for {} of {} layers", image, tag, i + 1, layers.size());
            final String layerDigest = layers.get(i);
            final String layerRef = getLayerName(image, tag);

            executeClairLayerScan(image, registry, lastLayer, layerDigest, layerRef, imageToken, tag);

            lastLayer = layerRef;
        }
        log.debug("Scanning clair {}:{} done.", image, tag);
        return lastLayer;
    }

    private void executeClairLayerScan(final String image, final DockerRegistry registry, final String prevLayer,
                                       final String layerDigest, final String layerRef, final String imageToken,
                                       final String tag) throws IOException {
        if (Objects.isNull(client)) {
            return;
        }

        final ClairScanRequest clairRequest = registry.isPipelineAuth()
                ? new ClairScanRequest(layerRef, layerDigest, registry.getPath(), image, prevLayer,
                imageToken)
                : new ClairScanRequest(layerRef, layerDigest, registry.getPath(), image, prevLayer,
                registry.getUserName(), registry.getPassword());

        ToolScanRestApiUtils.checkExecutionStatus(
                client.scanLayer(clairRequest).execute(), image, tag, layerRef);
    }

    @Override
    public Optional<ClairVulnerabilities> getScanResult(final String lastLayerRef, final Long toolId, final String tag)
            throws IOException {
        if (Objects.isNull(client)) {
            return Optional.empty();
        }

        final ToolScanStatus toolScanStatus = ToolScanStatus.COMPLETED;
        final Optional<ClairScanResult> clairScanResult =
                ToolScanRestApiUtils.getScanResult(true, () -> client.getScanResult(lastLayerRef));

        if (!clairScanResult.isPresent()) {
            return Optional.empty();
        }

        final Map<VulnerabilitySeverity, Integer> vulnerabilitiesCount = new HashMap<>();
        final List<Vulnerability> vulnerabilities = clairScanResult
                .map(result -> ListUtils.emptyIfNull(result.getFeatures()).stream())
                .orElse(Stream.empty())
                .flatMap(f -> f.getVulnerabilities() != null ? f.getVulnerabilities().stream().map(v -> {
                    Vulnerability vulnerability = new Vulnerability();
                    vulnerability.setName(v.getName());
                    vulnerability.setDescription(v.getDescription());
                    vulnerability.setFixedBy(v.getFixedBy());
                    vulnerability.setLink(v.getLink());
                    vulnerability.setSeverity(v.getSeverity());
                    vulnerability.setFeature(f.getName());
                    vulnerability.setFeatureVersion(f.getVersion());
                    vulnerabilitiesCount.merge(v.getSeverity(), 1,  (oldVal, newVal) -> oldVal + 1);
                    return vulnerability;
                }) : Stream.empty())
                .collect(Collectors.toList());

        final List<ToolDependency> dependencies = clairScanResult
                .map(result -> ListUtils.emptyIfNull(result.getFeatures()).stream())
                .orElse(Stream.empty())
                .map(f -> new ToolDependency(toolId, tag, f.getName(), f.getVersion(),
                        ToolDependency.Ecosystem.SYSTEM, null))
                .collect(Collectors.toList());

        return Optional.of(ClairVulnerabilities.builder()
                .toolScanStatus(toolScanStatus)
                .vulnerabilitiesCount(vulnerabilitiesCount)
                .vulnerabilities(vulnerabilities)
                .dependencies(dependencies)
                .build());
    }

    private String getLayerName(final String image, final String tag) throws UnsupportedEncodingException {
        return URLEncoder.encode(image, "UTF-8") + ":" + URLEncoder.encode(tag, "UTF-8")
                + ":" + UUID.randomUUID();
    }
}
