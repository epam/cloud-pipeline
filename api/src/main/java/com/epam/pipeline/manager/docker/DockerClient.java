/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.entity.docker.RawImageDescription;
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
import org.apache.commons.lang3.StringUtils;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.xml.ws.http.HTTPException;
import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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
    private static final String LAYER_DELETE_URL = "https://%s/v2/%s/blobs/%s";

    private static final String V2_MANIFEST_FORMAT = "application/vnd.docker.distribution.manifest.v2+json";
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
        } catch (HTTPException | ResourceAccessException e) {
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
            ResponseEntity<RegistryListing>
                    response = getRestTemplate().exchange(uri, HttpMethod.GET, entity,
                    new ParameterizedTypeReference<RegistryListing>() {});
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
                    new ParameterizedTypeReference<TagsListing>() {});
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


    public ImageDescription getImageDescription(DockerRegistry registry, String imageName, String tag) {
        RawImageDescription rawImage = getRawImageDescription(registry, imageName, tag, getAuthHeaders());
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
        final RawImageDescription rawImage = getRawImageDescription(registry, imageName, tag, getAuthHeaders());
        final List<String> buildHistory = DockerParsingUtils.getBuildHistory(rawImage);
        final Map<String, Long> layersSize = getLayersSize(registry, imageName, tag);
        final List<String> layersDigest = getLayersDigestDirectCreationOrder(rawImage);
        return IntStream.range(0, buildHistory.size())
            .mapToObj(i -> {
                final ImageHistoryLayer layer = new ImageHistoryLayer();
                layer.setCommand(buildHistory.get(i));
                layer.setSize(layersSize.getOrDefault(layersDigest.get(i), 0L));
                return layer;
            }).collect(Collectors.toList());
    }

    private Map<String, Long> getLayersSize(final DockerRegistry registry, final String imageName, final String tag) {
        return getManifest(registry, imageName, tag)
            .map(ManifestV2::getLayers)
            .map(Collection::stream)
            .orElse(Stream.empty())
            .collect(Collectors.toMap(ManifestV2.Config::getDigest, ManifestV2.Config::getSize, (s1, s2) -> s1));
    }

    private List<String> getLayersDigestDirectCreationOrder(final RawImageDescription rawImage) {
        final List<String> layers = rawImage.getFsLayers().stream()
            .map(Map::values)
            .flatMap(Collection::stream)
            .collect(Collectors.toList());
        Collections.reverse(layers);
        return layers;
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
            url = String.format(LAYER_DELETE_URL, registry.getPath(), URLEncoder.encode(image, "UTF-8"), digest);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException(e);
        }

        executeDeletion(url, image);
    }

    private void executeDeletion(String url, String image) {
        try {
            URI uri = new URI(url);
            HttpStatus status = getRestTemplate().execute(uri, HttpMethod.DELETE,
                request -> request.getHeaders().putAll(getAuthHeaders().getHeaders()),
                ClientHttpResponse::getStatusCode);

            if (status != HttpStatus.ACCEPTED) {
                throw new UnexpectedResponseStatusException(status);
            }
        } catch (URISyntaxException | UnexpectedResponseStatusException e) {
            throw new DockerConnectionException(url, e.getMessage());
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
                LOGGER.error("Image not found:" + image);
                return;
            }

            throw new DockerConnectionException(url, e.getMessage());
        }
    }

    /**
     * Deletes an image from Docker registry
     * @param registry registry, where image is located
     * @param image image to delete
     * @return Manifest of a deleted image
     */
    public Optional<ManifestV2> deleteImage(DockerRegistry registry, String image, String tag) {
        Optional<ManifestV2> manifestOpt = getManifest(registry, image, tag);
        return manifestOpt.map(manifest -> {
            String url;
            try {
                url = String.format(IMAGE_DESCRIPTION_URL, registry.getPath(), URLEncoder.encode(image, "UTF-8"),
                                    manifest.getDigest());
            } catch (UnsupportedEncodingException e) {
                throw new IllegalArgumentException(e);
            }

            executeDeletion(url, image);
            return manifest;
        });
    }

    public RawImageDescription getRawImageDescription(DockerRegistry registry, String imageName, String tag) {
        return getRawImageDescription(registry, imageName, tag, getAuthHeaders());
    }

    private RawImageDescription getRawImageDescription(DockerRegistry registry, String imageName, String tag,
                                                       HttpEntity headers) {
        String url = String.format(IMAGE_DESCRIPTION_URL, registry.getPath(), imageName, tag);
        try {
            URI uri = new URI(url);
            ResponseEntity<RawImageDescription>
                response = getRestTemplate().exchange(uri, HttpMethod.GET, headers,
                                                      new ParameterizedTypeReference<RawImageDescription>() {});
            if (response.getStatusCode() == HttpStatus.OK) {
                return response.getBody();
            } else {
                throw new UnexpectedResponseStatusException(response.getStatusCode());
            }
        } catch (URISyntaxException | UnexpectedResponseStatusException e) {
            LOGGER.error(e.getMessage(), e);
            throw new DockerConnectionException(url, e.getMessage());
        }
    }

    /**
     * Gets a V2 Manifest for a specified image and tag
     * @param registry a registry, where image is located
     * @param imageName a name of an image (repository)
     * @param tag tag name
     * @return image's manifest
     */
    public Optional<ManifestV2> getManifest(DockerRegistry registry, String imageName, String tag) {
        String url = String.format(IMAGE_DESCRIPTION_URL, registry.getPath(), imageName, tag);
        try {
            URI uri = new URI(url);
            ResponseEntity<ManifestV2>
                response = getRestTemplate().exchange(uri, HttpMethod.GET, getV2AuthHeaders(),
                                                      new ParameterizedTypeReference<ManifestV2>() {});
            if (response.getStatusCode() == HttpStatus.OK) {
                List<String> digest = response.getHeaders().get("docker-content-digest");
                ManifestV2 manifest = response.getBody();
                manifest.setDigest(digest.get(0));
                return Optional.of(manifest);
            } else {
                throw new UnexpectedResponseStatusException(response.getStatusCode());
            }
        } catch (URISyntaxException | UnexpectedResponseStatusException e) {
            LOGGER.error(e.getMessage(), e);
            throw new DockerConnectionException(url, e.getMessage());
        } catch (HttpClientErrorException e) {
            LOGGER.error(e.getMessage(), e);
            return Optional.empty();
        }
    }

    public ToolVersion getVersionAttributes(final DockerRegistry registry, final String imageName,
                                            final String tag) {
        ToolVersion attributes = new ToolVersion();
        attributes.setVersion(tag);
        ManifestV2 manifestV2 = getManifest(registry, imageName, tag)
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("Cannot get manifest for image %s/%s", imageName, tag)));
        attributes.setDigest(manifestV2.getDigest());
        attributes.setSize(manifestV2.getLayers()
                .stream()
                .mapToLong(ManifestV2.Config::getSize)
                .sum());
        attributes.setModificationDate(getLatestDate(registry, imageName, tag));
        return attributes;
    }

    private HttpEntity getAuthHeaders() {
        return new HttpEntity(getHttpHeaders());
    }

    private HttpEntity getV2AuthHeaders() {
        HttpHeaders headers = getHttpHeaders();
        headers.add(HttpHeaders.ACCEPT, V2_MANIFEST_FORMAT);
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

            SSLContext sslContext = org.apache.http.ssl.SSLContexts.custom()
                    .loadTrustMaterial(null, acceptingTrustStrategy)
                    .build();
            SSLConnectionSocketFactory csf = new SSLConnectionSocketFactory(sslContext);

            CloseableHttpClient httpClient = HttpClients.custom()
                    .setSSLSocketFactory(csf)
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
        RestTemplateBuilder builder = new RestTemplateBuilder()
                .additionalMessageConverters(new RestTemplate().getMessageConverters());

        if (StringUtils.isNotBlank(caCert)) {
            builder = builder.requestFactory(getHttpRequestFactory(caCert));
        }
        if (mapper != null) {
            builder = builder.additionalMessageConverters(getMessageConverters(mapper));
        }

        this.restTemplate = builder
                .setConnectTimeout(REQUEST_TIMEOUT)
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

    private Date getLatestDate(DockerRegistry registry, String imageName, String tag) {
        return DockerParsingUtils.getLatestDate(getRawImageDescription(registry, imageName, tag, getAuthHeaders()));
    }
}
