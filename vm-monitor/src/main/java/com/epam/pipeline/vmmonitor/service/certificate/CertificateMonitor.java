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
import com.epam.pipeline.vmmonitor.service.Monitor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Notifies about the certificate files of the scanned folders that expire within the configured number of days.
 * <p>
 * {@link #monitor()} is the full scan: it checks every certificate file. {@link #monitorChanges()} checks only the
 * files that are new or changed since their folder was scanned last time, so that a certificate which is added close
 * to its expiration is reported at the next check, not at the next full scan.
 * <p>
 * The first complete scan of a folder is its baseline: {@link #monitorChanges()} only records its files, so a
 * restart of the service sends no notifications. A scan that fails part way keeps the files recorded before for the
 * part it could not read. A file whose attributes cannot be read keeps its record; at the baseline, it is recorded
 * as unreadable, and then without a check once it can be read.
 */
@Service
@Slf4j
public class CertificateMonitor implements Monitor {

    private static final String DELIMITER = ",";
    private static final CertificateFile UNREADABLE = new CertificateFile(null, -1, null);
    private final List<String> directoriesToScan;
    private final List<String> certificateMasks;
    private final int daysToNotify;
    private final boolean changesMonitoringEnabled;
    private final CertificateNotifier notifier;
    private final Map<String, Map<Path, CertificateFile>> knownCertificates = new HashMap<>();

    @Autowired
    public CertificateMonitor(final CertificateNotifier notifier,
                              @Value("${monitor.cert.scan.folders}") final String directoriesToScan,
                              @Value("${monitor.cert.file.masks:pem}") final String certificateMasks,
                              @Value("${monitor.cert.expiration.notification.days:5}") final int daysToNotify,
                              @Value("${monitor.cert.changes.enable:true}") final boolean changesMonitoringEnabled) {
        this.notifier = notifier;
        this.directoriesToScan = Arrays.asList(directoriesToScan.split(DELIMITER));
        this.certificateMasks = Arrays.asList(certificateMasks.split(DELIMITER));
        this.daysToNotify = daysToNotify;
        this.changesMonitoringEnabled = changesMonitoringEnabled;
    }

    @Override
    public synchronized void monitor() {
        directoriesToScan.stream()
            .filter(StringUtils::isNotBlank)
            .forEach(dirPath -> {
                final FolderScan scan = scanDirForCertificates(dirPath, true);
                scan.getCertificates().keySet().forEach(this::checkExpiration);
                remember(dirPath, scan);
            });
    }

    /**
     * Checks the certificate files that are new or changed since the last scan of their folder. A file is changed
     * when its modification time, size or file key (the inode on Linux) differs, so a file replaced by another one
     * with the same modification time is checked too. Does nothing if {@code monitor.cert.changes.enable} is false.
     */
    public synchronized void monitorChanges() {
        if (!changesMonitoringEnabled) {
            log.debug("Skipping disabled monitoring of new and changed PKI certificates...");
            return;
        }
        directoriesToScan.stream()
            .filter(StringUtils::isNotBlank)
            .forEach(this::checkChanges);
    }

    private void checkChanges(final String dirPath) {
        final FolderScan scan = scanDirForCertificates(dirPath, false);
        final Map<Path, CertificateFile> known = knownCertificates.get(dirPath);
        if (known != null) {
            scan.getCertificates().entrySet().stream()
                .filter(certificate -> isChanged(certificate.getValue(), known.get(certificate.getKey())))
                .map(Map.Entry::getKey)
                .forEach(this::checkChangedCertificate);
        } else if (scan.isComplete()) {
            log.debug("Recorded {} certificate files of {}", scan.getCertificates().size(), dirPath);
        }
        remember(dirPath, scan);
    }

    /**
     * A file is new if it was not recorded, and changed if its record differs. A file that could not be read at the
     * baseline is neither: it was there before.
     */
    private static boolean isChanged(final CertificateFile file, final CertificateFile previous) {
        return !UNREADABLE.equals(previous) && !file.equals(previous);
    }

    /**
     * Checks one new or changed file. A failure, e.g. of the notification, is logged and does not stop the check of
     * the other files: the folder is still recorded, so the file is not checked again until it changes.
     */
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void checkChangedCertificate(final Path filePath) {
        log.info("Checking new or changed certificate file {}", filePath);
        try {
            checkExpiration(filePath);
        } catch (RuntimeException e) {
            log.error("Failed to check new or changed certificate file {}: {}", filePath, e.getMessage());
        }
    }

    /**
     * Records the files of a scan. A complete scan replaces what was known about the folder: a file whose attributes
     * could not be read keeps its record, or is recorded as unreadable at the baseline, and a new one is not recorded
     * until it can be read. A scan that failed part way only adds to what was known, as a file it did not list may
     * still be there; before the first complete scan, it records nothing, so that the next scan is the baseline.
     */
    private void remember(final String dirPath, final FolderScan scan) {
        final Map<Path, CertificateFile> known = knownCertificates.get(dirPath);
        if (scan.isComplete()) {
            final Map<Path, CertificateFile> recorded = new LinkedHashMap<>(scan.getCertificates());
            for (final Path path : scan.getUnreadable()) {
                if (known == null) {
                    recorded.put(path, UNREADABLE);
                } else if (known.containsKey(path)) {
                    recorded.put(path, known.get(path));
                }
            }
            knownCertificates.put(dirPath, recorded);
        } else if (known != null) {
            final Map<Path, CertificateFile> merged = new LinkedHashMap<>(known);
            merged.putAll(scan.getCertificates());
            knownCertificates.put(dirPath, merged);
        }
    }

    /**
     * The certificate files of the folder and of its subfolders, and the ones whose attributes cannot be read. The
     * scan is not complete if the folder cannot be walked. Only the full scan logs that as an error: the changes check
     * runs every minute, and a folder of the list may not exist in the service, e.g. if it is not mounted.
     */
    private FolderScan scanDirForCertificates(final String dirPath, final boolean fullScan) {
        final Map<Path, CertificateFile> certificates = new LinkedHashMap<>();
        final Set<Path> unreadable = new HashSet<>();
        try (Stream<Path> paths = Files.walk(Paths.get(dirPath))) {
            final Iterator<Path> iterator = paths.iterator();
            while (iterator.hasNext()) {
                final Path path = iterator.next();
                if (isCertificateFileName(path) && !readCertificateFile(path, certificates)) {
                    unreadable.add(path);
                }
            }
            return new FolderScan(certificates, unreadable, true);
        } catch (IOException | UncheckedIOException e) {
            if (fullScan) {
                log.error("Error during {} scanning: {}!", dirPath, e.getMessage());
            } else {
                log.debug("Error during {} scanning: {}!", dirPath, e.getMessage());
            }
            return new FolderScan(certificates, unreadable, false);
        }
    }

    private boolean isCertificateFileName(final Path path) {
        return certificateMasks.stream().anyMatch(mask -> path.toString().endsWith(mask));
    }

    /**
     * Adds the file to the certificates if it is a regular file, with what tells it from its previous version.
     * Returns false if its attributes cannot be read, unless it was removed after it was listed.
     */
    private boolean readCertificateFile(final Path path, final Map<Path, CertificateFile> certificates) {
        try {
            final BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
            if (attributes.isRegularFile()) {
                certificates.put(path, new CertificateFile(attributes.lastModifiedTime(), attributes.size(),
                                                           attributes.fileKey()));
            }
            return true;
        } catch (NoSuchFileException e) {
            log.debug("Certificate file {} does not exist: {}", path, e.getMessage());
            return true;
        } catch (IOException e) {
            log.debug("Unable to read the attributes of {}: {}", path, e.getMessage());
            return false;
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
            log.debug("Reading certificate from file {}", pathToFile.toAbsolutePath());
            final CertificateFactory factory = CertificateFactory.getInstance("X.509");
            return Optional.of((X509Certificate) factory.generateCertificate(bis));
        } catch (CertificateException | IOException e) {
            log.error("Error generating certificate from {}: {}", pathToFile, e.getMessage());
            return Optional.empty();
        }
    }

    private boolean isExpiring(final X509Certificate certificate) {
        log.debug("Certificate for {} expires after {}", certificate.getSubjectDN(), certificate.getNotAfter());
        final LocalDateTime expirationDate = LocalDateTime
            .ofInstant(certificate.getNotAfter().toInstant(), ZoneId.systemDefault());
        return LocalDateTime.now().plusDays(daysToNotify).isAfter(expirationDate);
    }

    @lombok.Value
    private static class CertificateFile {
        private final FileTime lastModifiedTime;
        private final long size;
        private final Object fileKey;
    }

    private static final class FolderScan {
        private final Map<Path, CertificateFile> certificates;
        private final Set<Path> unreadable;
        private final boolean complete;

        FolderScan(final Map<Path, CertificateFile> certificates, final Set<Path> unreadable, final boolean complete) {
            this.certificates = certificates;
            this.unreadable = unreadable;
            this.complete = complete;
        }

        Map<Path, CertificateFile> getCertificates() {
            return certificates;
        }

        Set<Path> getUnreadable() {
            return unreadable;
        }

        boolean isComplete() {
            return complete;
        }
    }
}
