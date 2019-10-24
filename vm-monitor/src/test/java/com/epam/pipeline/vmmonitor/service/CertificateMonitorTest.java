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

package com.epam.pipeline.vmmonitor.service;

import com.epam.pipeline.vmmonitor.service.impl.VMNotificationServiceImpl;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import sun.misc.BASE64Encoder;
import sun.security.provider.X509Factory;
import sun.security.tools.keytool.CertAndKeyGen;
import sun.security.x509.X500Name;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class CertificateMonitorTest {

    private static final String TEST_DIR = "src/test/resources/testScanningFolder/";
    private static final String TEST_CERT_MASK = "pem";
    private static final String KEY_TYPE = "RSA";
    private static final String SIG_ALG = "SHA1WithRSA";
    private static final String CN_TEST = "CN=TEST";
    private static final int KEY_SIZE = 1024;
    private static final int DAYS_TO_NOTIFY = 5;

    private final VMNotificationServiceImpl notificationService;
    private final CertificateMonitor certificateMonitor;
    private final CertAndKeyGen keyGen;

    public CertificateMonitorTest() throws Exception {
        notificationService = Mockito.mock(VMNotificationServiceImpl.class);
        certificateMonitor = new CertificateMonitor(notificationService, TEST_DIR, TEST_CERT_MASK, DAYS_TO_NOTIFY);
        keyGen = new CertAndKeyGen(KEY_TYPE, SIG_ALG);
        keyGen.generate(KEY_SIZE);
    }

    @BeforeEach
    public void createTestCertDir() throws IOException {
        Files.createDirectory(Paths.get(TEST_DIR));
    }

    @AfterEach
    public void removeTestCertDir() throws IOException {
        FileUtils.deleteDirectory(new File(TEST_DIR));
    }

    @Test
    public void testCheckCertificates() {
        final List<Integer> certDurations = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9);
        final int expiringCertificatesCount = (int) certDurations.stream()
            .filter(dur -> dur <= DAYS_TO_NOTIFY)
            .count();

        certDurations.stream()
            .map(this::generateCert)
            .filter(Objects::nonNull)
            .forEach(this::saveCertToTestDir);

        certificateMonitor.checkCertificates();
        Mockito.verify(notificationService, Mockito.times(expiringCertificatesCount))
            .notifyExpiringCertificate(Mockito.any());
    }

    private X509Certificate generateCert(final int validityDays) {
        try {
            return keyGen.getSelfCertificate(new X500Name(CN_TEST), TimeUnit.DAYS.toSeconds(validityDays));
        } catch (Exception e) {
            return null;
        }
    }

    private void saveCertToTestDir(final X509Certificate certificate) {
        final String certName = certificate.getSerialNumber() + ".pem";
        final BASE64Encoder encoder = new BASE64Encoder();
        try (FileOutputStream os = new FileOutputStream(TEST_DIR + certName)) {
            final String certText = String.format("%s\n%s\n%s",
                                                  X509Factory.BEGIN_CERT,
                                                  encoder.encode(certificate.getEncoded()),
                                                  X509Factory.END_CERT);
            os.write(certText.getBytes());
        } catch (Exception e) {
            throw new RuntimeException("Error during certificate saving!");
        }
    }
}
