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

package com.epam.pipeline.manager.execution;

import com.epam.pipeline.entity.cluster.container.ImagePullPolicy;
import com.epam.pipeline.entity.execution.OSSpecificLaunchCommandTemplate;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.run.container.RunContainerSpec;
import com.epam.pipeline.manager.cluster.KubernetesConstants;
import com.epam.pipeline.manager.cluster.KubernetesManager;
import com.epam.pipeline.manager.contextual.ContextualPreferenceManager;
import com.epam.pipeline.manager.preference.AbstractSystemPreference;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.security.AuthManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * Checks the pod a run is launched with, as it is sent to a fake Kubernetes API.
 */
public class PipelineExecutorImageDigestTest {

    private static final String NAMESPACE = "default";
    private static final Long RUN_ID = 1L;
    private static final String POD_ID = "pipeline-1";
    private static final String OWNER = "OWNER";
    private static final String TAG_IMAGE = "registry:443/group/tool:latest";
    private static final String DIGEST = "sha256:6c3c624b58dbbcd3c0dd82b4c53f04194d1247c6eebdaab7c610cf7d66709b3b";
    private static final String PINNED_IMAGE = "registry:443/group/tool@" + DIGEST;
    private static final String CONTAINER_IMAGE = "/spec/containers/0/image";
    private static final String ANNOTATIONS = "/metadata/annotations";
    private static final int CREATED = 201;

    private final PreferenceManager preferenceManager = mock(PreferenceManager.class);
    private final KubernetesManager kubernetesManager = mock(KubernetesManager.class);
    private final KubernetesClient client = mock(KubernetesClient.class);
    private final AtomicReference<byte[]> createdPod = new AtomicReference<>();
    private HttpServer kubernetesApi;
    private PipelineExecutor executor;

    @Before
    public void setUp() throws IOException {
        kubernetesApi = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        kubernetesApi.createContext("/", this::createPod);
        kubernetesApi.start();
        doReturn(new ConfigBuilder()
                .withMasterUrl("http://127.0.0.1:" + kubernetesApi.getAddress().getPort())
                .build())
                .when(client).getConfiguration();
        doReturn(client).when(kubernetesManager).getKubernetesClient();
        doAnswer(invocation -> ((AbstractSystemPreference<?>) invocation.getArguments()[0]).getDefaultValue())
                .when(preferenceManager).getPreference(any(AbstractSystemPreference.class));
        executor = new PipelineExecutor(preferenceManager, mock(ContextualPreferenceManager.class),
                mock(AuthManager.class), Collections.emptyList(), NAMESPACE, kubernetesManager);
    }

    @After
    public void tearDown() {
        kubernetesApi.stop(0);
    }

    @Test
    public void shouldCreatePodPinnedToRunDigest() throws IOException {
        final JsonNode pod = launch(run(DIGEST), true);

        assertEquals(PINNED_IMAGE, pod.at(CONTAINER_IMAGE).asText());
        assertEquals(TAG_IMAGE, pod.at(ANNOTATIONS + "/" + KubernetesConstants.DOCKER_IMAGE_ANNOTATION).asText());
    }

    @Test
    public void shouldCreatePodWithTagIfPinningIsNotAllowed() throws IOException {
        final JsonNode pod = launch(run(DIGEST), false);

        assertEquals(TAG_IMAGE, pod.at(CONTAINER_IMAGE).asText());
        assertTrue(pod.at(ANNOTATIONS).isMissingNode());
    }

    @Test
    public void shouldCreatePodWithTagForRunWithoutDigest() throws IOException {
        final JsonNode pod = launch(run(null), true);

        assertEquals(TAG_IMAGE, pod.at(CONTAINER_IMAGE).asText());
        assertTrue(pod.at(ANNOTATIONS).isMissingNode());
    }

    private JsonNode launch(final PipelineRun run, final boolean pinImageDigest) throws IOException {
        final RunContainerSpec podAssignPolicy = RunContainerSpec.builder()
                .selector(RunContainerSpec.PodAssignSelector.builder().label("pool").value("test").build())
                .skipContainerRequests(true)
                .build();
        executor.launchRootPod("sleep infinity", run, Collections.emptyList(), Collections.emptyList(), POD_ID,
                podAssignPolicy, null, null, ImagePullPolicy.ALWAYS, Collections.emptyMap(), null,
                OSSpecificLaunchCommandTemplate.builder().build(), pinImageDigest);
        return new ObjectMapper().readTree(createdPod.get());
    }

    private PipelineRun run(final String digest) {
        final PipelineRun run = new PipelineRun();
        run.setId(RUN_ID);
        run.setPodId(POD_ID);
        run.setOwner(OWNER);
        run.setPlatform("linux");
        run.setActualDockerImage(TAG_IMAGE);
        run.setActualDockerImageDigest(digest);
        return run;
    }

    private void createPod(final HttpExchange exchange) throws IOException {
        final byte[] pod = IOUtils.toByteArray(exchange.getRequestBody());
        createdPod.set(pod);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(CREATED, pod.length);
        try (OutputStream response = exchange.getResponseBody()) {
            response.write(pod);
        }
    }
}
