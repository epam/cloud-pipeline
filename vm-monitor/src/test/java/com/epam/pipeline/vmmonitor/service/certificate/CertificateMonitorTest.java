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

package com.epam.pipeline.vmmonitor.service.certificate;

import com.epam.pipeline.vmmonitor.model.cert.PkiCertificate;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * The certificates are self-signed: {@code certificates/expired.pem} expired on 2000-01-02, so it is always within
 * the days to notify; {@code certificates/valid.pem} is valid until 2126. Both were made with {@code keytool
 * -genkeypair} ({@code -startdate "2000/01/01 00:00:00" -validity 1} and {@code -validity 36500}) and
 * {@code keytool -exportcert -rfc}.
 */
public class CertificateMonitorTest {

    private static final String EXPIRED = "expired.pem";
    private static final String VALID = "valid.pem";
    private static final String ADDED = "added.pem";
    // Not a certificate file name: the link to it is
    private static final String LINK_TARGET = "target.data";
    private static final String MASKS = "pem,crt";
    private static final int DAYS_TO_NOTIFY = 7;
    private static final long ONE_HOUR = TimeUnit.HOURS.toMillis(1);

    private final CertificateNotifier notifier = mock(CertificateNotifier.class);
    private Path root;
    private Path folder;
    private Path otherFolder;

    @BeforeEach
    public void setUp() throws IOException {
        root = Files.createTempDirectory("certificate-monitor-test");
        folder = Files.createDirectory(root.resolve("pki"));
        otherFolder = Files.createDirectory(root.resolve("other-pki"));
    }

    @AfterEach
    public void tearDown() throws IOException {
        FileUtils.deleteDirectory(root.toFile());
    }

    @Test
    public void fullScanShouldNotifyEveryExpiringCertificateOfEveryFolderAndSubfolder() throws IOException {
        final Path expired = copy(EXPIRED, folder.resolve(EXPIRED));
        copy(VALID, folder.resolve(VALID));
        final Path expiredCrt = copy(EXPIRED, Files.createDirectory(folder.resolve("sub")).resolve("expired.crt"));
        copy(EXPIRED, folder.resolve("expired.txt"));
        final Path expiredInOtherFolder = copy(EXPIRED, otherFolder.resolve(EXPIRED));

        monitor(true).monitor();

        assertThat(notifiedPaths(3), containsInAnyOrder(expired, expiredCrt, expiredInOtherFolder));
    }

    @Test
    public void firstChangesCheckShouldOnlyRecordTheCertificates() throws IOException {
        copy(EXPIRED, folder.resolve(EXPIRED));

        monitor(true).monitorChanges();

        verify(notifier, never()).notifyExpiringCertificate(any());
    }

    @Test
    public void changesCheckShouldNotifyNewExpiringCertificateOnce() throws IOException {
        copy(EXPIRED, folder.resolve(EXPIRED));
        final CertificateMonitor monitor = monitor(true);
        monitor.monitorChanges();

        final Path added = copy(EXPIRED, Files.createDirectory(folder.resolve("e2e-tests")).resolve("added.crt"));
        monitor.monitorChanges();
        monitor.monitorChanges();

        assertThat(notifiedPaths(1), containsInAnyOrder(added));
    }

    @Test
    public void changesCheckShouldNotNotifyNewValidCertificate() throws IOException {
        final CertificateMonitor monitor = monitor(true);
        monitor.monitorChanges();

        copy(VALID, folder.resolve(VALID));
        monitor.monitorChanges();

        verify(notifier, never()).notifyExpiringCertificate(any());
    }

    @Test
    public void changesCheckShouldNotifyCertificateWithNewModificationTime() throws IOException {
        final Path expired = copy(EXPIRED, folder.resolve(EXPIRED));
        final CertificateMonitor monitor = monitor(true);
        monitor.monitorChanges();

        touch(expired, Files.getLastModifiedTime(expired).toMillis() + ONE_HOUR);
        monitor.monitorChanges();

        assertThat(notifiedPaths(1), containsInAnyOrder(expired));
    }

    @Test
    public void changesCheckShouldNotifyCertificateReplacedWithTheSameModificationTimeAndSize() throws IOException {
        final Path certificate = copy(VALID, folder.resolve("certificate.pem"));
        final CertificateMonitor monitor = monitor(true);
        monitor.monitorChanges();

        final Path replacement = copy(EXPIRED, root.resolve("replacement.pem"));
        Files.setLastModifiedTime(replacement, Files.getLastModifiedTime(certificate));
        // Only the file key (the inode) tells the replacement from the replaced file
        assertThat(Files.getLastModifiedTime(replacement), is(Files.getLastModifiedTime(certificate)));
        assertThat(Files.size(replacement), is(Files.size(certificate)));
        Files.move(replacement, certificate, StandardCopyOption.REPLACE_EXISTING);
        monitor.monitorChanges();

        assertThat(notifiedPaths(1), containsInAnyOrder(certificate));
    }

    @Test
    public void changesCheckShouldKeepTheCertificatesOfAFolderThatCannotBeRead() throws IOException {
        copy(EXPIRED, folder.resolve(EXPIRED));
        final CertificateMonitor monitor = monitor(true);
        monitor.monitorChanges();

        final Path moved = root.resolve("moved-pki");
        Files.move(folder, moved);
        monitor.monitorChanges();
        copy(EXPIRED, moved.resolve(ADDED));
        // The same files, with the same attributes, and one new file
        Files.move(moved, folder);
        monitor.monitorChanges();

        assertThat(notifiedPaths(1), containsInAnyOrder(folder.resolve(ADDED)));
    }

    @Test
    public void changesCheckShouldKeepACertificateWhoseAttributesCannotBeRead() throws IOException {
        final Path target = copy(EXPIRED, folder.resolve(LINK_TARGET));
        Files.createSymbolicLink(folder.resolve("link.pem"), target);
        final CertificateMonitor monitor = monitor(true);
        monitor.monitorChanges();

        // A symbolic link loop: reading the attributes fails, but not because the file does not exist
        final Path saved = root.resolve(LINK_TARGET);
        Files.move(target, saved);
        Files.createSymbolicLink(target, target);
        monitor.monitorChanges();
        Files.delete(target);
        Files.move(saved, target);
        monitor.monitorChanges();

        verify(notifier, never()).notifyExpiringCertificate(any());
    }

    @Test
    public void changesCheckShouldStillCheckAFolderWithACertificateThatCannotBeReadFromTheStart() throws IOException {
        final Path expired = copy(EXPIRED, folder.resolve(EXPIRED));
        final Path saved = copy(EXPIRED, root.resolve(LINK_TARGET));
        final Path target = folder.resolve(LINK_TARGET);
        Files.createSymbolicLink(target, target);
        Files.createSymbolicLink(folder.resolve("link.pem"), target);
        final CertificateMonitor monitor = monitor(true);
        monitor.monitorChanges();

        // The folder has its baseline: a new file is checked, and a removed one is forgotten
        final Path added = copy(EXPIRED, folder.resolve(ADDED));
        monitor.monitorChanges();
        Files.delete(expired);
        monitor.monitorChanges();
        copy(EXPIRED, expired);
        monitor.monitorChanges();
        // The file that could not be read at the baseline was there before: it is recorded without a check
        Files.delete(target);
        Files.move(saved, target);
        monitor.monitorChanges();

        assertThat(notifiedPaths(2), containsInAnyOrder(added, expired));
    }

    @Test
    public void changesCheckShouldNotNotifyCertificatesCheckedByTheFullScan() throws IOException {
        final CertificateMonitor monitor = monitor(true);
        monitor.monitorChanges();
        final Path expired = copy(EXPIRED, folder.resolve(EXPIRED));
        monitor.monitor();
        assertThat(notifiedPaths(1), containsInAnyOrder(expired));
        clearInvocations(notifier);

        monitor.monitorChanges();

        verify(notifier, never()).notifyExpiringCertificate(any());
    }

    @Test
    public void changesCheckShouldGoOnAndRecordTheFilesIfANotificationFails() throws IOException {
        final CertificateMonitor monitor = monitor(true);
        monitor.monitorChanges();
        doThrow(new IllegalStateException("test")).when(notifier).notifyExpiringCertificate(any());

        final Path expired = copy(EXPIRED, folder.resolve(EXPIRED));
        final Path otherExpired = copy(EXPIRED, folder.resolve("other.pem"));
        final Path expiredInOtherFolder = copy(EXPIRED, otherFolder.resolve(EXPIRED));
        monitor.monitorChanges();
        monitor.monitorChanges();

        assertThat(notifiedPaths(3), containsInAnyOrder(expired, otherExpired, expiredInOtherFolder));
    }

    @Test
    public void changesCheckShouldNotifyRemovedCertificateWhenItIsBack() throws IOException {
        final Path expired = copy(EXPIRED, folder.resolve(EXPIRED));
        final CertificateMonitor monitor = monitor(true);
        monitor.monitorChanges();

        Files.delete(expired);
        monitor.monitorChanges();
        verify(notifier, never()).notifyExpiringCertificate(any());

        copy(EXPIRED, expired);
        monitor.monitorChanges();

        assertThat(notifiedPaths(1), containsInAnyOrder(expired));
    }

    @Test
    public void changesCheckShouldTakeTheFirstScanOfAFolderCreatedLaterAsItsBaseline() throws IOException {
        final Path later = root.resolve("later-pki");
        final CertificateMonitor monitor = new CertificateMonitor(notifier, later.toString(), MASKS, DAYS_TO_NOTIFY,
                true);
        monitor.monitorChanges();

        copy(EXPIRED, Files.createDirectory(later).resolve(EXPIRED));
        monitor.monitorChanges();
        verify(notifier, never()).notifyExpiringCertificate(any());

        final Path added = copy(EXPIRED, later.resolve(ADDED));
        monitor.monitorChanges();

        assertThat(notifiedPaths(1), containsInAnyOrder(added));
    }

    @Test
    public void disabledChangesCheckShouldNotNotifyButFullScanShould() throws IOException {
        final CertificateMonitor monitor = monitor(false);
        monitor.monitorChanges();
        final Path expired = copy(EXPIRED, folder.resolve(EXPIRED));
        monitor.monitorChanges();
        verify(notifier, never()).notifyExpiringCertificate(any());

        monitor.monitor();

        assertThat(notifiedPaths(1), containsInAnyOrder(expired));
    }

    private CertificateMonitor monitor(final boolean changesMonitoringEnabled) {
        final String folders = String.join(",", Arrays.asList(folder.toString(), " ", otherFolder.toString()));
        return new CertificateMonitor(notifier, folders, MASKS, DAYS_TO_NOTIFY, changesMonitoringEnabled);
    }

    private Set<Path> notifiedPaths(final int times) {
        final ArgumentCaptor<PkiCertificate> captor = ArgumentCaptor.forClass(PkiCertificate.class);
        verify(notifier, atLeast(0)).notifyExpiringCertificate(captor.capture());
        final List<PkiCertificate> notified = captor.getAllValues();
        assertThat(String.format("Expected %d notifications, got %s", times, notified),
                notified.size() == times);
        return notified.stream().map(PkiCertificate::getPathToCertFile).collect(Collectors.toSet());
    }

    private static Path copy(final String resource, final Path target) throws IOException {
        try (InputStream stream = CertificateMonitorTest.class.getResourceAsStream("/certificates/" + resource)) {
            Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    private static void touch(final Path path, final long millis) throws IOException {
        Files.setLastModifiedTime(path, FileTime.fromMillis(millis));
    }
}
