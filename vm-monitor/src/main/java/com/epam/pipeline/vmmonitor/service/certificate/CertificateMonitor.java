/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.vmmonitor.service.certificate;

import com.epam.pipeline.vmmonitor.model.cert.PkiCertificate;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Service
@Slf4j
public class CertificateMonitor {

    private static final String DELIMITER = ",";
    private final List<String> directoriesToScan;
    private final List<String> certificateMasks;
    private final int daysToNotify;
    private final CertificateNotifier notifier;

    @Autowired
    public CertificateMonitor(final CertificateNotifier notifier,
                              @Value("${monitor.cert.scan.folders}") final String directoriesToScan,
                              @Value("${monitor.cert.file.masks:pem}") final String certificateMasks,
                              @Value("${monitor.cert.expiration.notification.days:5}") final int daysToNotify) {
        this.notifier = notifier;
        this.directoriesToScan = Arrays.asList(directoriesToScan.split(DELIMITER));
        this.certificateMasks = Arrays.asList(certificateMasks.split(DELIMITER));
        this.daysToNotify = daysToNotify;
    }

    public void checkCertificates() {
        directoriesToScan.forEach(this::scanDirForCertificates);
    }

    private void scanDirForCertificates(final String dirPath) {
        if (StringUtils.isBlank(dirPath)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(Paths.get(dirPath))) {
            paths
                .filter(this::isCertFile)
                .forEach(this::checkExpiration);
        } catch (IOException e) {
            log.error("Error during {} scanning: {}!", dirPath, e.getMessage());
        }
    }

    private void checkExpiration(final Path filePath) {
        generateX509Certificate(filePath)
            .filter(this::isExpiring)
            .map(x509cert -> new PkiCertificate(x509cert, filePath))
            .ifPresent(notifier::notifyExpiringCertificate);
    }

    private Optional<X509Certificate> generateX509Certificate(final Path pathToFile) {
        try (FileInputStream fis = new FileInputStream(pathToFile.toString());
             BufferedInputStream bis = new BufferedInputStream(fis)) {
            final CertificateFactory factory = CertificateFactory.getInstance("X.509");
            return Optional.of((X509Certificate) factory.generateCertificate(bis));
        } catch (CertificateException | IOException e) {
            log.error("Error generating certificate from {}: {}", pathToFile, e.getMessage());
            return Optional.empty();
        }
    }

    private boolean isCertFile(final Path pathToFile) {
        return Files.isRegularFile(pathToFile)
               && certificateMasks.stream().anyMatch(mask -> pathToFile.toString().endsWith(mask));
    }

    private boolean isExpiring(final X509Certificate certificate) {
        final LocalDateTime expirationDate = LocalDateTime
            .ofInstant(certificate.getNotAfter().toInstant(), ZoneId.systemDefault());
        return LocalDateTime.now().plusDays(daysToNotify).isAfter(expirationDate);
    }

}
