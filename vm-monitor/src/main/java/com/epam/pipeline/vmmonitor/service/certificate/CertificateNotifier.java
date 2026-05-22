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
import com.epam.pipeline.vmmonitor.service.notification.VMNotificationService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Slf4j
public class CertificateNotifier {

    private final VMNotificationService notificationService;
    private final String certificateExpirationSubject;
    private final String certificateExpirationTemplatePath;

    public CertificateNotifier(
            final VMNotificationService notificationService,
            final @Value("${notification.cert-expiration.subject}") String certificateExpirationSubject,
            final @Value("${notification.cert-expiration.template}") String certificateExpirationTemplate) {
        this.notificationService = notificationService;
        this.certificateExpirationSubject = certificateExpirationSubject;
        this.certificateExpirationTemplatePath = certificateExpirationTemplate;
    }

    public void notifyExpiringCertificate(final PkiCertificate certificate) {
        final Map<String, Object> parameters = createCertParameters(certificate);
        log.debug("Sending {} certificate expiration notification", certificate.getX509Certificate().getSubjectDN());
        notificationService.sendMessage(parameters, certificateExpirationSubject, certificateExpirationTemplatePath);
    }

    private Map<String, Object> createCertParameters(final PkiCertificate certificate) {
        final Map<String, Object> parameters = new HashMap<>();
        final X509Certificate x509Cert = certificate.getX509Certificate();
        parameters.put("san", formatSubjectAlternativeNames(x509Cert));
        parameters.put("serialNumber", x509Cert.getSerialNumber().toString());
        parameters.put("dn", x509Cert.getSubjectX500Principal().getName());
        parameters.put("issuer", x509Cert.getIssuerX500Principal().getName());
        parameters.put("notAfter", x509Cert.getNotAfter().toString());
        parameters.put("filePath", certificate.getPathToCertFile().toString());
        return parameters;
    }

    private String formatSubjectAlternativeNames(final X509Certificate x509Cert) {
        try {
            final Collection<List<?>> subjectAlternativeNames = x509Cert.getSubjectAlternativeNames();
            if (subjectAlternativeNames == null || subjectAlternativeNames.isEmpty()) {
                return StringUtils.EMPTY;
            }
            return subjectAlternativeNames.stream()
                    .filter(entry -> entry != null && entry.size() > 1 && entry.get(1) != null)
                    .map(entry -> entry.get(1).toString())
                    .collect(Collectors.joining(", "));
        } catch (CertificateParsingException e) {
            log.warn("Can't obtain SAN info from certificate!", e);
            return StringUtils.EMPTY;
        }
    }
}
