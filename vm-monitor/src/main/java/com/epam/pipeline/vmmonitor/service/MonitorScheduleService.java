/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
 *
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
 *
 */

package com.epam.pipeline.vmmonitor.service;

import com.epam.pipeline.vmmonitor.service.certificate.CertificateMonitor;
import com.epam.pipeline.vmmonitor.service.filesystem.FileSystemMonitor;
import com.epam.pipeline.vmmonitor.service.k8s.KubernetesDeploymentMonitor;
import com.epam.pipeline.vmmonitor.service.node.NodeMonitor;
import com.epam.pipeline.vmmonitor.service.vm.VMMonitor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.Nullable;

@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("PMD.AvoidCatchingGenericException")
public class MonitorScheduleService {

    private final VMMonitor vmMonitor;
    private final CertificateMonitor certificateMonitor;
    private final KubernetesDeploymentMonitor deploymentMonitor;
    private final FileSystemMonitor fileSystemMonitor;
    @Nullable
    private final NodeMonitor nodeMonitor;

    @Scheduled(cron = "${monitor.schedule.cron}")
    public void monitorVMs() {
        monitor("VMs", vmMonitor);
    }

    @Scheduled(cron = "${monitor.cert.schedule.cron}")
    public void monitorCerts() {
        monitor("PKI certificates", certificateMonitor);
    }

    @Scheduled(cron = "${monitor.k8s.deployment.cron}")
    public void monitorDeployments() {
        monitor("k8s deployments", deploymentMonitor);
    }

    @Scheduled(cron = "${monitor.filesystem.cron}")
    public void monitorFileSystem() {
        monitor("filesystems", fileSystemMonitor);
    }

    @Scheduled(cron = "${monitor.node.cron}")
    public void monitorNodes() {
        monitor("nodes", nodeMonitor);
    }

    private void monitor(final String name, final Monitor monitor) {
        if (monitor == null) {
            log.debug(StringUtils.capitalize(String.format("Skipping disabled %s monitoring...", name)));
            return;
        }
        try {
            log.debug("Starting {} monitoring...", name);
            monitor.monitor();
            log.debug("Finished {} monitoring.", name);
        } catch (Exception e) {
            log.error("Failed {} monitoring.", name, e);
        }
    }
}
