package com.epam.pipeline.acl.gcp;

import com.epam.pipeline.dao.region.CloudRegionDao;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.pipeline.DockerRegistryEvent;
import com.epam.pipeline.entity.pipeline.DockerRegistryEventEnvelope;
import com.epam.pipeline.entity.pipeline.GcpArtifactRegistryEvent;
import com.epam.pipeline.entity.pipeline.GcpArtifactRegistryEventBody;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.entity.region.GCPRegion;
import com.epam.pipeline.exception.GCPAuthorizationException;
import com.epam.pipeline.manager.docker.DockerRegistryAction;
import com.epam.pipeline.manager.docker.DockerRegistryManager;
import com.epam.pipeline.security.jwt.GCPJwtTokenVerifier;
import com.epam.pipeline.test.acl.AbstractAclTest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationServiceException;

import java.util.Base64;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GCPRegistryApiServiceTest extends AbstractAclTest {

    private static final String GCP_REGISTRY = "europe-west3-docker.pkg.dev";
    private static final String GCP_REGION_EUROPE_WEST_3 = "europe-west3";
    private static final String GCP_PROJECT = "gcp-project";
    private static final String GCP_REPOSITORY = "gcp-repo";
    private static final String DOCKER_IMAGE = "docker-image";
    private static final String DOCKER_IMAGE_DIGEST = "sha256:f6c859b589b72e67c05d";
    private static final String GCP_JWT_EXPIRED_TOKEN = "eyJhbGciOiJSUzI1NiIsImtpZCI6IjkxNGZiOWIwODcxODBiYzAzMDMyO" +
            "DQ1MDBjNWY1NDBjNmQ0ZjVlMmYiLCJ0eXAiOiJKV1QifQ.eyJhdWQiOiJodHRwczovL3B1YnN1Yi10dXRvcmlhbC02MTU2MTE3Njc" +
            "0MDkuZXVyb3BlLXdlc3QzLnJ1bi5hcHAvIiwiYXpwIjoiMTA5OTkyMTgwODIyMDQ3NDg2Nzc4IiwiZW1haWwiOiJnY2ZhLXBpcGVs" +
            "aW5lLWduYWkxMC1zYS1kZXZAaGwyLWVwbS1nbmFpMTAtdDFpeWx1LmlhbS5nc2VydmljZWFjY291bnQuY29tIiwiZW1haWxfdmVya" +
            "WZpZWQiOnRydWUsImV4cCI6MTc0MjM3NjcwMiwiaWF0IjoxNzQyMzczMTAyLCJpc3MiOiJodHRwczovL2FjY291bnRzLmdvb2dsZS" +
            "5jb20iLCJzdWIiOiIxMDk5OTIxODA4MjIwNDc0ODY3NzgifQ.iINc0FWK6iXVuujeHWwsCQbHONClZpqS2Ab_YT9fQ65OHg9nkszg" +
            "tO7-C3Jx6M0QwXCyFKVv7lrzGLjBbs7ioKloeG27DmpYgCzgKtUwkPWxuhoivBfIl9GwhbLH1z73LRGK5fJjZVkfP8P1PN6QA8swO" +
            "yn9eQg2n7w7OofguwAmb2Qi4Raa4F5OjH-YjdTAGhHl16L-eciofV7wU8i_JRKJOWMSehw3fN4EPv0OYz1FY_5pkafrGQY7XG4DgX" +
            "u9-UbKLVvUQ-IaglEbvDo-u5utbYpIlGeezn_PRV9rXN93hhYRBW4OLzN5xpnBpNuQAN7kOKkSCPfnNuCtaFQGhg";
    private static final String GCP_JWT_EXPIRED_TOKEN_BEARER = "Bearer " + GCP_JWT_EXPIRED_TOKEN;
    @Autowired
    private GCPRegistryApiService gcpRegistryApiService;
    @Autowired
    private GCPJwtTokenVerifier gcpJwtTokenVerifier;
    @Autowired
    private DockerRegistryManager dockerRegistryManager;
    @Autowired
    private CloudRegionDao cloudRegionDao;
    @Test(expected = AuthenticationServiceException.class)
    public void shouldFailOnEmptyAuthHeader() {
        gcpRegistryApiService.notifyGcpRegistryEvent("", null);
    }

    @Test(expected = GCPAuthorizationException.class)
    public void shouldFailOnExpiredToken() {
        gcpRegistryApiService.notifyGcpRegistryEvent(GCP_JWT_EXPIRED_TOKEN_BEARER, null);
    }

    @Test
    public void notifyGcpRegistryEventSuccess() throws JsonProcessingException {
        doNothing().when(gcpJwtTokenVerifier).validateToken(GCP_JWT_EXPIRED_TOKEN);
        final DockerRegistry dockerRegistry = buildRegistry(CloudProvider.GCP, GCP_REGISTRY);
        when(dockerRegistryManager.loadByNameOrId(GCP_REGISTRY)).thenReturn(dockerRegistry);
        when(cloudRegionDao.loadByRegionName(GCP_REGION_EUROPE_WEST_3))
                .thenReturn(Optional.of(buildRegion(GCP_REGION_EUROPE_WEST_3)));
        final Tool expectedTool = buildTool(DOCKER_IMAGE);
        when(dockerRegistryManager.notifyDockerRegistryEvents(anyString(), any(DockerRegistryEventEnvelope.class)))
                .thenReturn(Collections.singletonList(expectedTool));

        final Tool actualTool = gcpRegistryApiService
                .notifyGcpRegistryEvent(GCP_JWT_EXPIRED_TOKEN_BEARER, createTestEventBody("INSERT"));

        verify(gcpJwtTokenVerifier).validateToken(eq(GCP_JWT_EXPIRED_TOKEN));
        verify(dockerRegistryManager).loadByNameOrId(eq(GCP_REGISTRY));
        verify(cloudRegionDao).loadByRegionName(eq(GCP_REGION_EUROPE_WEST_3));
        ArgumentCaptor<DockerRegistryEventEnvelope> captor = ArgumentCaptor.forClass(DockerRegistryEventEnvelope.class);

        verify(dockerRegistryManager).notifyDockerRegistryEvents(eq(GCP_REGISTRY), captor.capture());


        assertThat(actualTool.getImage()).isEqualTo(expectedTool.getImage());
        assertThat(actualTool.getRegistry()).isEqualTo(expectedTool.getRegistry());
        assertThat(actualTool.getOwner()).isEqualTo(expectedTool.getOwner());

        DockerRegistryEventEnvelope dockerRegistryEventEnvelope = captor.getValue();
        assertThat(dockerRegistryEventEnvelope.getEvents()).size().isEqualTo(1);

        DockerRegistryEvent dockerRegistryEvent = dockerRegistryEventEnvelope.getEvents().get(0);
        assertThat(dockerRegistryEvent.getAction()).isEqualTo(DockerRegistryAction.PUSH.getAction());
        assertThat(dockerRegistryEvent.getTarget()).isNotNull();
        assertThat(dockerRegistryEvent.getTarget().getDigest()).isEqualTo(DOCKER_IMAGE_DIGEST);
        assertThat(dockerRegistryEvent.getTarget().getTag()).isEqualTo("latest");
        assertThat(dockerRegistryEvent.getTarget().getRepository())
                .isEqualTo(String.format("%s/%s/%s", GCP_PROJECT, GCP_REPOSITORY, DOCKER_IMAGE));
        assertThat(dockerRegistryEvent.getActor()).isNotNull();
        assertThat(dockerRegistryEvent.getActor().getName()).isEqualTo("PIPE_ADMIN");
    }

    @Test
    public void notifyGcpRegistryEventShouldIgnoreDeleteAction() throws JsonProcessingException {
        doNothing().when(gcpJwtTokenVerifier).validateToken(GCP_JWT_EXPIRED_TOKEN);

        final Tool actualTool = gcpRegistryApiService
                .notifyGcpRegistryEvent(GCP_JWT_EXPIRED_TOKEN_BEARER, createTestEventBody("DELETE"));

        verify(gcpJwtTokenVerifier).validateToken(eq(GCP_JWT_EXPIRED_TOKEN));
        verify(dockerRegistryManager, never()).loadByNameOrId(anyString());
        verify(dockerRegistryManager, never()).notifyDockerRegistryEvents(anyString(), anyObject());
        verify(cloudRegionDao, never()).loadByRegionName(anyString());

        assertThat(actualTool).isNull();
    }

    private GcpArtifactRegistryEventBody createTestEventBody(String action) throws JsonProcessingException {
        GcpArtifactRegistryEvent gcpEvent = new GcpArtifactRegistryEvent();
        gcpEvent.setAction(action);
        gcpEvent.setDigest("europe-west3-docker.pkg.dev/gcp-project/gcp-repo/docker-image@sha256:f6c859b589b72e67c05d");
        gcpEvent.setTag("gcp-registry/gcp-project/gcp-repo/docker-image:latest");

        GcpArtifactRegistryEventBody.Message message = new GcpArtifactRegistryEventBody.Message();
        message.setData(Base64.getEncoder().encodeToString(new ObjectMapper().writeValueAsBytes(gcpEvent)));
        message.setMessageId("12345");
        message.setPublishTime("2025-03-19T12:00:00Z");

        GcpArtifactRegistryEventBody event = new GcpArtifactRegistryEventBody();
        event.setMessage(message);
        return event;
    }

    private DockerRegistry buildRegistry(CloudProvider provider, String path) {
        final DockerRegistry registry = new DockerRegistry();
        registry.setPath(path);
        registry.setOwner("PIPE_ADMIN");
        registry.setProvider(provider);
        return registry;
    }

    private GCPRegion buildRegion(String region) {
        final GCPRegion gcpRegion = new GCPRegion();
        gcpRegion.setName(region);
        gcpRegion.setRegionCode(region);
        gcpRegion.setProject(GCP_PROJECT);
        gcpRegion.setProvider(CloudProvider.GCP);
        return gcpRegion;
    }

    private Tool buildTool(String image) {
        final Tool tool = new Tool();
        tool.setImage(image);
        tool.setCpu("0mi");
        tool.setRam("0Gi");
        tool.setRegistry(GCP_REGISTRY);
        tool.setOwner("PIPE_ADMIN");
        return tool;
    }
}
