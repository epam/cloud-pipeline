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
 */

package com.epam.pipeline.vmmonitor.service.impl;

import com.epam.pipeline.entity.cluster.NodeInstance;
import com.epam.pipeline.vmmonitor.model.cert.PkiCertificate;
import com.epam.pipeline.vmmonitor.model.vm.VirtualMachine;
import com.epam.pipeline.vmmonitor.service.NotificationSender;
import com.epam.pipeline.vmmonitor.service.VMNotificationService;
import com.epam.pipeline.vo.notification.NotificationMessageVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.ResourceUtils;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class VMNotificationServiceImpl implements VMNotificationService {

    private final NotificationSender notificationSender;
    private final String missingNodeSubject;
    private final String missingLabelsSubject;
    private final String certificateExpirationSubject;
    private final String missingNodeTemplatePath;
    private final String missingLabelsTemplatePath;
    private final String certificateExpirationTemplatePath;
    private final String toUser;
    private final List<String> copyUsers;

    public VMNotificationServiceImpl(
            final NotificationSender notificationSender,
            @Value("${notification.missing-node.subject}") final String missingNodeSubject,
            @Value("${notification.missing-node.template}") final String missingNodeTemplatePath,
            @Value("${notification.missing-labels.subject}") final String missingLabelsSubject,
            @Value("${notification.missing-labels.template}") final String missingLabelsTemplatePath,
            @Value("${notification.missing-labels.subject}") final String certificateExpirationSubject,
            @Value("${notification.missing-labels.template}") final String certificateExpirationTemplatePath,
            @Value("${notification.to-user}") final String toUser,
            @Value("${notification.copy-users}") final String copyUsers) {
        this.notificationSender = notificationSender;
        this.missingNodeSubject = missingNodeSubject;
        this.missingLabelsSubject = missingLabelsSubject;
        this.missingNodeTemplatePath = missingNodeTemplatePath;
        this.missingLabelsTemplatePath = missingLabelsTemplatePath;
        this.certificateExpirationSubject = certificateExpirationSubject;
        this.certificateExpirationTemplatePath = certificateExpirationTemplatePath;
        this.toUser = toUser;
        this.copyUsers = Arrays.asList(copyUsers.split(","));
    }

    @Override
    public void notifyMissingNode(final VirtualMachine vm) {
        final Map<String, Object> parameters = new HashMap<>();
        addVmParameters(vm, parameters);
        log.debug("Sending missing node notification for VM {} {}", vm.getInstanceId(), vm.getCloudProvider());
        sendMessage(parameters, missingNodeSubject, missingNodeTemplatePath);
    }


    @Override
    public void notifyMissingLabels(final VirtualMachine vm,
                                    final NodeInstance node,
                                    final List<String> labels) {
        final Map<String, Object> parameters = new HashMap<>();
        addVmParameters(vm, parameters);
        addNodeParameters(node, parameters);
        parameters.put("missingLabels", labels.stream().collect(Collectors.joining(",")));
        log.debug("Sending missing labels notification for VM {} {}", vm.getInstanceId(), vm.getCloudProvider());
        sendMessage(parameters, missingLabelsSubject, missingLabelsTemplatePath);
    }

    @Override
    public void notifyExpiringCertificate(final PkiCertificate certificate) {
        final Map<String, Object> parameters = createCertParameters(certificate);
        log.debug("Sending {} certificate expiration notification", certificate.getX509Certificate().getSubjectDN());
        sendMessage(parameters, certificateExpirationSubject, certificateExpirationTemplatePath);
    }

    private void addNodeParameters(final NodeInstance node, final Map<String, Object> parameters) {
        parameters.put("nodeName", node.getName());
    }

    private void addVmParameters(final VirtualMachine vm, final Map<String, Object> parameters) {
        parameters.put("instanceId", vm.getInstanceId());
        parameters.put("privateIp", vm.getPrivateIp());
        parameters.put("provider", vm.getCloudProvider().name());
        parameters.put("instanceName", vm.getInstanceName());
    }

    private Map<String, Object> createCertParameters(final PkiCertificate certificate) {
        final Map<String, Object> parameters = new HashMap<>();
        final X509Certificate x509Cert = certificate.getX509Certificate();
        try {
            parameters.put("san", x509Cert.getSubjectAlternativeNames());
        } catch (CertificateParsingException e) {
            log.warn("Can't obtain SAN info from certificate!", e);
            parameters.put("san", StringUtils.EMPTY);
        }
        parameters.put("serialNumber", x509Cert.getSerialNumber());
        parameters.put("dn", x509Cert.getSubjectDN());
        parameters.put("issuer", x509Cert.getIssuerDN());
        parameters.put("notAfter", x509Cert.getNotAfter());
        parameters.put("filePath", certificate.getPathToCertFile());
        return parameters;
    }

    private void sendMessage(final Map<String, Object> parameters, final String subject, final String template) {
        NotificationMessageVO build = NotificationMessageVO.builder()
                .subject(subject)
                .body(getTemplateContent(template))
                .parameters(parameters)
                .toUser(toUser)
                .copyUsers(copyUsers).build();
        notificationSender.sendMessage(build);
    }

    private String getTemplateContent(final String template) {
        try (InputStream stream = openTemplateContent(template)) {
            return IOUtils.toString(stream, Charset.defaultCharset());
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read template file: " + template, e);
        }
    }

    private InputStream openTemplateContent(final String path) throws FileNotFoundException {
        if (path.startsWith(ResourceUtils.CLASSPATH_URL_PREFIX)) {
            final InputStream classPathResource = getClass().getResourceAsStream(path
                    .substring(ResourceUtils.CLASSPATH_URL_PREFIX.length()));
            Assert.notNull(classPathResource, String.format("Failed to resolve path: %s", path));
            return classPathResource;
        }
        if (path.startsWith(ResourceUtils.FILE_URL_PREFIX)) {
            return new FileInputStream(path.substring(ResourceUtils.FILE_URL_PREFIX.length()));
        }
        return new FileInputStream(path);
    }
}
