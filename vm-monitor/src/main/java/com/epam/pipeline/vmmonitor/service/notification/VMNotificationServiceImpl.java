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

package com.epam.pipeline.vmmonitor.service.notification;

import com.epam.pipeline.vo.notification.NotificationMessageVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.ResourceUtils;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class VMNotificationServiceImpl implements VMNotificationService {

    private final NotificationSender notificationSender;
    private final String toUser;
    private final List<String> copyUsers;
    private final String platformName;

    public VMNotificationServiceImpl(
            final NotificationSender notificationSender,
            @Value("${cloud.pipeline.platform.name}") final String platformName,
            @Value("${notification.to-user}") final String toUser,
            @Value("${notification.copy-users}") final String copyUsers) {
        this.notificationSender = notificationSender;
        this.platformName = platformName;
        this.toUser = toUser;
        this.copyUsers = Arrays.asList(copyUsers.split(","));
    }

    @Override
    public void sendMessage(final Map<String, Object> parameters, final String subject, final String template) {
        NotificationMessageVO build = NotificationMessageVO.builder()
                .subject(subject)
                .body(getTemplateContent(template))
                .parameters(withCommonParams(parameters))
                .toUser(toUser)
                .copyUsers(copyUsers).build();
        notificationSender.sendMessage(build);
    }

    private Map<String, Object> withCommonParams(Map<String, Object> parameters) {
        final Map<String, String> commonParams = Collections.singletonMap("platformName", this.platformName);
        return Stream.concat(parameters.entrySet().stream(), commonParams.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (val1, val2) -> val1));
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
