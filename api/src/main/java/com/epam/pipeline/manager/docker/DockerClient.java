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

import com.epam.pipeline.config.Constants;
import com.epam.pipeline.entity.docker.ImageDescription;
import com.epam.pipeline.entity.docker.ImageHistoryLayer;
import com.epam.pipeline.entity.docker.ManifestV2;
import com.epam.pipeline.entity.docker.HistoryEntryV2;
import com.epam.pipeline.entity.docker.RawImageDescriptionV2;
import com.epam.pipeline.entity.docker.RegistryListing;
import com.epam.pipeline.entity.docker.TagsListing;
import com.epam.pipeline.entity.docker.ToolVersion;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.exception.docker.DockerCertificateException;
import com.epam.pipeline.exception.docker.DockerConnectionException;
import com.epam.pipeline.exception.docker.DockerCredentialsException;
import com.epam.pipeline.exception.git.UnexpectedResponseStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.impl.DefaultRedirectStrategy;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.ssl.TrustStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.apache.hc.core5.http.HttpHeaders.AUTHORIZATION;
import static org.apache.hc.core5.http.HttpHeaders.COOKIE;

/**
 * Provides methods to operate Docker Registry API
 */
@SuppressWarnings("unchecked")
public class DockerClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(DockerClient.class);
    private static final String HEALTH_ENTRY_POINT = "https://%s/v2/";
    private static final String TAGS_LIST = "https://%s/v2/%s/tags/list";
    // TODO use docker registry paging API to list images
    private static final String LIST_REGISTRY_URL = "https://%s/v2/_catalog?n=1000";
    private static final String IMAGE_DESCRIPTION_URL = "https://%s/v2/%s/manifests/%s";
    private static final String BLOBS_URL = "https://%s/v2/%s/blobs/%s";

    private static final String V2_MANIFEST_FORMAT = "application/vnd.docker.distribution.manifest.v2+json";
    private static final String MANIFEST_LIST_FORMAT =
            "application/vnd.docker.distribution.manifest.list.v2+json";
    private static final String OCI_MANIFEST_FORMAT = "application/vnd.oci.image.manifest.v1+json";
    private static final String OCI_INDEX_FORMAT = "application/vnd.oci.image.index.v1+json";
    /**
     * All the manifest formats, that may be requested from a registry. A manifest of a format, that is not accepted,
     * is reported by a registry as a missing one, therefore all the formats, an image may be pushed in,
     * shall be listed here.
     */
    private static final List<String> ACCEPTED_MANIFEST_FORMATS =
            Arrays.asList(V2_MANIFEST_FORMAT, MANIFEST_LIST_FORMAT, OCI_MANIFEST_FORMAT, OCI_INDEX_FORMAT);
    private static final String DEFAULT_OS = "linux";
    private static final String DEFAULT_ARCHITECTURE = "amd64";
    /**
     * A platform of the manifests, that do not describe an image, e.g. build attestations
     */
    private static final String UNKNOWN_PLATFORM = "unknown";
    private static final int MANIFEST_RESOLUTION_DEPTH = 3;
    /**
     * A manifest list, that does not reference any image manifests, therefore may be pushed to a registry
     * without uploading any blobs. It is used to get rid of dangling tags, see {@link #untagImage}.
     */
    private static final String EMPTY_MANIFEST_LIST = "{\"schemaVersion\":2,\"mediaType\":\""
            + MANIFEST_LIST_FORMAT + "\",\"manifests\":[]}";
    private static final String DOCKER_CONTENT_DIGEST_HEADER = "docker-content-digest";
    private static final String SHA_256_PREFIX = "sha256:";
    // in ms
    private static final int REQUEST_TIMEOUT = 30 * 1000;

    private String hostName;
    private String userName;
    private String password;
    private String caCert;
    private String token;
    private RestTemplate restTemplate;

    public DockerClient(DockerRegistry registry, ObjectMapper mapper, String token) {
        this.hostName = registry.getPath();
        this.userName = registry.getUserName();
        this.password = registry.getPassword();
        this.caCert = registry.getCaCert();
        this.token = token;
        initRestTemplate(mapper);
    }

    public DockerClient(DockerRegistry registry, ObjectMapper mapper) {
        this(registry, mapper, null);
    }

    public DockerClient(String hostName, ObjectMapper mapper) {
        this.hostName = hostName;
        initRestTemplate(mapper);
    }

    public void checkAvailability() {
        HttpEntity entity = getAuthHeaders();
        String uri = String.format(HEALTH_ENTRY_POINT, hostName);
        try {
            getRestTemplate().exchange(uri, HttpMethod.GET, entity, String.class);
        } catch (ResourceAccessException e) {
            if (e.getCause() instanceof SSLHandshakeException) {
                throw new DockerCertificateException(hostName, e.getCause());
            } else {
                throw new DockerConnectionException(hostName, e.getMessage(), e);
            }
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new DockerCredentialsException(hostName, userName, password, e);
            } else {
                throw new DockerConnectionException(hostName, e.getMessage(), e);
            }
        }
    }

    public Set<String> getRegistryEntries() {
        try {
            URI uri = new URI(String.format(LIST_REGISTRY_URL, hostName));
            HttpEntity entity = getAuthHeaders();
            ResponseEntity<RegistryListing> response = getRestTemplate().exchange(uri, HttpMethod.GET, entity,
                new ParameterizedTypeReference<>() {});
            if (response.getStatusCode() == HttpStatus.OK) {
                return response.getBody().getRepositories();
            } else {
                throw new UnexpectedResponseStatusException(response.getStatusCode());
            }
        } catch (URISyntaxException | UnexpectedResponseStatusException e) {
            LOGGER.error(e.getMessage(), e);
            return Collections.emptySet();
        }
    }

    public List<String> getImageTags(String registryPath, String image) {
        String url = String.format(TAGS_LIST, registryPath, image);
        try {
            URI uri = new URI(url);
            HttpEntity entity = getAuthHeaders();
            ResponseEntity<TagsListing>
                    response = getRestTemplate().exchange(uri, HttpMethod.GET, entity,
                        new ParameterizedTypeReference<>() {});
            if (response.getStatusCode() == HttpStatus.OK) {
                return response.getBody().getTags();
            } else {
                throw new UnexpectedResponseStatusException(response.getStatusCode());
            }
        } catch (URISyntaxException | HttpClientErrorException | UnexpectedResponseStatusException e) {
            LOGGER.error(e.getMessage(), e);
            throw new DockerConnectionException(url, e.getMessage());
        }
    }

    /**
     * Lists tags of a specified image. Unlike {@link #getImageTags(String, String)} an empty list is returned
     * if an image is not found in a registry instead of failing.
     * @param registryPath a registry to list tags from
     * @param image an image (repository) name
     */
    public List<String> findImageTags(final String registryPath, final String image) {
        final String url = String.format(TAGS_LIST, registryPath, image);
        try {
            final URI uri = new URI(url);
            final ResponseEntity<TagsListing> response = getRestTemplate().exchange(uri, HttpMethod.GET,
                    getAuthHeaders(), new ParameterizedTypeReference<TagsListing>() {});
            if (response.getStatusCode() != HttpStatus.OK) {
                throw new UnexpectedResponseStatusException(response.getStatusCode());
            }
            return Optional.ofNullable(response.getBody())
                    .map(TagsListing::getTags)
                    .orElseGet(Collections::emptyList);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                LOGGER.debug("Image {} is not found in registry {}", image, registryPath);
                return Collections.emptyList();
            }
            LOGGER.error(e.getMessage(), e);
            throw new DockerConnectionException(url, e.getMessage());
        } catch (URISyntaxException | UnexpectedResponseStatusException | RestClientException e) {
            LOGGER.error(e.getMessage(), e);
            throw new DockerConnectionException(url, e.getMessage());
        }
    }

    public ImageDescription getImageDescription(final DockerRegistry registry,
                                                final String imageName, final String tag) {
        final RawImageDescriptionV2 rawImage = getRawImageDescription(registry, imageName, tag);
        rawImage.setRegistry(registry.getId());
        return rawImage.getImageDescription();
    }

    /**
     * Returns a history of commands called during an image build, retrieved from image manifest
     * @param registry a registry to search version in
     * @param imageName an image name
     * @param tag an image tag
     */
    public List<ImageHistoryLayer> getImageHistory(final DockerRegistry registry, final String imageName,
                                                   final String tag) {
        final ManifestV2 manifestV2 = getManifestV2(registry, imageName, tag);
        final RawImageDescriptionV2 rawImage = getRawImageDescription(manifestV2, registry, imageName);
        final List<HistoryEntryV2> history = rawImage.getHistory();
        final Map<String, Long> layersSize = getLayersSize(manifestV2);
        final List<String> layersDigest = getLayersDigestDirectCreationOrder(manifestV2);
        int i = 0;
        final List<ImageHistoryLayer> result = new ArrayList<>();
        for (HistoryEntryV2 h: history) {
            final ImageHistoryLayer layer = new ImageHistoryLayer();
            layer.setCommand(DockerParsingUtils.getBuildHistory(h));
            if (h.isEmptyLayer()) {
                layer.setSize(0L);
            } else {
                layer.setSize(layersSize.getOrDefault(layersDigest.get(i), 0L));
                i++;
            }
            result.add(layer);
        }
        return result;
    }

    public Map<String, String> getImageLabels(final DockerRegistry registry, final String imageName, final String tag) {
        final RawImageDescriptionV2 rawImage = getRawImageDescription(registry, imageName, tag);
        return DockerParsingUtils.getLabels(rawImage);
    }

    private ManifestV2 getManifestV2(final DockerRegistry registry, final String imageName, final String tag) {
        return resolveImageManifest(registry, imageName, tag)
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("Cannot get manifest for image %s/%s", imageName, tag)));
    }

    private Map<String, Long> getLayersSize(final ManifestV2 manifestV2) {
        return ListUtils.emptyIfNull(manifestV2.getLayers()).stream()
            .collect(Collectors.toMap(ManifestV2.Config::getDigest, ManifestV2.Config::getSize, (s1, s2) -> s1));
    }

    private List<String> getLayersDigestDirectCreationOrder(final ManifestV2 manifestV2) {
        return ListUtils.emptyIfNull(manifestV2.getLayers()).stream()
            .map(ManifestV2.Config::getDigest)
            .collect(Collectors.toList());
    }

    /**
     * Deletes a version of specified image from specified Docker registry
     * @param registry a registry to delete version from
     * @param image an image (repository) name
     * @param digest a version (layer) digest
     */
    public void deleteLayer(DockerRegistry registry, String image, String digest) {
        String url;
        try {
            url = String.format(BLOBS_URL, registry.getPath(), URLEncoder.encode(image, "UTF-8"), digest);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException(e);
        }
        executeDeletion(url, image);
    }

    /**
     * @return {@code true} if a requested entity has been deleted, {@code false} if it is not found in a registry
     */
    private boolean executeDeletion(final String url, final String image) {
        try {
            URI uri = new URI(url);
            HttpStatusCode status = getRestTemplate().execute(uri, HttpMethod.DELETE,
                request -> request.getHeaders().putAll(getAuthHeaders().getHeaders()),
                ClientHttpResponse::getStatusCode);

            if (status != HttpStatus.ACCEPTED) {
                throw new UnexpectedResponseStatusException(status);
            }
            return true;
        } catch (URISyntaxException | UnexpectedResponseStatusException e) {
            throw new DockerConnectionException(url, e.getMessage());
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
                LOGGER.warn("Nothing to delete for image {}: {} is not found", image, url);
                return false;
            }
            throw new DockerConnectionException(url, e.getMessage());
        } catch (RestClientException e) {
            // a registry failure or an unreachable registry shall be reported the same way as any other registry
            // communication error: the callers rely on DockerConnectionException to detect a failed deletion
            LOGGER.error(e.getMessage(), e);
            throw new DockerConnectionException(url, e.getMessage());
        }
    }

    /**
     * Deletes an image from Docker registry
     *
     * Note that a manifest, that cannot be resolved by the given tag, cannot be deleted, since a registry
     * provides no way to delete a manifest by a tag. Such a tag may still be listed by a registry,
     * see {@link #untagImage} for details.
     *
     * A manifest, the given tag points to, is deleted as is, without resolving a manifest list to a platform
     * specific image manifest: it is the manifest of a tag, that shall be deleted for a registry to remove
     * the tag itself. The deletion of a referenced image manifest instead leaves a tag pointing
     * to a broken manifest list behind.
     *
     * @param registry registry, where image is located
     * @param image image to delete
     * @param tag a tag of an image to delete
     * @return Manifest of a deleted image or an empty value if a manifest cannot be resolved by the given tag
     */
    public Optional<ManifestV2> deleteImage(DockerRegistry registry, String image, String tag) {
        final Optional<ManifestV2> manifestOpt = getManifest(registry, image, tag);
        if (!manifestOpt.isPresent()) {
            LOGGER.warn("Manifest of image {}:{} cannot be resolved in registry {}, "
                    + "no manifest deletion request will be sent", image, tag, registry.getPath());
            return Optional.empty();
        }
        final String digest = manifestOpt.get().getDigest();
        if (!deleteManifest(registry, image, digest)) {
            LOGGER.warn("Manifest {} of image {}:{} is not found in registry {} and cannot be deleted",
                    digest, image, tag, registry.getPath());
        }
        return manifestOpt;
    }

    /**
     * Removes a tag of the given image from a registry even if a manifest, the tag points to, cannot be resolved.
     *
     * There is no way to delete a tag itself using Docker Registry API: a tag is removed only as a side effect
     * of the deletion of a manifest it points to. Therefore if a manifest is already deleted, while a tag
     * pointing to it is left behind (e.g. a registry has failed to untag a manifest after its deletion),
     * such a dangling tag cannot be removed in a regular way at all.
     *
     * To get rid of such a tag it is first repointed to an empty manifest list, that does not reference any
     * blobs and thus may be pushed as is, and then the pushed manifest is deleted, which makes a registry
     * remove the tag as well. The operation is idempotent: if it fails in between, the tag will point to
     * the empty manifest list and may be deleted in a regular way.
     *
     * @param registry registry, where image is located
     * @param image an image (repository) name
     * @param tag a tag to remove
     * @return {@code true} if a temporary manifest has been deleted from a registry
     */
    public boolean untagImage(final DockerRegistry registry, final String image, final String tag) {
        LOGGER.debug("Removing tag {} of image {} from registry {}", tag, image, registry.getPath());
        final String digest = putEmptyManifestList(registry, image, tag);
        return deleteManifest(registry, image, digest);
    }

    private boolean deleteManifest(final DockerRegistry registry, final String image, final String digest) {
        return executeDeletion(String.format(IMAGE_DESCRIPTION_URL, registry.getPath(), image, digest), image);
    }

    private String putEmptyManifestList(final DockerRegistry registry, final String image, final String tag) {
        final String url = String.format(IMAGE_DESCRIPTION_URL, registry.getPath(), image, tag);
        final byte[] manifest = EMPTY_MANIFEST_LIST.getBytes(StandardCharsets.UTF_8);
        try {
            final URI uri = new URI(url);
            final HttpHeaders headers = getHttpHeaders();
            headers.setContentType(MediaType.parseMediaType(MANIFEST_LIST_FORMAT));
            final ResponseEntity<String> response = getRestTemplate()
                    .exchange(uri, HttpMethod.PUT, new HttpEntity<>(manifest, headers), String.class);
            if (response.getStatusCode() != HttpStatus.CREATED && response.getStatusCode() != HttpStatus.OK) {
                throw new UnexpectedResponseStatusException(response.getStatusCode());
            }
            return Optional.ofNullable(response.getHeaders().getFirst(DOCKER_CONTENT_DIGEST_HEADER))
                    .filter(StringUtils::isNotBlank)
                    .orElseGet(() -> SHA_256_PREFIX + DigestUtils.sha256Hex(manifest));
        } catch (URISyntaxException | UnexpectedResponseStatusException | RestClientException e) {
            LOGGER.error(e.getMessage(), e);
            throw new DockerConnectionException(url, e.getMessage());
        }
    }

    private RawImageDescriptionV2 getRawImageDescription(final DockerRegistry registry, final String imageName,
                                                         final String tag) {
        final ManifestV2 manifestV2 = getManifestV2(registry, imageName, tag);
        return getRawImageDescription(manifestV2, registry, imageName);
    }

    private RawImageDescriptionV2 getRawImageDescription(final ManifestV2 manifestV2, final DockerRegistry registry,
                                                         final String imageName) {
        String url;
        try {
            url = String.format(BLOBS_URL, registry.getPath(), URLEncoder.encode(imageName, "UTF-8"),
                    manifestV2.getConfig().getDigest());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException(e);
        }
        try {
            final URI uri = new URI(url);
            final ResponseEntity<byte[]> response = getRestTemplate().exchange(
                    uri,
                    HttpMethod.GET,
                    new HttpEntity<>(getHttpHeaders()),
                    byte[].class
            );
            if (response.getStatusCode() != HttpStatus.OK) {
                throw new DockerConnectionException(url, "Unexpected response status: " + response.getStatusCode());
            }
            return new ObjectMapper().readValue(response.getBody(), RawImageDescriptionV2.class);
        } catch (IOException | URISyntaxException e) {
            LOGGER.error(e.getMessage(), e);
            throw new DockerConnectionException(url, e.getMessage());
        }
    }

    /**
     * Gets a V2 Manifest for a specified image and tag
     * @param registry a registry, where image is located
     * @param imageName a name of an image (repository)
     * @param tag tag name
     * @return image's manifest or an empty value if a manifest is not found in a registry
     */
    public Optional<ManifestV2> getManifest(DockerRegistry registry, String imageName, String tag) {
        String url = String.format(IMAGE_DESCRIPTION_URL, registry.getPath(), imageName, tag);
        try {
            URI uri = new URI(url);
            ResponseEntity<ManifestV2>
                response = getRestTemplate().exchange(uri, HttpMethod.GET, getV2AuthHeaders(),
                                                      new ParameterizedTypeReference<ManifestV2>() {});
            if (response.getStatusCode() == HttpStatus.OK) {
                final ManifestV2 manifest = Optional.ofNullable(response.getBody())
                        .orElseThrow(() -> new DockerConnectionException(url, "Manifest response has no body"));
                manifest.setDigest(getManifestDigest(response, url));
                return Optional.of(manifest);
            } else {
                throw new UnexpectedResponseStatusException(response.getStatusCode());
            }
        } catch (HttpClientErrorException e) {
            // only a missing manifest is reported as an empty value, any other client error (an expired token,
            // insufficient permissions, etc.) shall not be mistaken for a nonexistent image version
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                LOGGER.debug("Manifest is not found at {}: {}", url, e.getMessage());
                return Optional.empty();
            }
            LOGGER.error(e.getMessage(), e);
            throw new DockerConnectionException(url, e.getMessage());
        } catch (URISyntaxException | UnexpectedResponseStatusException | RestClientException e) {
            LOGGER.error(e.getMessage(), e);
            throw new DockerConnectionException(url, e.getMessage());
        }
    }

    /**
     * Extracts a digest of a manifest from a registry response. A digest identifies a manifest in a registry
     * and is the only way to address it, e.g. to delete it, therefore a response without the digest header
     * cannot be processed and shall be reported as an error rather than silently accepted.
     */
    private String getManifestDigest(final ResponseEntity<ManifestV2> response, final String url) {
        return Optional.ofNullable(response.getHeaders().getFirst(DOCKER_CONTENT_DIGEST_HEADER))
                .filter(StringUtils::isNotBlank)
                .orElseThrow(() -> new DockerConnectionException(url,
                        String.format("Manifest response has no %s header", DOCKER_CONTENT_DIGEST_HEADER)));
    }

    /**
     * Gets an image manifest for a specified image and tag.
     *
     * If a tag points to a manifest list (an OCI index), a platform specific image manifest is resolved,
     * since only an image manifest describes a configuration and layers of an image. A digest of the manifest,
     * the tag points to, is kept though, because it is the digest, that identifies an image version
     * in a registry, e.g. it is the one, that shall be used to delete a version.
     *
     * @param registry a registry, where image is located
     * @param imageName a name of an image (repository)
     * @param tag tag name
     * @return an image manifest or an empty value if a manifest is not found in a registry
     */
    public Optional<ManifestV2> resolveImageManifest(final DockerRegistry registry, final String imageName,
                                                     final String tag) {
        return getManifest(registry, imageName, tag)
                .map(manifest -> resolveManifestList(registry, imageName, manifest));
    }

    private ManifestV2 resolveManifestList(final DockerRegistry registry, final String imageName,
                                           final ManifestV2 manifest) {
        ManifestV2 resolved = manifest;
        for (int depth = 0; depth < MANIFEST_RESOLUTION_DEPTH && isManifestList(resolved); depth++) {
            final String digest = findImageManifestDigest(registry, imageName, resolved);
            LOGGER.debug("Resolving manifest list {} of image {} to image manifest {}",
                    resolved.getDigest(), imageName, digest);
            resolved = getManifest(registry, imageName, digest)
                    .orElseThrow(() -> new DockerConnectionException(registry.getPath(), String.format(
                            "Image manifest %s of image %s is not found", digest, imageName)));
        }
        validateImageManifest(registry, imageName, manifest, resolved);
        resolved.setDigest(manifest.getDigest());
        return resolved;
    }

    /**
     * Fails if a manifest does not describe an image: it is either a manifest list, that has not been resolved
     * within the allowed depth, or a manifest without an image configuration, e.g. the empty manifest list,
     * {@link #untagImage} temporary pushes, or a legacy schema 1 manifest. Such a manifest must never be returned
     * as an image manifest, since none of the image attributes may be read from it.
     */
    private void validateImageManifest(final DockerRegistry registry, final String imageName,
                                       final ManifestV2 manifest, final ManifestV2 resolved) {
        if (isManifestList(resolved) || Objects.isNull(resolved.getConfig())) {
            throw new DockerConnectionException(registry.getPath(), String.format(
                    "Image manifest of image %s cannot be resolved from manifest %s",
                    imageName, manifest.getDigest()));
        }
    }

    /**
     * Detects a manifest list (an OCI index) rather than an image manifest. Note that a manifest list may
     * reference no image manifests at all, hence the media type is checked as well: such an empty list
     * still describes no image and shall not be mistaken for an image manifest.
     */
    private boolean isManifestList(final ManifestV2 manifest) {
        return CollectionUtils.isNotEmpty(manifest.getManifests())
                || MANIFEST_LIST_FORMAT.equalsIgnoreCase(manifest.getMediaType())
                || OCI_INDEX_FORMAT.equalsIgnoreCase(manifest.getMediaType());
    }

    /**
     * Selects an image manifest, that describes an image the best: a manifest of the default platform is preferred,
     * otherwise the first manifest of a known platform is used.
     */
    private String findImageManifestDigest(final DockerRegistry registry, final String imageName,
                                          final ManifestV2 manifestList) {
        final List<ManifestV2.ManifestReference> references = ListUtils.emptyIfNull(manifestList.getManifests());
        if (references.isEmpty()) {
            throw new DockerConnectionException(registry.getPath(), String.format(
                    "Manifest list %s of image %s references no image manifests",
                    manifestList.getDigest(), imageName));
        }
        return references.stream()
                .filter(this::isDefaultPlatform)
                .findFirst()
                .orElseGet(() -> references.stream()
                        .filter(this::isKnownPlatform)
                        .findFirst()
                        .orElse(references.get(0)))
                .getDigest();
    }

    private boolean isDefaultPlatform(final ManifestV2.ManifestReference reference) {
        return Optional.ofNullable(reference.getPlatform())
                .filter(platform -> DEFAULT_OS.equalsIgnoreCase(platform.getOs())
                        && DEFAULT_ARCHITECTURE.equalsIgnoreCase(platform.getArchitecture()))
                .isPresent();
    }

    private boolean isKnownPlatform(final ManifestV2.ManifestReference reference) {
        return Optional.ofNullable(reference.getPlatform())
                .filter(platform -> !UNKNOWN_PLATFORM.equalsIgnoreCase(platform.getOs())
                        && !UNKNOWN_PLATFORM.equalsIgnoreCase(platform.getArchitecture()))
                .isPresent();
    }

    public ToolVersion getVersionAttributes(final DockerRegistry registry, final String imageName,
                                            final String tag) {
        final ToolVersion attributes = new ToolVersion();
        attributes.setVersion(tag);
        final ManifestV2 manifestV2 = getManifestV2(registry, imageName, tag);
        attributes.setDigest(manifestV2.getDigest());
        attributes.setSize(ListUtils.emptyIfNull(manifestV2.getLayers())
                .stream()
                .mapToLong(ManifestV2.Config::getSize)
                .sum());
        final RawImageDescriptionV2 rawImage = getRawImageDescription(manifestV2, registry, imageName);
        final String platform = DockerParsingUtils.getPlatform(rawImage).orElse("linux");
        attributes.setPlatform(platform);
        attributes.setModificationDate(getLatestDate(registry, imageName, tag));
        return attributes;
    }

    private HttpEntity getAuthHeaders() {
        return new HttpEntity(getHttpHeaders());
    }

    private HttpEntity getV2AuthHeaders() {
        HttpHeaders headers = getHttpHeaders();
        ACCEPTED_MANIFEST_FORMATS.forEach(format -> headers.add(HttpHeaders.ACCEPT, format));
        return new HttpEntity(headers);
    }

    private HttpHeaders getHttpHeaders() {
        HttpHeaders headers = new HttpHeaders();
        if (StringUtils.isNotBlank(token)) {
            headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        } else if (StringUtils.isNotBlank(userName)) {
            headers.add(HttpHeaders.AUTHORIZATION, encodeCredentialsForBasicAuth(userName, password));
        }
        return headers;
    }

    private ClientHttpRequestFactory getHttpRequestFactory(String caCert) {
        try {
            X509Certificate providedCert = getCertificate(caCert);
            TrustStrategy acceptingTrustStrategy =
                (x509Certificates, s) -> Arrays.stream(x509Certificates).anyMatch(cert ->
                    cert.getSerialNumber().equals(providedCert.getSerialNumber()));

            SSLContext sslContext = SSLContexts.custom()
                    .loadTrustMaterial(null, acceptingTrustStrategy)
                    .build();
            var tlsStrategy = new DefaultClientTlsStrategy(sslContext);
            var connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                    .setTlsSocketStrategy(tlsStrategy)
                    .build();
            CloseableHttpClient httpClient = HttpClients.custom()
                    .setConnectionManager(connectionManager)
                    .setRedirectStrategy(new DefaultRedirectStrategy() {
                        @Override
                        public boolean isRedirectAllowed(
                                final HttpHost currentTarget,
                                final HttpHost newTarget,
                                final HttpRequest redirect,
                                final HttpContext context) {
                            if (currentTarget != null && newTarget != null && !currentTarget.equals(newTarget)) {
                                redirect.removeHeaders(AUTHORIZATION);
                                redirect.removeHeaders(COOKIE);
                            }
                            return super.isRedirectAllowed(currentTarget, newTarget, redirect, context);
                        }
                    })
                    .build();

            HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
            requestFactory.setHttpClient(httpClient);
            return requestFactory;
        } catch (GeneralSecurityException e) {
            throw new DockerCertificateException(hostName);
        }
    }

    private X509Certificate getCertificate(String caCert) throws CertificateException {
        byte [] decoded = Base64.decodeBase64(caCert
                .replaceAll(Constants.X509_BEGIN_CERTIFICATE, "")
                .replaceAll(Constants.X509_END_CERTIFICATE, ""));

        return (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(decoded));
    }

    private RestTemplate getRestTemplate() {
        return restTemplate;
    }

    private String encodeCredentialsForBasicAuth(String username, String password) {
        return "Basic " + Base64.encodeBase64String((username + ":" + password).getBytes());
    }

    private void initRestTemplate(ObjectMapper mapper) {
        this.restTemplate = getRestTemplate(mapper);
    }

    private RestTemplate getRestTemplate(ObjectMapper mapper) {
        RestTemplateBuilder builder = new RestTemplateBuilder()
                .additionalMessageConverters(new RestTemplate().getMessageConverters());

        if (StringUtils.isNotBlank(caCert)) {
            builder = builder.requestFactory(() -> getHttpRequestFactory(caCert));
        }
        if (mapper != null) {
            builder = builder.additionalMessageConverters(getMessageConverters(mapper));
        }

        return builder
                .setConnectTimeout(Duration.ofMillis(REQUEST_TIMEOUT))
                .build();
    }

    private HttpMessageConverter<?> getMessageConverters(ObjectMapper mapper) {
        MappingJackson2HttpMessageConverter dockerResponseConverter = new MappingJackson2HttpMessageConverter();
        dockerResponseConverter.setObjectMapper(mapper);
        dockerResponseConverter.setSupportedMediaTypes(
                    Arrays.asList(MediaType.APPLICATION_JSON, new MediaType("application", "*+json"),
                            new MediaType("application", "*+prettyjws")));
        return dockerResponseConverter;
    }

    private Date getLatestDate(final DockerRegistry registry, final String imageName, final String tag) {
        return DockerParsingUtils.getLatestDate(getRawImageDescription(registry, imageName, tag));
    }
}
