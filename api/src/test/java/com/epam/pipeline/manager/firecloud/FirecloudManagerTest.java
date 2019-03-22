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

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.firecloud.FirecloudInputsOutputs;
import com.epam.pipeline.entity.firecloud.FirecloudMethod;
import com.epam.pipeline.entity.firecloud.FirecloudMethodConfiguration;
import com.epam.pipeline.entity.firecloud.FirecloudMethodWDL;
import com.epam.pipeline.entity.firecloud.FirecloudRawMethod;
import com.epam.pipeline.manager.google.GoogleCredentialsManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.google.auth.oauth2.AccessToken;
import okhttp3.Request;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

public class FirecloudManagerTest {

    private static final String REFRESH_TOKEN = "TOKEN_VALUE";
    private static final String ACCESS_TOKEN = "Bearer " + REFRESH_TOKEN;

    @Mock
    private FirecloudClient firecloudClient;

    @Mock
    private GoogleCredentialsManager credentialsManager;

    @Mock
    private PreferenceManager preferenceManager;

    @Mock
    private MessageHelper messageHelper;

    @InjectMocks
    private FirecloudManager firecloudManager =
            new FirecloudManager(preferenceManager, credentialsManager, messageHelper);

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(firecloudClient.getMethods(Mockito.eq(ACCESS_TOKEN)))
                .thenReturn(new MockCall<>(getTestList()));

        when(firecloudClient.getMethod(Mockito.eq(ACCESS_TOKEN),
                Mockito.anyString(), Mockito.anyString(), Mockito.anyLong()))
                .thenReturn(new MockCall<>(getExpectedMethodDescription()));

        when(firecloudClient.getConfigurations(Mockito.eq(ACCESS_TOKEN),
                Mockito.anyString(), Mockito.anyString()))
                .thenReturn(new MockCall<>(getExpectedConfigurations()));

        when(firecloudClient.getInputsOutputs(Mockito.eq(ACCESS_TOKEN), Mockito.any()))
                .thenReturn(new MockCall<>(getExpectedInputsOutputs()));


        when(credentialsManager.getDefaultToken())
                .thenReturn(new AccessToken(REFRESH_TOKEN, null));
    }

    @Test
    public void checkResultOfGetMethods() {
        final List<FirecloudMethod> expected = getExpectedList();
        List<FirecloudMethod> actual = firecloudManager.getMethods(null);
        assertEquals(expected, actual);
    }

    @Test
    public void checkResultOfGetMethod() {
        final FirecloudMethodWDL expected = getExpectedMethodDescription();
        final String workspace = "namespace";
        final String method = "method";
        long snapshot = 1;

        FirecloudMethodWDL actual = firecloudManager.getMethod(null, workspace, method, snapshot);
        assertEquals(expected, actual);
    }

    @Test
    public void checkResultOfGetInputsOutputs() {
        final FirecloudInputsOutputs expected = getExpectedInputsOutputs();
        final String workspace = "workspace";
        final String method = "methodName";
        long snapshot = 2;

        FirecloudInputsOutputs actual = firecloudManager.getInputsOutputs(null, workspace, method, snapshot);
        assertEquals(expected, actual);
    }


    @Test
    public void checkResultOfGetConfigurations() {
        final List<FirecloudMethodConfiguration> expected = getExpectedConfigurations();
        final String workspace = "namespace";
        final String method = "method";
        long snapshot = 1;

        List<FirecloudMethodConfiguration> actual = firecloudManager
                .getConfigurations(null, workspace, method, snapshot);
        assertEquals(expected, actual);
    }

    private List<FirecloudMethodConfiguration> getExpectedConfigurations() {
        final String workspace = "namespace";
        final String method = "method";
        long snapshot = 1;
        final String entityType = "Configuration";
        FirecloudMethodConfiguration.MethodConfigurationObject object =
                new FirecloudMethodConfiguration.MethodConfigurationObject();
        FirecloudMethodConfiguration.MethodRepositoryMethod repositoryMethod =
                new FirecloudMethodConfiguration.MethodRepositoryMethod();
        repositoryMethod.setMethodName(method);
        repositoryMethod.setMethodNamespace(workspace);
        repositoryMethod.setMethodVersion(snapshot);

        object.setMethodRepoMethod(repositoryMethod);

        FirecloudMethodConfiguration configuration =
                new FirecloudMethodConfiguration(workspace, method, Long.toString(snapshot), entityType, object);
        return Collections.singletonList(configuration);
    }

    private FirecloudMethodWDL getExpectedMethodDescription() {
        return new FirecloudMethodWDL("some wdl");
    }

    private static List<FirecloudRawMethod> getTestList() {
        final FirecloudRawMethod method1namespace1snapshot1 = new FirecloudRawMethod(
                "method1",
                "namespace1",
                new Date().toString(),
                "some url",
                "some synopsis",
                "entity type",
                "1"
        );
        String method2 = "method2";

        FirecloudRawMethod method1namespace1snapshot2 = method1namespace1snapshot1.clone();
        method1namespace1snapshot2.setSnapshotId("2");

        FirecloudRawMethod method2namespace1snapshot1 = method1namespace1snapshot1.clone();
        method2namespace1snapshot1.setName(method2);

        FirecloudRawMethod method3namespace1snapshot1 = method1namespace1snapshot1.clone();
        method3namespace1snapshot1.setName("method3");

        FirecloudRawMethod method1namespace2snapshot1 = method1namespace1snapshot1.clone();
        method1namespace2snapshot1.setNamespace("namespace2");

        FirecloudRawMethod method2namespace2snapshot1 = method1namespace2snapshot1.clone();
        method2namespace2snapshot1.setName(method2);

        return Arrays.asList(
                method1namespace1snapshot1,
                method2namespace1snapshot1,
                method3namespace1snapshot1,
                method1namespace1snapshot2,
                method1namespace2snapshot1,
                method2namespace2snapshot1
        );
    }

    private static List<FirecloudMethod> getExpectedList() {
        final FirecloudMethod method1namespace1snapshot1 = new FirecloudMethod(
                "method1",
                "namespace1",
                new Date().toString(),
                "some url",
                "some synopsis",
                "entity type",
                Collections.singletonList("1")
        );
        String method2 = "method2";

        FirecloudMethod method1namespace2snapshots1 = method1namespace1snapshot1.clone();
        method1namespace2snapshots1.setNamespace("namespace2");

        FirecloudMethod method2namespace2snapshots1 = method1namespace2snapshots1.clone();
        method2namespace2snapshots1.setName(method2);

        FirecloudMethod method1namespace1snapshots12 = method1namespace1snapshot1.clone();
        method1namespace1snapshots12.setSnapshotIds(Arrays.asList("1", "2"));

        FirecloudMethod method2namespace1snapshots1 = method1namespace1snapshot1.clone();
        method2namespace1snapshots1.setName(method2);

        FirecloudMethod method3namespace1snapshots1 = method1namespace1snapshot1.clone();
        method3namespace1snapshots1.setName("method3");

        return Arrays.asList(
                method1namespace2snapshots1,
                method2namespace2snapshots1,
                method1namespace1snapshots12,
                method2namespace1snapshots1,
                method3namespace1snapshots1
        );
    }

    private FirecloudInputsOutputs getExpectedInputsOutputs() {
        FirecloudInputsOutputs.FirecloudInput input1 = new FirecloudInputsOutputs
                .FirecloudInput("input1", "someInputType", true);
        FirecloudInputsOutputs.FirecloudInput input2 = new FirecloudInputsOutputs.
                FirecloudInput("input2", "someInputType", false);
        FirecloudInputsOutputs.FirecloudOutput output1 = new FirecloudInputsOutputs.
                FirecloudOutput("output1", "someOutputType");
        FirecloudInputsOutputs.FirecloudOutput output2 = new FirecloudInputsOutputs.
                FirecloudOutput("output2", "someOutputType");

        List<FirecloudInputsOutputs.FirecloudInput> inputs = Arrays.asList(input1, input2);
        List<FirecloudInputsOutputs.FirecloudOutput> outputs = Arrays.asList(output1, output2);
        return new FirecloudInputsOutputs(inputs, outputs);
    }


    private final class MockCall<T> implements Call<T> {

        private T payload;

        private MockCall(T payload) {
            this.payload = payload;
        }

        @Override
        public Response<T> execute() {
            return Response.success(payload);
        }

        @Override
        public void enqueue(Callback<T> callback) {
            // no-op
        }

        @Override
        public boolean isExecuted() {
            return true;
        }

        @Override
        public void cancel() {
            // no-op
        }

        @Override
        public boolean isCanceled() {
            return false;
        }

        @Override
        public Call<T> clone() {
            return new FirecloudManagerTest.MockCall<>(payload);
        }

        @Override
        public Request request() {
            return null;
        }
    }
}
