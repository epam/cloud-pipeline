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

package com.epam.pipeline.notifier.service;

import com.epam.pipeline.entity.notification.NotificationMessage;
import com.epam.pipeline.entity.notification.NotificationTemplate;
import com.epam.pipeline.notifier.entity.message.MessageText;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CapacityReservationTemplatesTest {

    private static final Path TEMPLATES = Paths.get("..", "..", "deploy", "contents", "install", "email-templates");
    private static final List<String> TYPES = Arrays.asList(
            "CAPACITY_RESERVATION_REQUIRES_APPROVAL",
            "CAPACITY_RESERVATION_STATUS_CHANGED",
            "CAPACITY_RESERVATION_FINALIZING");
    private static final Pattern VARIABLE =
            Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}|\\$([A-Za-z_][A-Za-z0-9_]*)");
    private static final String RESERVATION_NAME = "g5 reserved pool";
    private static final String REASON = "No available start date in the requested window";
    private static final long COMMITMENT_DURATION = 14 * 24 * 3600L;
    private static final long SIX_HOURS = 6 * 3600L;

    private final TemplateService templateService = new TemplateService();

    @Test
    public void shouldRenderEveryTemplateCompletely() throws IOException {
        for (final String type : TYPES) {
            final MessageText text = render(type, parameters(null));

            assertTrue(text.getSubject().contains(RESERVATION_NAME), type);
            assertTrue(text.getBody().contains(RESERVATION_NAME), type);
            assertTrue(text.getBody().contains("g5.48xlarge"), type);
            assertTrue(text.getBody().contains("https://cp.example.com:443/pipeline/#/cluster/hot"), type);
            assertFalse(text.getSubject().contains("$"), type + " left a reference unrendered");
            assertFalse(text.getBody().contains("$"), type + " left a reference unrendered");
            assertFalse(text.getBody().contains("#if"), type + " left a directive unrendered");
        }
    }

    @Test
    public void shouldRenderTheCommitmentAsDaysAndHours() throws IOException {
        final Map<String, Object> parameters = parameters(null);
        parameters.put("commitmentDuration", COMMITMENT_DURATION + SIX_HOURS);

        for (final String type : TYPES) {
            assertTrue(render(type, parameters).getBody().contains("14d 6h"));
        }
    }

    @Test
    public void shouldLeaveTheCommitmentEmptyWhenTheReservationHasNone() throws IOException {
        final Map<String, Object> parameters = parameters(null);
        parameters.remove("commitmentDuration");

        for (final String type : TYPES) {
            final String body = render(type, parameters).getBody();
            assertFalse(body.contains("null"));
            assertFalse(body.contains("$"));
        }
    }

    @Test
    public void shouldGiveTheReasonForAStatusChangeOnlyWhenThereIsOne() throws IOException {
        assertTrue(render("CAPACITY_RESERVATION_STATUS_CHANGED", parameters(REASON)).getBody().contains(REASON));
        assertFalse(render("CAPACITY_RESERVATION_STATUS_CHANGED", parameters(null)).getBody().contains("Reason:"));
    }

    private MessageText render(final String type, final Map<String, Object> parameters) throws IOException {
        final String body = envsubst(read(TEMPLATES.resolve("contents").resolve(type + ".html")));
        final String subject = new ObjectMapper()
                .readTree(read(TEMPLATES.resolve("configs").resolve(type + ".json")))
                .get("subject").asText();

        final NotificationTemplate template = new NotificationTemplate();
        template.setSubject(subject);
        template.setBody(body);
        final NotificationMessage message = new NotificationMessage();
        message.setTemplate(template);
        message.setTemplateParameters(parameters);
        return templateService.buildMessageText(message);
    }

    private static String envsubst(final String text) {
        final Map<String, String> environment = new HashMap<>();
        environment.put("CP_DOLLAR", "$");
        environment.put("CP_API_SRV_EXTERNAL_HOST", "cp.example.com");
        environment.put("CP_API_SRV_EXTERNAL_PORT", "443");
        environment.put("CP_PREF_UI_PIPELINE_DEPLOYMENT_NAME", "Cloud Pipeline");
        final Matcher matcher = VARIABLE.matcher(text);
        final StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            final String name = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            matcher.appendReplacement(result, Matcher.quoteReplacement(environment.getOrDefault(name, "")));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static Map<String, Object> parameters(final String statusReason) {
        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("reservationId", 7);
        parameters.put("reservationName", RESERVATION_NAME);
        parameters.put("nodePoolId", 3);
        parameters.put("status", "ACTIVE");
        if (statusReason != null) {
            parameters.put("statusReason", statusReason);
        }
        parameters.put("reservationType", "FUTURE_DATED");
        parameters.put("cloudProvider", "AWS");
        parameters.put("regionId", 1);
        parameters.put("instanceType", "g5.48xlarge");
        parameters.put("instanceCount", 2);
        parameters.put("commitmentDuration", COMMITMENT_DURATION);
        parameters.put("startDate", "2026-10-07 12:00:00.000");
        parameters.put("endDate", "2026-10-21 12:00:00.000");
        parameters.put("availabilityZone", "us-east-1c");
        parameters.put("owner", "requester");
        return parameters;
    }

    private static String read(final Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
