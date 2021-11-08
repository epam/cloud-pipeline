/*
 * Copyright 2021 EPAM Systems, Inc. (https://www.epam.com/)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.epam.release.notes.agent.service.action;

import com.epam.release.notes.agent.entity.action.Action;
import com.epam.release.notes.agent.service.action.mail.TemplateNotificationService;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class ActionServiceProvider {

    private final JavaMailSender javaMailSender;
    private final TemplateNotificationService templateNotificationService;

    public ActionServiceProvider(final JavaMailSender javaMailSender,
                                 final TemplateNotificationService templateNotificationService) {
        this.javaMailSender = javaMailSender;
        this.templateNotificationService = templateNotificationService;
    }

    public ActionNotificationService getActionService(final String actionName) {
        final Action action = Action.getByName(actionName);
        if (Action.PUBLICATION == action) {
            return new PublishActionNotificationService();
        }
        return new EmailSendActionNotificationService(javaMailSender, templateNotificationService);
    }
}
