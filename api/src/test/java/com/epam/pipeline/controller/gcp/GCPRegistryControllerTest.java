package com.epam.pipeline.controller.gcp;

import com.epam.pipeline.acl.gcp.GCPRegistryApiService;
import com.epam.pipeline.entity.pipeline.GcpArtifactRegistryEvent;
import com.epam.pipeline.entity.pipeline.GcpArtifactRegistryEventBody;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.test.creator.docker.DockerCreatorUtils;
import com.epam.pipeline.test.web.AbstractControllerTest;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Base64;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@WebMvcTest(controllers = GCPRegistryController.class)
public class GCPRegistryControllerTest extends AbstractControllerTest {
    private static final String GCP_REGISTRY_URL = SERVLET_PATH + "/gcpRegistry";
    private static final String NOTIFY_GCP_REGISTRY_URL = GCP_REGISTRY_URL + "/notify";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private final Tool tool = DockerCreatorUtils.getTool();
    @Autowired
    private GCPRegistryApiService mockGcpRegistryApiService;

    @Test
    public void shouldNotifyGcpRegistryEvent() throws Exception {
        final GcpArtifactRegistryEventBody eventBody = createTestEventBody();
        final String content = getObjectMapper().writeValueAsString(eventBody);
        doReturn(tool).when(mockGcpRegistryApiService).notifyGcpRegistryEvent(eq(""), eq(eventBody));

        final MvcResult mvcResult = performRequest(
                post(NOTIFY_GCP_REGISTRY_URL).header(AUTHORIZATION_HEADER, "").content(content));

        verify(mockGcpRegistryApiService).notifyGcpRegistryEvent(eq(""), eq(eventBody));
        assertResponseEntity(mvcResult, tool);
        assertEquals(mvcResult.getResponse().getStatus(), HttpStatus.OK.value());
    }

    private GcpArtifactRegistryEventBody createTestEventBody() throws JsonProcessingException {
        GcpArtifactRegistryEvent gcpEvent = new GcpArtifactRegistryEvent();
        gcpEvent.setAction("INSERT");
        gcpEvent.setDigest("gcp-registry/gcp-project/gcp-repo/docker-image@sha256:f6c859b589b72e67c05db46df6b79ef7dc7");
        gcpEvent.setTag("gcp-registry/gcp-project/gcp-repo/docker-image:latest");

        GcpArtifactRegistryEventBody.Message message = new GcpArtifactRegistryEventBody.Message();
        message.setData(Base64.getEncoder().encodeToString(getObjectMapper().writeValueAsBytes(gcpEvent)));
        message.setMessageId("12345");
        message.setPublishTime("2025-03-19T12:00:00Z");

        GcpArtifactRegistryEventBody event = new GcpArtifactRegistryEventBody();
        event.setMessage(message);
        return event;
    }
}
