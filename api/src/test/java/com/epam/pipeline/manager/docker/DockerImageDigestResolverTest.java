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
import com.epam.pipeline.exception.docker.DockerConnectionException;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import org.junit.Before;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;

public class DockerImageDigestResolverTest {

    private static final Long REGISTRY_ID = 1L;
    private static final String REGISTRY_PATH = "registry:443";
    private static final String IMAGE = "group/tool";
    private static final String TAG = "latest";
    private static final String DOCKER_IMAGE = REGISTRY_PATH + "/" + IMAGE + ":" + TAG;
    private static final String DIGEST = "sha256:6c3c624b58dbbcd3c0dd82b4c53f04194d1247c6eebdaab7c610cf7d66709b3b";

    private final PreferenceManager preferenceManager = mock(PreferenceManager.class);
    private final DockerRegistryManager dockerRegistryManager = mock(DockerRegistryManager.class);
    private final DockerClient dockerClient = mock(DockerClient.class);
    private final DockerImageDigestResolver resolver =
            new DockerImageDigestResolver(preferenceManager, dockerRegistryManager);
    private final DockerRegistry registry = new DockerRegistry();

    @Before
    public void setUp() {
        registry.setId(REGISTRY_ID);
        registry.setPath(REGISTRY_PATH);
        mockPinning(true);
        doReturn(registry).when(dockerRegistryManager).loadByNameOrId(REGISTRY_PATH);
        doReturn(dockerClient).when(dockerRegistryManager).getDockerClient(registry, IMAGE);
    }

    @Test
    public void shouldNotResolveDigestIfPinningIsDisabled() {
        mockPinning(false);

        assertEquals(Optional.empty(), resolver.findDigestToPin(DOCKER_IMAGE));
        verifyZeroInteractions(dockerRegistryManager);
    }

    @Test
    public void shouldResolveDigestOfImageTag() {
        mockDigest(DIGEST);

        assertEquals(Optional.of(DIGEST), resolver.findDigestToPin(DOCKER_IMAGE));
    }

    @Test
    public void shouldNotResolveDigestIfManifestIsNotFound() {
        doReturn(Optional.empty()).when(dockerClient).getManifest(registry, IMAGE, TAG);

        assertEquals(Optional.empty(), resolver.findDigestToPin(DOCKER_IMAGE));
    }

    @Test
    public void shouldNotResolveInvalidDigest() {
        mockDigest("not a digest");

        assertEquals(Optional.empty(), resolver.findDigestToPin(DOCKER_IMAGE));
    }

    @Test
    public void shouldNotResolveDigestIfRegistryIsUnavailable() {
        doThrow(new DockerConnectionException(REGISTRY_PATH, "unavailable"))
                .when(dockerClient).getManifest(any(), anyString(), anyString());

        assertEquals(Optional.empty(), resolver.findDigestToPin(DOCKER_IMAGE));
    }

    @Test
    public void shouldNotResolveDigestIfRegistryIsUnknown() {
        assertEquals(Optional.empty(), resolver.findDigestToPin("unknown:443/" + IMAGE + ":" + TAG));
    }

    @Test
    public void shouldNotResolveDigestOfImageWithoutRegistry() {
        mockDigest(DIGEST);

        assertEquals(Optional.empty(), resolver.findDigestToPin(IMAGE + ":" + TAG));
    }

    @Test
    public void shouldNotResolveDigestOfImageWithoutTag() {
        mockDigest(DIGEST);

        assertEquals(Optional.empty(), resolver.findDigestToPin(REGISTRY_PATH + "/" + IMAGE));
    }

    private void mockPinning(final boolean enabled) {
        doReturn(enabled).when(preferenceManager).getPreference(SystemPreferences.LAUNCH_DOCKER_IMAGE_PIN_DIGEST);
    }

    private void mockDigest(final String digest) {
        final ManifestV2 manifest = new ManifestV2();
        manifest.setDigest(digest);
        doReturn(Optional.of(manifest)).when(dockerClient).getManifest(registry, IMAGE, TAG);
    }
}
