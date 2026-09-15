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
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.response.DefaultResponseCreator;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.Optional;

import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
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
    private static final String EMPTY_MANIFEST_LIST = "{\"schemaVersion\":2,\"mediaType\":\""
            + MANIFEST_LIST_MEDIA_TYPE + "\",\"manifests\":[]}";
    private static final String MANIFEST_BODY = "{\"schemaVersion\":2,\"mediaType\":\"" + MANIFEST_MEDIA_TYPE
            + "\",\"config\":{\"mediaType\":\"application/vnd.docker.container.image.v1+json\",\"size\":1,"
            + "\"digest\":\"sha256:config\"},\"layers\":[{\"mediaType\":\""
            + "application/vnd.docker.image.rootfs.diff.tar.gzip\",\"size\":2,\"digest\":\"sha256:layer\"}]}";
    private static final String TAGS_BODY = "{\"name\":\"library/image\",\"tags\":[\"1.0\"]}";
    private static final String DOCKER_CONTENT_DIGEST = "Docker-Content-Digest";

    private DockerClient dockerClient;
    private MockRestServiceServer server;

    @Before
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

    private String digestUrl(final String digest) {
        return String.format(DIGEST_URL_FORMAT, digest);
    }

    private DockerRegistry registry() {
        final DockerRegistry registry = new DockerRegistry();
        registry.setPath(REGISTRY_PATH);
        return registry;
    }
}
