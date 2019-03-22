/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.firecloud;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.firecloud.FirecloudInputsOutputs;
import com.epam.pipeline.entity.firecloud.FirecloudMethod;
import com.epam.pipeline.entity.firecloud.FirecloudMethodConfiguration;
import com.epam.pipeline.entity.firecloud.FirecloudMethodParameters;
import com.epam.pipeline.entity.firecloud.FirecloudMethodWDL;
import com.epam.pipeline.entity.firecloud.FirecloudRawMethod;
import com.epam.pipeline.entity.firecloud.MethodInputsOutputsRequest;
import com.epam.pipeline.exception.FirecloudException;
import com.epam.pipeline.manager.google.CredentialsManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.AccessToken;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;

/**
 * Uses {@link FirecloudClient} to connect to Firecloud's REST API
 * initializes FirecloudClient and uses it to get responses from Firecloud and post processes it.
 */
@Service
@Slf4j
public class FirecloudManager {

    private static final MediaType CONTENT_TYPE = MediaType.parse("application/json");
    private final PreferenceManager preferenceManager;
    private CredentialsManager credentialsManager;
    private MessageHelper messageHelper;
    private FirecloudClient firecloudClient;
    private ObjectMapper objectMapper;

    public FirecloudManager(PreferenceManager preferenceManager,
                            CredentialsManager credentialsManager,
                            MessageHelper messageHelper) {
        this.preferenceManager = preferenceManager;
        this.credentialsManager = credentialsManager;
        this.messageHelper = messageHelper;
        this.objectMapper = new ObjectMapper();
    }

    @PostConstruct
    public void init() {
        OkHttpClient client = new OkHttpClient.Builder()
                .build();
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(preferenceManager.getPreference(SystemPreferences.FIRECLOUD_BASE_URL))
                .addConverterFactory(JacksonConverterFactory.create(mapper))
                .client(client)
                .build();
        this.firecloudClient = retrofit.create(FirecloudClient.class);
    }

    public List<FirecloudMethod> getMethods(String refreshToken) {
        List<FirecloudRawMethod> rawMethods = executeRequest(() -> firecloudClient.getMethods(getToken(refreshToken)));
        return convertResult(rawMethods);
    }

    public List<FirecloudMethodConfiguration> getConfigurations(
            String refreshToken, String workspace, String methodName, Long snapshot) {
        List<FirecloudMethodConfiguration> configurations =
                executeRequest(() -> firecloudClient.getConfigurations(getToken(refreshToken), workspace, methodName));
        return filterConfigurations(configurations, snapshot);
    }

    public FirecloudMethodParameters getParameters(String refreshToken, String workspace,
                                                   String methodName, Long snapshot) {
        String wdl = getMethod(refreshToken, workspace, methodName, snapshot).getPayload();
        FirecloudInputsOutputs inputsOutputs = this.getInputsOutputs(refreshToken, workspace, methodName, snapshot);
        return new FirecloudMethodParameters(inputsOutputs.getInputs(), inputsOutputs.getOutputs(), wdl);
    }

    public FirecloudMethodWDL getMethod(String refreshToken, String workspace, String methodName, Long snapshot) {
        return executeRequest(() -> firecloudClient.getMethod(getToken(refreshToken), workspace, methodName, snapshot));
    }

    public FirecloudInputsOutputs getInputsOutputs(
            String refreshToken, String workspace, String methodName, Long snapshot) {
        try {
            MethodInputsOutputsRequest request = new MethodInputsOutputsRequest(workspace, methodName, snapshot);
            RequestBody body = RequestBody.create(CONTENT_TYPE, objectMapper.writeValueAsString(request));
            return executeRequest(() -> firecloudClient.getInputsOutputs(getToken(refreshToken), body));
        } catch (JsonProcessingException e) {
            throw new FirecloudException(e);
        }
    }

    private  <T> T executeRequest(Supplier<Call<T>> request) {
        try {
            Response<T> response = request.get().execute();
            if (response.isSuccessful()) {
                return Objects.requireNonNull(response.body());
            } else {
                throw new FirecloudException(messageHelper.getMessage(MessageConstants.ERROR_FIRECLOUD_REQUEST_FAILED,
                        response.code(), response.errorBody() == null ? "" : response.errorBody().string()));
            }
        } catch (IOException e) {
            throw new FirecloudException(e);
        }
    }

    private List<FirecloudMethodConfiguration> filterConfigurations(
            List<FirecloudMethodConfiguration> configurations, Long snapshot) {
        if (CollectionUtils.isEmpty(configurations)) {
            return configurations;
        }
        return configurations.stream()
                .filter(conf -> conf.getPayloadObject().getMethodRepoMethod().getMethodVersion().equals(snapshot))
                .collect(Collectors.toList());
    }

    private List<FirecloudMethod> convertResult(List<FirecloudRawMethod> body) {
        return body.stream()
                .collect(
                        groupingBy(
                                FirecloudRawMethod::getNamespace,
                                groupingBy(FirecloudRawMethod::getName)
                        )
                ).values()
                .stream()
                .flatMap(groupedByNameRawMethods -> groupedByNameRawMethods.values().stream())
                .map(this::createFirecloudMethod)
                .collect(Collectors.toList());
    }

    private FirecloudMethod createFirecloudMethod(List<FirecloudRawMethod> list) {
        FirecloudRawMethod firstInEntry = list.get(0);
        return new FirecloudMethod(
                firstInEntry.getName(),
                firstInEntry.getNamespace(),
                firstInEntry.getCreateDate(),
                firstInEntry.getUrl(),
                firstInEntry.getSynopsis(),
                firstInEntry.getEntityType(),
                collectSnapshotIds(list));
    }

    private List<String> collectSnapshotIds(List<FirecloudRawMethod> list) {
        return list.stream()
                .map(FirecloudRawMethod::getSnapshotId)
                .sorted(Comparator.comparing(Integer::parseInt))
                .collect(Collectors.toList());
    }

    private String getToken(String refreshToken) {
        AccessToken token = StringUtils.isNotBlank(refreshToken) ?
                credentialsManager.getAccessToken(refreshToken) : credentialsManager.getDefaultToken();
        return "Bearer " + token.getTokenValue();
    }
}
