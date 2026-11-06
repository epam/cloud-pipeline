/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.notifier.util;

import com.epam.pipeline.entity.notification.NotificationMessage;
import com.epam.pipeline.entity.notification.NotificationMessageDlq;
import org.springframework.stereotype.Component;

@Component
public class MessageToDlqConverter {
    private static final int MAX_ERROR_MESSAGE_SIZE = 1000;
    private static final int MAX_ERROR_CAUSE_SIZE = 200;

    public NotificationMessageDlq convertToDlq(NotificationMessage message, Exception exception) {
        NotificationMessageDlq dlqMessage = new NotificationMessageDlq();

        // Copy all fields from original message
        dlqMessage.setSubject(message.getSubject());
        dlqMessage.setBody(message.getBody());
        dlqMessage.setTemplate(message.getTemplate());
        dlqMessage.setToUserId(message.getToUserId());
        dlqMessage.setCopyUserIds(message.getCopyUserIds());
        dlqMessage.setTemplateParameters(message.getTemplateParameters());

        // Set failure reason
        String reason = buildFailureReason(exception);
        dlqMessage.setReason(reason);

        return dlqMessage;
    }

    private String buildFailureReason(Exception exception) {
        if (exception == null) {
            return "Unknown error";
        }

        StringBuilder reasonBuilder = new StringBuilder();
        reasonBuilder.append(exception.getClass().getSimpleName());
        reasonBuilder.append(": ");

        String message = exception.getMessage();
        if (message != null) {
            // Truncate if too long
            if (message.length() > MAX_ERROR_MESSAGE_SIZE) {
                reasonBuilder.append(message, 0, MAX_ERROR_MESSAGE_SIZE);
                reasonBuilder.append("... (truncated)");
            } else {
                reasonBuilder.append(message);
            }
        } else {
            reasonBuilder.append("No error message");
        }

        // Add cause if present
        Throwable cause = exception.getCause();
        if (cause != null && cause != exception) {
            reasonBuilder.append("\nCaused by: ");
            reasonBuilder.append(cause.getClass().getSimpleName());
            if (cause.getMessage() != null) {
                reasonBuilder.append(": ");
                String causeMessage = cause.getMessage();
                if (causeMessage.length() > MAX_ERROR_CAUSE_SIZE) {
                    reasonBuilder.append(causeMessage, 0, MAX_ERROR_CAUSE_SIZE);
                    reasonBuilder.append("...");
                } else {
                    reasonBuilder.append(causeMessage);
                }
            }
        }

        return reasonBuilder.toString();
    }
}
