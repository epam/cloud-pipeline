/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.aspect.cluster;

import com.epam.pipeline.manager.cluster.KubernetesManager;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
@ConditionalOnProperty(name = "ha.deploy.enabled",
    havingValue = "true")
@Order(Ordered.LOWEST_PRECEDENCE - 1)
public class ScheduledTasksSynchronizationAspect {

    @Autowired
    private KubernetesManager kubernetesManager;

    @Around("@annotation(net.javacrumbs.shedlock.core.SchedulerLock)")
    public void skipScheduledMethodInvocation(ProceedingJoinPoint joinPoint) throws Throwable {
        if (kubernetesManager.isMasterHost()) {
            joinPoint.proceed();
        } else {
            log.warn("Scheduled method skipped :" + joinPoint.getSignature().toString());
        }
    }
}
