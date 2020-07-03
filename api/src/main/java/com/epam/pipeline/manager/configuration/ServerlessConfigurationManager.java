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

package com.epam.pipeline.manager.configuration;

import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.controller.vo.PagingRunFilterVO;
import com.epam.pipeline.controller.vo.ServiceUrlVO;
import com.epam.pipeline.dao.pipeline.StopServerlessRunDao;
import com.epam.pipeline.entity.configuration.AbstractRunConfigurationEntry;
import com.epam.pipeline.entity.configuration.RunConfiguration;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.StopServerlessRun;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.pipeline.runner.ConfigurationRunner;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.mapper.AbstractRunConfigurationMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.Charset;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class ServerlessConfigurationManager {

    private static final int REQUEST_TIMEOUT = 30 * 1000;

    private final RunConfigurationManager runConfigurationManager;
    private final ConfigurationRunner configurationRunner;
    private final AbstractRunConfigurationMapper runConfigurationMapper;
    private final PipelineRunManager runManager;
    private final PreferenceManager preferenceManager;
    private final StopServerlessRunDao stopServerlessRunDao;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRED)
    public String run(final Long configurationId, final String configName, final HttpServletRequest request) {
        final RunConfiguration configuration = runConfigurationManager.load(configurationId);
        final AbstractRunConfigurationEntry configurationEntry = configuration.getEntries().stream()
                .filter(config -> Objects.equals(config.getName(), configName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(String
                        .format("Cannot find configuration with name '%s'", configName)));
        final PipelineRun pipelineRun = receivePipelineRun(configurationId, configuration);

        final StopServerlessRun stopRunInfo = StopServerlessRun.builder()
                .runId(pipelineRun.getId())
                .lastUpdate(LocalDateTime.now())
                .build();
        if (configurationEntry.isStopAfter()) {
            stopServerlessRunDao.createServerlessRun(stopRunInfo);
        }

        waitForRunInitialized(pipelineRun);
        final String endPointName = getEndpointUrl(configuration, configurationEntry, pipelineRun);

        final String appPath = buildApplicationUrl(request, endPointName);
        log.debug("The request '{}' will be sent", appPath);

        final String response = sendRequest(request, appPath);

        if (configurationEntry.isStopAfter()) {
            stopRunInfo.setLastUpdate(LocalDateTime.now());
            stopServerlessRunDao.updateServerlessRun(stopRunInfo);
        }

        return response;
    }

    public String generateUrl(final Long configurationId, String configName) {
        configName = getConfigurationName(configurationId, configName);
        final String apiHost = preferenceManager.getPreference(SystemPreferences.BASE_API_HOST_EXTERNAL);
        Assert.state(StringUtils.isNotBlank(apiHost), "API host is not specified");
        return String.format("%s/serverless/%d/%s", StringUtils.stripEnd(apiHost, "/"),
                configurationId, configName);
    }

    private String getConfigurationName(final Long configurationId, final String configName) {
        return runConfigurationManager.load(configurationId).getEntries().stream()
                .filter(entry -> StringUtils.isNotBlank(configName)
                        ? Objects.equals(entry.getName(), configName)
                        : entry.isDefaultConfiguration())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Failed to determine config name"))
                .getName();
    }

    private void waitForRunInitialized(final PipelineRun pipelineRun) {
        final Integer maxRetryCount = preferenceManager.getPreference(SystemPreferences.LAUNCH_SERVERLESS_WAIT_COUNT);
        final Integer waitTime = preferenceManager.getPreference(SystemPreferences.LAUNCH_TASK_STATUS_UPDATE_RATE);

        for (int i = 0; i < maxRetryCount; i++) {
            if (StringUtils.isNotBlank(runManager.loadPipelineRun(pipelineRun.getId()).getServiceUrl())) {
                return;
            }
            try {
                Thread.sleep(waitTime);
            } catch (InterruptedException e) {
                throw new IllegalArgumentException(e);
            }
        }
        throw new IllegalArgumentException("Exceeded maximum waiting time for launching configuration");
    }

    private PipelineRun receivePipelineRun(final Long configurationId, final RunConfiguration configuration) {
        final PagingRunFilterVO filter = new PagingRunFilterVO();
        filter.setConfigurationIds(Collections.singletonList(configurationId));
        filter.setStatuses(Collections.singletonList(TaskStatus.RUNNING));
        List<PipelineRun> activeRunsForConfiguration = ListUtils.emptyIfNull(runManager
                .searchPipelineRuns(filter, false)
                .getElements());
        if (CollectionUtils.isEmpty(activeRunsForConfiguration)) {
            activeRunsForConfiguration = configurationRunner.runConfiguration(null,
                    runConfigurationMapper.toRunConfigurationWithEntitiesVO(configuration), null);
        }
        return activeRunsForConfiguration.stream()
                .max(Comparator.comparing(PipelineRun::getStartDate))
                .orElseThrow(() -> new IllegalArgumentException("Failed to find pipeline run for configuration"));
    }

    private String getEndpointUrl(final RunConfiguration configuration,
                                  final AbstractRunConfigurationEntry configurationEntry,
                                  final PipelineRun pipelineRun) {
        final String endpointName = configurationEntry.getEndpointName();
        final List<ServiceUrlVO> serviceUrls = ListUtils.emptyIfNull(JsonMapper.parseData(pipelineRun.getServiceUrl(),
                new TypeReference<List<ServiceUrlVO>>() {}, objectMapper));

        if (StringUtils.isNotBlank(endpointName)) {
            return serviceUrls.stream()
                    .filter(serviceUrl -> Objects.equals(serviceUrl.getName(), endpointName))
                    .findAny()
                    .orElseThrow(() -> new IllegalArgumentException(String
                            .format("Failed to find endpoint url for endpoint name '%s'", endpointName)))
                    .getUrl();
        }
        Assert.state(serviceUrls.size() == 1,
                "Only one service url is allowed when endpoint name is not specified");
        return serviceUrls.get(0).getUrl();
    }

    String sendRequest(final HttpServletRequest request, final String appPath) {
        try {
            final HttpEntity<String> requestEntity = buildHttpEntity(request);
            final ResponseEntity<String> resp = buildRestTemplate()
                    .exchange(appPath, HttpMethod.valueOf(request.getMethod()), requestEntity, String.class);
            return resp.getBody();
        } catch (IOException | NoSuchAlgorithmException | KeyStoreException | KeyManagementException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private String buildApplicationUrl(final HttpServletRequest request, final String endpointName) {
        final String appPath = String.format("%s/%s",
                StringUtils.stripEnd(endpointName, "/"), getApplicationPath(request));
        return StringUtils.isNotBlank(request.getQueryString())
                ? String.format("%s?%s", appPath, request.getQueryString())
                : appPath;
    }

    private String getApplicationPath(final HttpServletRequest request) {
        final Pattern pattern = Pattern.compile("/serverless/.*?/.*?/([^']*)");
        final Matcher matcher = pattern.matcher(request.getPathInfo());
        return matcher.matches() ? matcher.group(1) : "";
    }

    private HttpHeaders buildHttpHeaders(final HttpServletRequest request) {
        final HttpHeaders headers = new HttpHeaders();
        Collections.list(request.getHeaderNames())
                .forEach(headerName -> headers.add(headerName, request.getHeader(headerName)));
        return headers;
    }

    private RestTemplate buildRestTemplate() throws KeyStoreException, NoSuchAlgorithmException,
            KeyManagementException {
        final RestTemplateBuilder builder = new RestTemplateBuilder()
                .additionalMessageConverters(new RestTemplate().getMessageConverters());
        final TrustStrategy acceptingTrustStrategy = (X509Certificate[] chain, String authType) -> true;
        final SSLContext sslContext = org.apache.http.ssl.SSLContexts.custom()
                .loadTrustMaterial(null, acceptingTrustStrategy)
                .build();
        final SSLConnectionSocketFactory csf = new SSLConnectionSocketFactory(sslContext);
        final CloseableHttpClient httpClient = HttpClients.custom()
                .setSSLSocketFactory(csf)
                .build();
        final HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
        requestFactory.setHttpClient(httpClient);
        return builder
                .requestFactory(requestFactory)
                .setConnectTimeout(REQUEST_TIMEOUT)
                .build();
    }

    private HttpEntity<String> buildHttpEntity(final HttpServletRequest request) throws IOException {
        final String body = IOUtils.toString(request.getInputStream(),
                Charset.forName(request.getCharacterEncoding()));
        return new HttpEntity<>(body, buildHttpHeaders(request));
    }
}
