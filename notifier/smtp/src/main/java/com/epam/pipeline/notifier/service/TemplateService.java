/*
 * Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.tools.generic.NumberTool;
import org.springframework.stereotype.Service;

import java.io.StringWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Optional;
import java.util.TimeZone;

@Service
public class TemplateService {

    public static final String MESSAGE_TAG = "message";

    public MessageText buildMessageText(final NotificationMessage message) {
        final Optional<NotificationTemplate> template = Optional.ofNullable(message.getTemplate());
        String subject = template.map(NotificationTemplate::getSubject).orElse(message.getSubject());
        String body = template.map(NotificationTemplate::getBody).orElse(message.getBody());

        final VelocityContext velocityContext = getVelocityContext(message);
        velocityContext.put("numberTool", new NumberTool());

        final StringWriter subjectOut = new StringWriter();
        final StringWriter bodyOut = new StringWriter();

        Velocity.evaluate(velocityContext, subjectOut, MESSAGE_TAG + message.hashCode(), subject);
        Velocity.evaluate(velocityContext, bodyOut, MESSAGE_TAG + message.hashCode(), body);

        return MessageText.builder()
                .subject(subjectOut.toString())
                .body(bodyOut.toString())
                .build();
    }

    private VelocityContext getVelocityContext(final NotificationMessage message) {
        final VelocityContext velocityContext = new VelocityContext();
        velocityContext.put("templateParameters", message.getTemplateParameters());

        final Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        velocityContext.put("calendar", calendar);

        final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        velocityContext.put("dateFormat", dateFormat);

        return velocityContext;
    }
}
