/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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
package com.epam.pipeline.autotests;

import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.NotifierLog;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.VmMonitorLog;
import com.epam.pipeline.autotests.utils.VmMonitorRecipients;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.codeborne.selenide.Selenide.open;
import static com.epam.pipeline.autotests.utils.Utils.sleep;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * 2870_1: the VM monitor emails the admin about a certificate that expires within a week.
 * <p>
 * The test writes a certificate that expires in 3 days into a folder the VM monitor scans: a subfolder of
 * /opt/common/pki, which run-e2e.sh mounts writable into the runner container (e2e.ui.pki.test.dir). The VM monitor
 * checks the new and changed certificate files every minute, so the email follows within minutes, not at its daily
 * scan.
 * <p>
 * The email is not read from a mailbox. The test reads the VM monitor's log, which shows that it checked the file and
 * sent the notification to the API, and the notifier's log, which shows the subject and the recipients of the email,
 * and that the mail server accepted it. run-e2e.sh mounts both log directories into the runner container read-only.
 * <p>
 * The certificate is found in the VM monitor's log by its file name and subject, which are new, so no time window is
 * needed. The email's subject, "Certificate expires soon", is the same for every certificate. So the test takes the
 * emails with that subject that were not in the notifier's log before it wrote the certificate, and expects one for
 * each notification the VM monitor sent meanwhile: usually only the one of this certificate.
 */
public class CertificateExpirationNotificationTest extends AbstractBfxPipelineTest {

    // The subject of the CERTIFICATE-EXPIRATION email of the VM monitor: "[<platform name>] Certificate expires soon"
    private static final String SUBJECT = "Certificate expires soon";
    private static final int VALIDITY_DAYS = 3;
    private static final String NAME_PREFIX = "cp-e2e-2870-";
    private static final String KEY_ALIAS = "cp-e2e";
    // Both services log a check every 30-60 s
    private static final Duration LOG_MAX_AGE = Duration.ofMinutes(2);
    // Old enough to cover the log files that are written while the test runs
    private static final Duration BASELINE_PERIOD = Duration.ofMinutes(10);
    private static final Duration CHANGES_CHECK_PERIOD = Duration.ofMinutes(10);
    private static final Duration FORMAT_CHECK_PERIOD = Duration.ofDays(1);
    // The VM monitor checks every minute, but its one scheduler thread may be busy with another monitor
    private static final Duration REPORT_TIMEOUT = Duration.ofMinutes(10);
    private static final Duration EMAIL_TIMEOUT = Duration.ofMinutes(10);
    private static final Duration LOG_POLL = Duration.ofSeconds(15);
    // The notifier checks its queue every 30 s, and sends at most 5 messages each time
    private static final Duration QUIET_PERIOD = Duration.ofSeconds(75);
    private static final Duration QUIET_TIMEOUT = Duration.ofMinutes(10);
    private static final Duration KEYTOOL_TIMEOUT = Duration.ofMinutes(2);

    private Path certificateFile;

    @Test
    @TestCase(value = "2870_1")
    public void notificationIsSentForCertificateExpiringWithinAWeek() {
        // Checked before the certificate is written
        final Path pkiDir = pkiTestDir();
        final long now = System.currentTimeMillis();
        final VmMonitorLog vmMonitorLog = new VmMonitorLog(C.VM_MONITOR_LOGS_DIR)
                .assertCurrent(LOG_MAX_AGE)
                .assertChangesChecked(now - CHANGES_CHECK_PERIOD.toMillis());
        final NotifierLog notifierLog = new NotifierLog(C.NOTIFIER_LOGS_DIR)
                .assertCurrent(LOG_MAX_AGE)
                .assertSubjectsLogged(now - FORMAT_CHECK_PERIOD.toMillis());
        open(C.ROOT_ADDRESS);
        final VmMonitorRecipients recipients = VmMonitorRecipients.load("2870_1");

        final long since = now - BASELINE_PERIOD.toMillis();
        waitForQuietLogs(vmMonitorLog, notifierLog, since);
        final Set<String> reportsBefore = vmMonitorLog.reports(since).stream()
                .map(report -> report.line)
                .collect(Collectors.toSet());
        final Set<Long> emailsBefore = notifierLog.emailsWithSubjectEndingWith(SUBJECT, since).keySet();

        // Steps 1-2
        final String name = NAME_PREFIX + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + "-"
                + UUID.randomUUID().toString().substring(0, 8);
        certificateFile = createCertificate(pkiDir, name);
        final X509Certificate certificate = readCertificate(certificateFile);
        System.out.printf("2870_1: certificate %s (%s), expires %s%n", certificateFile.getFileName(),
                certificate.getSubjectX500Principal().getName(), certificate.getNotAfter());

        // Step 3
        final VmMonitorLog.CertificateCheck check = waitForReport(vmMonitorLog, certificateFile.getFileName()
                .toString(), name, since);
        printEvidence("VM monitor", check.lines);
        assertTrue(check.report.sent, format("The VM monitor did not send the notification about %s to the API, or "
                + "the API did not accept it: %s", name, check.report.lines));

        // Step 4
        final Map<Long, NotifierLog.Email> emails = waitForEmails(notifierLog, emailsBefore, vmMonitorLog,
                reportsBefore, since);
        printEvidence("notifier", emails.values().stream()
                .flatMap(email -> email.lines.stream())
                .collect(Collectors.toList()));
        final List<VmMonitorLog.Report> reports = newReports(vmMonitorLog, reportsBefore, since);
        if (reports.size() > 1) {
            System.out.printf("2870_1: the VM monitor sent %d notifications meanwhile, which have the same subject: "
                    + "%s%n", reports.size(), reports.stream().map(report -> report.subject)
                    .collect(Collectors.toList()));
        }
        emails.values().forEach(recipients::assertSentTo);
    }

    @AfterClass(alwaysRun = true)
    public void deleteCertificate() throws IOException {
        if (certificateFile != null) {
            // A certificate left there would be reported every day, also after it expires
            Files.deleteIfExists(certificateFile);
        }
    }

    private static Path pkiTestDir() {
        assertTrue(!C.PKI_TEST_DIR.trim().isEmpty(), "No certificates folder: set e2e.ui.pki.test.dir (run-e2e.sh "
                + "mounts /opt/common/pki/e2e-tests at /pki-test)");
        final Path dir = Paths.get(C.PKI_TEST_DIR);
        assertTrue(Files.isDirectory(dir) && Files.isWritable(dir), format("%s is not a folder the tests user can "
                + "write to: the certificates folder is not mounted", dir));
        return dir;
    }

    /**
     * Writes a self-signed certificate that expires in {@link #VALIDITY_DAYS} days into the folder, with the subject
     * {@code CN=<name>}, as {@code <name>.pem}. The file appears at once: it is written under another name that the
     * VM monitor does not take for a certificate, and then renamed, so the VM monitor never reads a part of it.
     */
    private static Path createCertificate(final Path dir, final String name) {
        final Path temporary = dir.resolve("." + name + ".tmp");
        // ".pem" is among the certificate file masks of the VM monitor by default: of its jar and of a deployment
        final Path certificate = dir.resolve(name + ".pem");
        Path keystoreDir = null;
        try {
            keystoreDir = Files.createTempDirectory(NAME_PREFIX);
            final String keystore = keystoreDir.resolve("keystore.p12").toString();
            final String password = UUID.randomUUID().toString();
            keytool("-genkeypair", "-alias", KEY_ALIAS, "-keyalg", "RSA", "-keysize", "2048", "-dname", "CN=" + name,
                    "-validity", String.valueOf(VALIDITY_DAYS), "-keystore", keystore, "-storetype", "PKCS12",
                    "-storepass", password, "-keypass", password);
            keytool("-exportcert", "-rfc", "-alias", KEY_ALIAS, "-keystore", keystore, "-storetype", "PKCS12",
                    "-storepass", password, "-file", temporary.toString());
            Files.move(temporary, certificate, StandardCopyOption.ATOMIC_MOVE);
            return certificate;
        } catch (IOException e) {
            throw new UncheckedIOException(format("Unable to write the certificate %s", certificate), e);
        } finally {
            deleteQuietly(temporary);
            if (keystoreDir != null) {
                try (Stream<Path> paths = Files.walk(keystoreDir)) {
                    paths.sorted(Comparator.reverseOrder())
                            .forEach(CertificateExpirationNotificationTest::deleteQuietly);
                } catch (IOException e) {
                    System.out.printf("2870_1: unable to delete %s: %s%n", keystoreDir, e.getMessage());
                }
            }
        }
    }

    /**
     * Runs the keytool of the JDK the tests run with.
     */
    private static void keytool(final String... arguments) throws IOException {
        final Path javaHome = Paths.get(System.getProperty("java.home"));
        final List<String> command = new ArrayList<>();
        command.add(Stream.of(javaHome.resolve("bin/keytool"), javaHome.resolve("../bin/keytool"))
                .filter(Files::isExecutable)
                .findFirst()
                .map(Path::toString)
                .orElse("keytool"));
        command.addAll(Arrays.asList(arguments));
        final Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        // keytool fails instead of waiting for an answer if it ever asks for one
        process.getOutputStream().close();
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (InputStream stream = process.getInputStream()) {
            final byte[] buffer = new byte[4096];
            for (int read = stream.read(buffer); read >= 0; read = stream.read(buffer)) {
                output.write(buffer, 0, read);
            }
            if (!process.waitFor(KEYTOOL_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                fail(format("keytool %s did not finish in %d min", arguments[0], KEYTOOL_TIMEOUT.toMinutes()));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while keytool runs", e);
        }
        assertEquals(process.exitValue(), 0, format("keytool %s failed: %s", arguments[0],
                new String(output.toByteArray(), StandardCharsets.UTF_8)));
    }

    private static X509Certificate readCertificate(final Path file) {
        try (InputStream stream = Files.newInputStream(file)) {
            final X509Certificate certificate = (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(stream);
            final long left = certificate.getNotAfter().getTime() - System.currentTimeMillis();
            assertTrue(left > TimeUnit.DAYS.toMillis(VALIDITY_DAYS - 1) && left <= TimeUnit.DAYS.toMillis(VALIDITY_DAYS),
                    format("The certificate %s expires %s, not in %d days", file, certificate.getNotAfter(),
                            VALIDITY_DAYS));
            return certificate;
        } catch (IOException e) {
            throw new UncheckedIOException(format("Unable to read %s", file), e);
        } catch (CertificateException e) {
            throw new IllegalStateException(format("%s is not a certificate", file), e);
        }
    }

    /**
     * Waits until the VM monitor's log shows the notification about the certificate, and whether the API accepted it.
     */
    private static VmMonitorLog.CertificateCheck waitForReport(final VmMonitorLog vmMonitorLog, final String fileName,
                                                               final String name, final long since) {
        final long deadline = System.currentTimeMillis() + REPORT_TIMEOUT.toMillis();
        while (true) {
            final VmMonitorLog.CertificateCheck check = vmMonitorLog.check(fileName, name, since);
            if (check.checked && check.read && check.report != null && check.report.sent != null) {
                return check;
            }
            if (System.currentTimeMillis() > deadline) {
                printEvidence("VM monitor", check.lines);
                if (!check.checked) {
                    fail(format("The VM monitor did not check %s in %d min: it does not scan the folder of "
                            + "e2e.ui.pki.test.dir (a subfolder of /opt/common/pki), or does not run on the node of "
                            + "run-e2e.sh", fileName, REPORT_TIMEOUT.toMinutes()));
                }
                if (!check.read) {
                    fail(format("The VM monitor found %s, but did not read the certificate in %d min", fileName,
                            REPORT_TIMEOUT.toMinutes()));
                }
                fail(format(check.report == null
                        ? "The VM monitor read %s, but did not report it in %d min: a certificate that expires in "
                                + VALIDITY_DAYS + " days must be within CP_VM_MONITOR_PKI_EXPIRATION_DAYS"
                        : "The VM monitor reported %s, but the log does not show in %d min whether the API "
                                + "accepted it", fileName, REPORT_TIMEOUT.toMinutes()));
            }
            sleep(LOG_POLL.toMillis(), MILLISECONDS);
        }
    }

    /**
     * Waits until the emails of the notifications that the VM monitor sent before the test are sent: until the VM
     * monitor has no notification in progress, the notifier log has at least as many emails with the subject as the
     * VM monitor log has notifications the API accepted (both logs start a new file every day, and the same files are
     * read), and no new email appears for {@link #QUIET_PERIOD}. Otherwise such an email, sent after the test took its
     * baseline, would be taken for the email of the test, e.g. after the full scan at 00:00, which reports every
     * certificate that expires soon, and whose emails may wait in the notifier's queue behind other messages.
     */
    private static void waitForQuietLogs(final VmMonitorLog vmMonitorLog, final NotifierLog notifierLog,
                                         final long since) {
        final long deadline = System.currentTimeMillis() + QUIET_TIMEOUT.toMillis();
        Set<Long> emails = notifierLog.emailsWithSubjectEndingWith(SUBJECT, since).keySet();
        long quietSince = System.currentTimeMillis();
        while (true) {
            final List<VmMonitorLog.Report> reports = vmMonitorLog.reports(since);
            final boolean inProgress = reports.stream().anyMatch(report -> report.sent == null);
            final long accepted = reports.stream().filter(report -> Boolean.TRUE.equals(report.sent)).count();
            final boolean queued = emails.size() < accepted;
            if (inProgress || queued) {
                quietSince = System.currentTimeMillis();
            } else if (System.currentTimeMillis() - quietSince >= QUIET_PERIOD.toMillis()) {
                return;
            }
            if (System.currentTimeMillis() > deadline) {
                fail(format("The emails '%s' did not settle in %d min: the VM monitor log has %d notification(s) the "
                        + "API accepted (%s in progress), and the notifier log %d email(s)", SUBJECT,
                        QUIET_TIMEOUT.toMinutes(), accepted, inProgress ? "some" : "none", emails.size()));
            }
            sleep(LOG_POLL.toMillis(), MILLISECONDS);
            final Set<Long> current = notifierLog.emailsWithSubjectEndingWith(SUBJECT, since).keySet();
            if (!current.equals(emails)) {
                emails = current;
                quietSince = System.currentTimeMillis();
            }
        }
    }

    /**
     * The notifications the VM monitor sent to the API, and the API accepted, after the test started.
     */
    private static List<VmMonitorLog.Report> newReports(final VmMonitorLog vmMonitorLog,
                                                        final Set<String> reportsBefore, final long since) {
        return vmMonitorLog.reports(since).stream()
                .filter(report -> !reportsBefore.contains(report.line))
                .filter(report -> Boolean.TRUE.equals(report.sent))
                .collect(Collectors.toList());
    }

    /**
     * Waits until the notifier's log has one new email with the subject for each notification that the VM monitor
     * sent meanwhile, all of them accepted by the mail server, and returns them. The notifier sends an email only
     * after the API has queued it, so there are not more emails than notifications.
     */
    private static Map<Long, NotifierLog.Email> waitForEmails(final NotifierLog notifierLog,
                                                              final Set<Long> emailsBefore,
                                                              final VmMonitorLog vmMonitorLog,
                                                              final Set<String> reportsBefore, final long since) {
        final long deadline = System.currentTimeMillis() + EMAIL_TIMEOUT.toMillis();
        while (true) {
            final int expected = newReports(vmMonitorLog, reportsBefore, since).size();
            final Map<Long, NotifierLog.Email> emails = notifierLog.emailsWithSubjectEndingWith(SUBJECT, since);
            emails.keySet().removeAll(emailsBefore);
            if (emails.size() == expected && emails.values().stream().allMatch(email -> email.sent)) {
                return emails;
            }
            if (System.currentTimeMillis() > deadline) {
                printEvidence("notifier", emails.values().stream()
                        .flatMap(email -> email.lines.stream())
                        .collect(Collectors.toList()));
                notifierLog.assertSubjectsLogged(since);
                fail(format("In %d min, the notifier log has %d new email(s) '%s' (message ids %s, sent: %d, dropped: "
                        + "%d), and the VM monitor sent %d notification(s)", EMAIL_TIMEOUT.toMinutes(),
                        emails.size(), SUBJECT, emails.keySet(),
                        emails.values().stream().filter(email -> email.sent).count(),
                        emails.values().stream().filter(email -> email.dropped).count(), expected));
            }
            sleep(LOG_POLL.toMillis(), MILLISECONDS);
        }
    }

    private static void deleteQuietly(final Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            System.out.printf("2870_1: unable to delete %s: %s%n", path, e.getMessage());
        }
    }

    /**
     * Prints a service's log lines about the certificate or its email into the console log of the run, as the
     * evidence of the case.
     */
    private static void printEvidence(final String service, final List<String> lines) {
        System.out.printf("2870_1: %d line(s) of the %s log%n", lines.size(), service);
        lines.forEach(line -> System.out.println("    " + line));
    }
}
