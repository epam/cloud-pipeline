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
import com.epam.pipeline.vmmonitor.service.k8s.TinyproxyMonitor;
import com.epam.pipeline.vmmonitor.service.vm.VMMonitor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final TinyproxyMonitor tinyproxyMonitor;

    @Scheduled(cron = "${monitor.schedule.cron}")
    public void monitor() {
        try {
            log.debug("Starting VM monitoring");
            vmMonitor.monitor();
            log.debug("Finished VM monitoring");
        } catch (Exception e) {
            log.error("Un error occurred during VM monitoring", e);
        }
    }

    @Scheduled(cron = "${monitor.cert.schedule.cron}")
    public void monitorCerts() {
        try {
            log.debug("Starting PKI certificates check.");
            certificateMonitor.checkCertificates();
            log.debug("Finished PKI certificates check.");
        } catch (Exception e) {
            log.error("An error occurred during PKI certificates check.", e);
        }
    }

    @Scheduled(cron = "${monitor.k8s.deployment.cron}")
    public void monitorDeployments() {
        try {
            log.debug("Starting k8s deployment check.");
            deploymentMonitor.monitorDeployments();
            log.debug("Finished k8s deployment check.");
        } catch (Exception e) {
            log.error("An error occurred during Pk8s deployment check.", e);
        }
    }

    @Scheduled(cron = "${monitor.filesystem.cron}")
    public void monitorFileSystem() {
        try {
            log.debug("Starting filesystem check.");
            fileSystemMonitor.checkFileSystemConsumption();
            log.debug("Finished filesystem check.");
        } catch (Exception e) {
            log.error("An error occurred during filesystem check.", e);
        }
    }

    @Scheduled(cron = "${monitor.tinyproxy.cron}")
    public void monitorTinyproxy() {
        if (tinyproxyMonitor == null) {
            log.debug("Tinyproxy monitoring is disabled.");
            return;
        }
        try {
            log.debug("Starting tinyproxy check.");
            tinyproxyMonitor.monitor();
            log.debug("Finished tinyproxy check.");
        } catch (Exception e) {
            log.error("An error occurred during tinyproxy check.", e);
        }
    }
}
