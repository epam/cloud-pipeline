/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.docker;

import com.epam.pipeline.entity.docker.ManifestV2;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.manager.pipeline.ToolUtils;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves the digest, which a run's docker image tag points to, for the run's pod to be pinned to it.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DockerImageDigestResolver {

    // a digest as the OCI image specification defines it, e.g. sha256:6c3c62...
    private static final Pattern DIGEST = Pattern.compile("^[a-z0-9]+(?:[+._-][a-z0-9]+)*:[a-zA-Z0-9=_-]+$");

    private final PreferenceManager preferenceManager;
    private final DockerRegistryManager dockerRegistryManager;

    /**
     * Resolves the digest to pin a run's pod to, if the pinning is enabled.
     *
     * The digest is resolved for the very image the pod is created with, so the pinned image always names
     * the repository the digest belongs to. A digest, which cannot be resolved, is not a launch error:
     * such a run is launched with the docker image tag, just as it is launched while the pinning is disabled.
     *
     * @param dockerImage the run's docker image, in the registry/group/tool:tag form
     * @return the digest, or an empty value if the pinning is disabled or the digest cannot be resolved
     */
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public Optional<String> findDigestToPin(final String dockerImage) {
        if (!Boolean.TRUE.equals(preferenceManager.getPreference(SystemPreferences.LAUNCH_DOCKER_IMAGE_PIN_DIGEST))) {
            return Optional.empty();
        }
        try {
            final Optional<String> digest = resolveDigest(dockerImage);
            if (!digest.isPresent()) {
                log.warn("Docker image {} will be pulled by the tag: no valid digest is found in the registry.",
                        dockerImage);
            }
            return digest;
        } catch (RuntimeException e) {
            log.warn("Docker image {} will be pulled by the tag: its digest cannot be resolved. {}",
                    dockerImage, e.getMessage());
            log.debug(e.getMessage(), e);
            return Optional.empty();
        }
    }

    private Optional<String> resolveDigest(final String dockerImage) {
        final Matcher matcher = ToolUtils.REPOSITORY_AND_IMAGE.matcher(dockerImage);
        if (!matcher.find()) {
            return Optional.empty();
        }
        final DockerRegistry registry = dockerRegistryManager.loadByNameOrId(matcher.group(1));
        if (registry == null) {
            return Optional.empty();
        }
        final String imageWithTag = matcher.group(2);
        final String image = ToolUtils.getImageWithoutTag(imageWithTag);
        final String tag = ToolUtils.getImageTag(imageWithTag);
        return dockerRegistryManager.getDockerClient(registry, image)
                .getManifest(registry, image, tag)
                .map(ManifestV2::getDigest)
                .filter(value -> DIGEST.matcher(value).matches());
    }
}
