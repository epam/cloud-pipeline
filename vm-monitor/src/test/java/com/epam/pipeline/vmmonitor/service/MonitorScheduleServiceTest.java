/*
 * Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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
 */

package com.epam.pipeline.vmmonitor.service;

import com.epam.pipeline.vmmonitor.service.certificate.CertificateMonitor;
import com.epam.pipeline.vmmonitor.service.filesystem.FileSystemMonitor;
import com.epam.pipeline.vmmonitor.service.k8s.KubernetesDeploymentMonitor;
import com.epam.pipeline.vmmonitor.service.k8s.TinyproxyMonitor;
import com.epam.pipeline.vmmonitor.service.node.NodeMonitor;
import com.epam.pipeline.vmmonitor.service.vm.VMMonitor;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.CronSequenceGenerator;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class MonitorScheduleServiceTest {

    private static final String CHANGES_CRON = "monitor.cert.changes.cron";

    private final CertificateMonitor certificateMonitor = mock(CertificateMonitor.class);
    private final MonitorScheduleService service = new MonitorScheduleService(mock(VMMonitor.class),
            certificateMonitor, mock(KubernetesDeploymentMonitor.class), mock(FileSystemMonitor.class),
            mock(TinyproxyMonitor.class), mock(NodeMonitor.class));

    @Test
    public void certChangesShouldRunOnlyTheChangesCheck() {
        service.monitorCertChanges();

        verify(certificateMonitor).monitorChanges();
        verify(certificateMonitor, never()).monitor();
    }

    @Test
    public void certChangesShouldNotFailIfTheChangesCheckFails() {
        doThrow(new IllegalStateException("test")).when(certificateMonitor).monitorChanges();

        service.monitorCertChanges();

        verify(certificateMonitor).monitorChanges();
    }

    @Test
    public void certChangesCronShouldRunEveryMinute() throws IOException {
        final Properties properties = new Properties();
        try (InputStream stream = MonitorScheduleServiceTest.class.getResourceAsStream("/application.properties")) {
            properties.load(stream);
        }
        final CronSequenceGenerator cron = new CronSequenceGenerator(properties.getProperty(CHANGES_CRON));
        final Date start = new Date(TimeUnit.DAYS.toMillis(1));

        final Date first = cron.next(start);
        final Date second = cron.next(first);

        assertThat(second.getTime() - first.getTime(), is(TimeUnit.MINUTES.toMillis(1)));
        assertThat(first.getTime() - start.getTime(), is(TimeUnit.MINUTES.toMillis(1)));
    }
}
