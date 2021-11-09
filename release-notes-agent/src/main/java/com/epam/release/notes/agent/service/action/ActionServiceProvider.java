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
import org.apache.commons.collections4.ListUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.lang.String.format;

@Component
public class ActionServiceProvider {

    private final Map<Action, ActionNotificationService> notificationServiceProviders;

    public ActionServiceProvider(final List<ActionNotificationService> actionNotificationServices) {
        this.notificationServiceProviders = ListUtils.emptyIfNull(actionNotificationServices).stream()
                .collect(Collectors.toMap(ActionNotificationService::getServiceAction, Function.identity()));
    }

    public ActionNotificationService getActionService(final String actionName) {
        final Action action = Action.getByName(actionName);
        final ActionNotificationService service = notificationServiceProviders.get(action);
        if (service == null) {
            throw new IllegalArgumentException(format("The %s action is not supported!", actionName));
        }
        return service;
    }
}
