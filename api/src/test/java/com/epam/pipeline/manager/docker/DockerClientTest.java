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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.response.DefaultResponseCreator;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

public class DockerClientTest {

    private static final String REGISTRY_PATH = "registry:443";
    private static final String IMAGE = "library/image";
    private static final String TAG = "1.0";
    private static final String TOKEN = "token";
    private static final String MANIFEST_URL = "https://registry:443/v2/library/image/manifests/1.0";
    private static final String TAGS_URL = "https://registry:443/v2/library/image/tags/list";
    private static final String DIGEST_URL_FORMAT = "https://registry:443/v2/library/image/manifests/%s";
    private static final String DIGEST = "sha256:2d9a4d1c9c9b3d92c8dc2fbd0e3e2f5b1a6c7d8e9f0a1b2c3d4e5f60718293a4";
    /**
     * A digest of a manifest list, that does not reference any image manifests.
     */
    private static final String EMPTY_MANIFEST_LIST_DIGEST =
            "sha256:9d23fb26fef6bd93a9ec12b89d4674b270a7a54cb09dfed6933c0abcacddfc0f";
    private static final String MANIFEST_MEDIA_TYPE = "application/vnd.docker.distribution.manifest.v2+json";
    private static final String MANIFEST_LIST_MEDIA_TYPE =
            "application/vnd.docker.distribution.manifest.list.v2+json";
    private static final String OCI_MANIFEST_MEDIA_TYPE = "application/vnd.oci.image.manifest.v1+json";
    private static final String OCI_INDEX_MEDIA_TYPE = "application/vnd.oci.image.index.v1+json";
    private static final String ACCEPTED_MANIFEST_FORMATS = MANIFEST_MEDIA_TYPE + "," + MANIFEST_LIST_MEDIA_TYPE
            + "," + OCI_MANIFEST_MEDIA_TYPE + "," + OCI_INDEX_MEDIA_TYPE;
    private static final String EMPTY_MANIFEST_LIST = "{\"schemaVersion\":2,\"mediaType\":\""
            + MANIFEST_LIST_MEDIA_TYPE + "\",\"manifests\":[]}";
    private static final String CONFIG_DIGEST = "sha256:config";
    private static final String MANIFEST_BODY = "{\"schemaVersion\":2,\"mediaType\":\"" + MANIFEST_MEDIA_TYPE
            + "\",\"config\":{\"mediaType\":\"application/vnd.docker.container.image.v1+json\",\"size\":1,"
            + "\"digest\":\"" + CONFIG_DIGEST + "\"},\"layers\":[{\"mediaType\":\""
            + "application/vnd.docker.image.rootfs.diff.tar.gzip\",\"size\":2,\"digest\":\"sha256:layer\"}]}";
    /**
     * A manifest, that references no image configuration, e.g. a legacy schema 1 one
     */
    private static final String MANIFEST_WITHOUT_CONFIG_BODY = "{\"schemaVersion\":1,\"name\":\"library/image\"}";
    private static final String LIST_DIGEST =
            "sha256:1f0e3dad99908345f7439f8ffabdffc418e4d3f7c3d4e5a6b7c8d9e0f1a2b3c4";
    private static final String AMD_DIGEST = "sha256:3c59dc048e8850243be8079a5c74d079e2f4d9d8e1a8f0b3c9d2e1f0a9b8c7d6";
    private static final String ARM_DIGEST = "sha256:b6d767d2f8ed5d21a44b0e5886680cb9c1b1b2b3c4d5e6f708192a3b4c5d6e7f";
    private static final String ATTESTATION_DIGEST =
            "sha256:37693cfc748049e45d87b8c7d8b9aacd1e4a3f6a2b3c4d5e6f708192a3b4c5d6";
    private static final String LINUX_OS = "linux";
    private static final String AMD_ARCHITECTURE = "amd64";
    private static final String ARM_ARCHITECTURE = "arm64";
    private static final String UNKNOWN_PLATFORM = "unknown";
    private static final String TAGS_BODY = "{\"name\":\"library/image\",\"tags\":[\"1.0\"]}";
    private static final String CONNECTION_ERROR = "Connection refused";
    private static final String DOCKER_CONTENT_DIGEST = "Docker-Content-Digest";

    private DockerClient dockerClient;
    private MockRestServiceServer server;

    @BeforeEach
    public void setUp() {
        dockerClient = new DockerClient(registry(), new ObjectMapper(), TOKEN);
        final RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(dockerClient, "restTemplate");
        server = MockRestServiceServer.bindTo(restTemplate).build();
    }

    @Test
    public void shouldRemoveTagByPushingAndDeletingEmptyManifestList() {
        server.expect(requestTo(MANIFEST_URL))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MANIFEST_LIST_MEDIA_TYPE))
                .andExpect(content().string(EMPTY_MANIFEST_LIST))
                .andRespond(digestResponse(HttpStatus.CREATED, DIGEST));
        server.expect(requestTo(digestUrl(DIGEST)))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.ACCEPTED));

        assertTrue(dockerClient.untagImage(registry(), IMAGE, TAG));
        server.verify();
    }

    @Test
    public void shouldCalculateTemporaryManifestDigestIfRegistryDoesNotReturnIt() {
        server.expect(requestTo(MANIFEST_URL))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withStatus(HttpStatus.CREATED));
        server.expect(requestTo(digestUrl(EMPTY_MANIFEST_LIST_DIGEST)))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.ACCEPTED));

        assertTrue(dockerClient.untagImage(registry(), IMAGE, TAG));
        server.verify();
    }

    @Test
    public void shouldNotFailIfTemporaryManifestIsAlreadyDeleted() {
        server.expect(requestTo(MANIFEST_URL))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(digestResponse(HttpStatus.CREATED, DIGEST));
        server.expect(requestTo(digestUrl(DIGEST)))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertFalse(dockerClient.untagImage(registry(), IMAGE, TAG));
        server.verify();
    }

    @Test
    public void shouldFailTagRemovalIfTemporaryManifestCannotBePushed() {
        server.expect(requestTo(MANIFEST_URL))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThrows(DockerConnectionException.class, () -> dockerClient.untagImage(registry(), IMAGE, TAG));
    }

    @Test
    public void shouldReturnEmptyManifestIfItIsNotFound() {
        server.expect(requestTo(MANIFEST_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertFalse(dockerClient.getManifest(registry(), IMAGE, TAG).isPresent());
        server.verify();
    }

    @Test
    public void shouldFailManifestRetrievalIfRegistryReturnsClientError() {
        server.expect(requestTo(MANIFEST_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThrows(DockerConnectionException.class, () -> dockerClient.getManifest(registry(), IMAGE, TAG));
    }

    @Test
    public void shouldFailManifestRetrievalIfRegistryFails() {
        server.expect(requestTo(MANIFEST_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThrows(DockerConnectionException.class, () -> dockerClient.getManifest(registry(), IMAGE, TAG));
    }

    @Test
    public void shouldFailManifestRetrievalIfRegistryIsNotReachable() {
        // a registry, that cannot be reached at all, shall be reported as any other registry communication error
        server.expect(requestTo(MANIFEST_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(request -> {
                    throw new IOException(CONNECTION_ERROR);
                });

        assertThrows(DockerConnectionException.class, () -> dockerClient.getManifest(registry(), IMAGE, TAG));
    }

    @Test
    public void shouldFailTagsListingIfRegistryIsNotReachable() {
        server.expect(requestTo(TAGS_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(request -> {
                    throw new IOException(CONNECTION_ERROR);
                });

        assertThrows(DockerConnectionException.class, () -> dockerClient.findImageTags(REGISTRY_PATH, IMAGE));
    }

    @Test
    public void shouldFailTagRemovalIfRegistryIsNotReachable() {
        server.expect(requestTo(MANIFEST_URL))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(request -> {
                    throw new IOException(CONNECTION_ERROR);
                });

        assertThrows(DockerConnectionException.class, () -> dockerClient.untagImage(registry(), IMAGE, TAG));
    }

    @Test
    public void shouldDeleteManifestByDigestResolvedByTag() {
        server.expect(requestTo(MANIFEST_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(manifestResponse(MANIFEST_MEDIA_TYPE, DIGEST, MANIFEST_BODY));
        server.expect(requestTo(digestUrl(DIGEST)))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.ACCEPTED));

        final Optional<ManifestV2> manifest = dockerClient.deleteImage(registry(), IMAGE, TAG);

        assertTrue(manifest.isPresent());
        assertEquals(DIGEST, manifest.get().getDigest());
        server.verify();
    }

    @Test
    public void shouldNotSendDeletionRequestIfManifestCannotBeResolved() {
        server.expect(requestTo(MANIFEST_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertFalse(dockerClient.deleteImage(registry(), IMAGE, TAG).isPresent());
        server.verify();
    }

    @Test
    public void shouldReturnNoTagsIfImageIsNotFound() {
        server.expect(requestTo(TAGS_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertTrue(dockerClient.findImageTags(REGISTRY_PATH, IMAGE).isEmpty());
        server.verify();
    }

    @Test
    public void shouldReturnTagsListedByRegistry() {
        server.expect(requestTo(TAGS_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(TAGS_BODY, MediaType.APPLICATION_JSON));

        assertEquals(Collections.singletonList(TAG), dockerClient.findImageTags(REGISTRY_PATH, IMAGE));
        server.verify();
    }

    @Test
    public void shouldFailTagsListingIfRegistryReturnsClientError() {
        server.expect(requestTo(TAGS_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThrows(DockerConnectionException.class, () -> dockerClient.findImageTags(REGISTRY_PATH, IMAGE));
    }

    @Test
    public void shouldRequestManifestsOfAllSupportedFormats() {
        server.expect(requestTo(MANIFEST_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.ACCEPT, ACCEPTED_MANIFEST_FORMATS))
                .andRespond(manifestResponse(MANIFEST_MEDIA_TYPE, DIGEST, MANIFEST_BODY));

        assertTrue(dockerClient.getManifest(registry(), IMAGE, TAG).isPresent());
        server.verify();
    }

    @Test
    public void shouldResolveManifestListToImageManifestOfDefaultPlatform() {
        server.expect(requestTo(MANIFEST_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(manifestResponse(MANIFEST_LIST_MEDIA_TYPE, LIST_DIGEST,
                        manifestList(MANIFEST_LIST_MEDIA_TYPE,
                                reference(ARM_DIGEST, LINUX_OS, ARM_ARCHITECTURE),
                                reference(AMD_DIGEST, LINUX_OS, AMD_ARCHITECTURE))));
        server.expect(requestTo(digestUrl(AMD_DIGEST)))
                .andExpect(method(HttpMethod.GET))
                .andRespond(manifestResponse(MANIFEST_MEDIA_TYPE, AMD_DIGEST, MANIFEST_BODY));

        final Optional<ManifestV2> manifest = dockerClient.resolveImageManifest(registry(), IMAGE, TAG);

        assertTrue(manifest.isPresent());
        assertEquals(CONFIG_DIGEST, manifest.get().getConfig().getDigest());
        // a digest of the manifest, a tag points to, identifies an image version and shall be preserved
        assertEquals(LIST_DIGEST, manifest.get().getDigest());
        server.verify();
    }

    @Test
    public void shouldSkipManifestsOfUnknownPlatformWhileResolvingOciIndex() {
        server.expect(requestTo(MANIFEST_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(manifestResponse(OCI_INDEX_MEDIA_TYPE, LIST_DIGEST,
                        manifestList(OCI_INDEX_MEDIA_TYPE,
                                reference(ATTESTATION_DIGEST, UNKNOWN_PLATFORM, UNKNOWN_PLATFORM),
                                reference(ARM_DIGEST, LINUX_OS, ARM_ARCHITECTURE))));
        server.expect(requestTo(digestUrl(ARM_DIGEST)))
                .andExpect(method(HttpMethod.GET))
                .andRespond(manifestResponse(OCI_MANIFEST_MEDIA_TYPE, ARM_DIGEST, MANIFEST_BODY));

        final Optional<ManifestV2> manifest = dockerClient.resolveImageManifest(registry(), IMAGE, TAG);

        assertTrue(manifest.isPresent());
        assertEquals(CONFIG_DIGEST, manifest.get().getConfig().getDigest());
        server.verify();
    }

    @Test
    public void shouldFailManifestResolutionIfReferencedManifestIsNotFound() {
        server.expect(requestTo(MANIFEST_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(manifestResponse(MANIFEST_LIST_MEDIA_TYPE, LIST_DIGEST,
                        manifestList(MANIFEST_LIST_MEDIA_TYPE, reference(AMD_DIGEST, LINUX_OS, AMD_ARCHITECTURE))));
        server.expect(requestTo(digestUrl(AMD_DIGEST)))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThrows(DockerConnectionException.class,
            () -> dockerClient.resolveImageManifest(registry(), IMAGE, TAG));
        server.verify();
    }

    @Test
    public void shouldDeleteManifestListItselfRatherThanReferencedImageManifest() {
        // the deletion of a referenced image manifest leaves a tag pointing to a broken manifest list behind
        server.expect(requestTo(MANIFEST_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(manifestResponse(MANIFEST_LIST_MEDIA_TYPE, LIST_DIGEST,
                        manifestList(MANIFEST_LIST_MEDIA_TYPE, reference(AMD_DIGEST, LINUX_OS, AMD_ARCHITECTURE))));
        server.expect(requestTo(digestUrl(LIST_DIGEST)))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.ACCEPTED));

        final Optional<ManifestV2> manifest = dockerClient.deleteImage(registry(), IMAGE, TAG);

        assertTrue(manifest.isPresent());
        assertEquals(LIST_DIGEST, manifest.get().getDigest());
        server.verify();
    }

    @Test
    public void shouldFailManifestResolutionIfManifestListReferencesNoImageManifests() {
        // an empty manifest list, e.g. a temporary one of a dangling tag, describes no image at all
        server.expect(requestTo(MANIFEST_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(manifestResponse(MANIFEST_LIST_MEDIA_TYPE, EMPTY_MANIFEST_LIST_DIGEST,
                        EMPTY_MANIFEST_LIST));

        assertThrows(DockerConnectionException.class,
            () -> dockerClient.resolveImageManifest(registry(), IMAGE, TAG));
        server.verify();
    }

    @Test
    public void shouldFailManifestResolutionIfManifestDescribesNoImage() {
        server.expect(requestTo(MANIFEST_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(manifestResponse(MANIFEST_MEDIA_TYPE, DIGEST, MANIFEST_WITHOUT_CONFIG_BODY));

        assertThrows(DockerConnectionException.class,
            () -> dockerClient.resolveImageManifest(registry(), IMAGE, TAG));
        server.verify();
    }

    @Test
    public void shouldDeleteTagPointingToEmptyManifestList() {
        // a dangling tag, that has been repointed to an empty manifest list, shall still be deletable
        server.expect(requestTo(MANIFEST_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(manifestResponse(MANIFEST_LIST_MEDIA_TYPE, EMPTY_MANIFEST_LIST_DIGEST,
                        EMPTY_MANIFEST_LIST));
        server.expect(requestTo(digestUrl(EMPTY_MANIFEST_LIST_DIGEST)))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.ACCEPTED));

        assertTrue(dockerClient.deleteImage(registry(), IMAGE, TAG).isPresent());
        server.verify();
    }

    @Test
    public void shouldFailManifestRetrievalIfRegistryReturnsNoDigest() {
        // a digest is the only way to address a manifest, e.g. to delete it
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(MANIFEST_MEDIA_TYPE));
        server.expect(requestTo(MANIFEST_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK).headers(headers).body(MANIFEST_BODY));

        assertThrows(DockerConnectionException.class, () -> dockerClient.getManifest(registry(), IMAGE, TAG));
    }

    @Test
    public void shouldFailDeletionIfRegistryFails() {
        server.expect(requestTo(MANIFEST_URL))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(digestResponse(HttpStatus.CREATED, DIGEST));
        server.expect(requestTo(digestUrl(DIGEST)))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThrows(DockerConnectionException.class, () -> dockerClient.untagImage(registry(), IMAGE, TAG));
        server.verify();
    }

    private DefaultResponseCreator manifestResponse(final String mediaType, final String digest, final String body) {
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(mediaType));
        headers.add(DOCKER_CONTENT_DIGEST, digest);
        return withStatus(HttpStatus.OK).headers(headers).body(body);
    }

    private DefaultResponseCreator digestResponse(final HttpStatus status, final String digest) {
        final HttpHeaders headers = new HttpHeaders();
        headers.add(DOCKER_CONTENT_DIGEST, digest);
        return withStatus(status).headers(headers);
    }

    private String manifestList(final String mediaType, final String... references) {
        return "{\"schemaVersion\":2,\"mediaType\":\"" + mediaType + "\",\"manifests\":["
                + String.join(",", references) + "]}";
    }

    private String reference(final String digest, final String os, final String architecture) {
        return "{\"mediaType\":\"" + MANIFEST_MEDIA_TYPE + "\",\"size\":100,\"digest\":\"" + digest
                + "\",\"platform\":{\"architecture\":\"" + architecture + "\",\"os\":\"" + os + "\"}}";
    }

    private String digestUrl(final String digest) {
        return String.format(DIGEST_URL_FORMAT, digest);
    }

    private DockerRegistry registry() {
        final DockerRegistry registry = new DockerRegistry();
        registry.setPath(REGISTRY_PATH);
        return registry;
    }
}
