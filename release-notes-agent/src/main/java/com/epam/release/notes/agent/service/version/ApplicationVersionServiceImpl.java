/*
 * Copyright 2021 EPAM Systems, Inc. (https://www.epam.com/)
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
package com.epam.release.notes.agent.service.version;

import com.epam.pipeline.client.pipeline.CloudPipelineAPI;
import com.epam.pipeline.client.pipeline.CloudPipelineApiBuilder;
import com.epam.pipeline.client.pipeline.CloudPipelineApiExecutor;
import com.epam.pipeline.entity.app.ApplicationInfo;
import com.epam.release.notes.agent.entity.version.Version;
import com.epam.release.notes.agent.entity.version.VersionStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Optional;

import static java.lang.String.format;

@Service
public class ApplicationVersionServiceImpl implements ApplicationVersionService {

    private final CloudPipelineAPI cloudPipelineAPI;
    private final CloudPipelineApiExecutor executor;

    @Value("${pipeline.api.version.file.path}")
    private String versionFilePath;

    public ApplicationVersionServiceImpl(final CloudPipelineApiBuilder builder,
                                         final CloudPipelineApiExecutor cloudPipelineApiExecutor) {
        this.cloudPipelineAPI = builder.buildClient();
        this.executor = cloudPipelineApiExecutor;
    }

    @Override
    public Version fetchCurrentVersion() {
        return buildVersion(
                executor.execute(cloudPipelineAPI.fetchVersion())
        );
    }

    @Override
    public Version loadPreviousVersion() {
        try {
            final Path path = Paths.get(versionFilePath);
            if (Files.notExists(path)) {
                Files.createFile(path);
            }
            final String savedVersion = Files.lines(path)
                    .findFirst()
                    .orElseGet(this::setCurrentVersion);
            return Version.buildVersion(savedVersion);
        } catch (IOException e) {
            throw new IllegalStateException(format("Unable to create or read file from path %s", versionFilePath), e);
        }
    }

    @Override
    public VersionStatus getVersionStatus(final Version old, final Version current) {
        if (old.toString().equals(current.toString())) {
            return VersionStatus.NOT_CHANGED;
        } else if (!old.getMajor().equals(current.getMajor())) {
            return VersionStatus.MAJOR_CHANGED;
        }
        return VersionStatus.MINOR_CHANGED;
    }

    @Override
    public void storeVersion(Version version) {
        updateVersionInFile(version);
    }

    private Version buildVersion(final ApplicationInfo applicationInfo) {
        return Optional.ofNullable(applicationInfo.getVersion())
                .map(Version::buildVersion)
                .orElseThrow(() -> new IllegalArgumentException("The application version is empty"));
    }

    void updateVersionInFile(final Version version) {
        try {
            Files.write(Paths.get(versionFilePath), Collections.singleton(version.toString()));
        } catch (IOException e) {
            throw new IllegalStateException(format("Unable to update version %s in file %s, cause: %s", version,
                    versionFilePath, e.getMessage()), e);
        }
    }

    private String setCurrentVersion() {
        final Version currentVersion = fetchCurrentVersion();
        storeVersion(currentVersion);
        return currentVersion.toString();
    }
}
