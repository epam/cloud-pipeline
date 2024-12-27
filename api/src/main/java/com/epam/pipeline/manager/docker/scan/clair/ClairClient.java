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

package com.epam.pipeline.manager.docker.scan.clair;

import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.scan.clair.ClairVulnerabilities;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public interface ClairClient {

    /**
     * Initiates clair scan process for given layers.
     *
     * @param image - tool's image
     * @param registry - registry object this image belongs to
     * @param layers - list of layers to scan
     * @param manifestDigest - manifest digest, relevant for ClairV4 only
     * @param imageToken - image token, relevant for pipeline auth only
     * @param tag - tool's tag
     * @return the marker string to get results, depends on the clair's version:
     *         - if ClairV2 the last layer reference shall be returner
     *         - if ClairV4 the manifest digest expected
     * @throws IOException error
     */
    String scanLayers(String image, DockerRegistry registry, List<String> layers, String manifestDigest,
                      String imageToken, String tag) throws IOException;

    /**
     * Returns Clair's vulnerability scan results
     *
     * @param resultsMark - the marker string from {@link ClairClient#scanLayers} method results.
     * @param toolId - tool's ID
     * @param tag - tool's tag
     * @return retrieved vulnerabilities if present
     * @throws IOException error
     */
    Optional<ClairVulnerabilities> getScanResult(String resultsMark, Long toolId, String tag) throws IOException;

    default String buildLayerPath(final String registryPath, final String image, final String digest) {
        return String.format("https://%s/v2/%s/blobs/%s", registryPath, image, digest);
    }
}
