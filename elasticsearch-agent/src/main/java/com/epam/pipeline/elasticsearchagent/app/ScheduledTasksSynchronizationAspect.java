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

package com.epam.pipeline.elasticsearchagent.app;

import com.epam.pipeline.elasticsearchagent.rest.cluster.PodMasterStatusApi;
import com.epam.pipeline.elasticsearchagent.rest.cluster.PodMasterStatusApiBuilder;
import com.epam.pipeline.entity.cluster.MasterPodInfo;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Aspect
@Component
@ConditionalOnProperty(name = "ha.deploy.enabled", havingValue = "true")
public class ScheduledTasksSynchronizationAspect {

    @Value("${kube.master.pod.check.url}")
    private String baseUrl;

    @Value("${kube.pod.name.env.var:HOSTNAME}")
    private String podNameEnvVar;

    @Around("@annotation(net.javacrumbs.shedlock.core.SchedulerLock)")
    public void skipScheduledMethodInvocation(ProceedingJoinPoint joinPoint) throws Throwable {
        if (isMasterHost()) {
            joinPoint.proceed();
        } else {
            log.warn("Scheduled method skipped :" + joinPoint.getSignature().toString());
        }
    }

    /**
     * Check if current host is master or not
     *
     * @return true - if a host is master or no master found, false - otherwise
     */
    private boolean isMasterHost() {
        final String masterName = receiveMasterName();
        return masterName == null
               || masterName.equals(System.getenv(podNameEnvVar));
    }

    /**
     * Obtains a master pod name.
     *
     * @return master name or <code>null</code> if no such found
     */
    private String receiveMasterName() {
        final PodMasterStatusApi masterPodApi = new PodMasterStatusApiBuilder(baseUrl).build();
        try {
            final MasterPodInfo masterInfo = masterPodApi.getMasterName().execute().body();
            return (masterInfo != null)
                   ? masterInfo.getName()
                   : null;
        } catch (IOException e) {
            log.warn("Can't receive master info!");
            return null;
        }
    }
}
