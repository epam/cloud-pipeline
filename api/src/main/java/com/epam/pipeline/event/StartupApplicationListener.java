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

package com.epam.pipeline.event;

import com.epam.pipeline.manager.docker.DockerRegistryManager;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@AllArgsConstructor
@Slf4j
@SuppressWarnings("PMD.AvoidCatchingGenericException")
public class StartupApplicationListener {
    private DockerRegistryManager dockerRegistryManager;

    @EventListener
    public void onApplicationEvent(ContextRefreshedEvent event) {
        try {
            if (Objects.isNull(event.getApplicationContext().getParent())) {
                dockerRegistryManager.checkDockerSecrets();
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }
}
