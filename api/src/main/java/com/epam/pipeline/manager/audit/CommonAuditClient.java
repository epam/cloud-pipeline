/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * This class shall be used to support common audit logs.
 * There are two options to support audit logging:
 * - wire this client to desired bean and log message
 * - process method/class that contains audit log directly in {@link com.epam.pipeline.security.SecurityLogAspect}
 * @see com.epam.pipeline.security.SecurityLogAspect#AUDIT_RELATED_METHODS_POINTCUT
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CommonAuditClient {

    public void log(final String message) {
        log.info(message);
    }
}
