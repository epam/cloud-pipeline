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

import java.util.Optional;

import static com.epam.release.notes.agent.utils.VersionUtils.readVersionFromFile;
import static com.epam.release.notes.agent.utils.VersionUtils.updateVersionInFile;

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
    public Version fetchVersion() {
        final ApplicationInfo applicationInfo = executor.execute(cloudPipelineAPI.fetchVersion());
        return buildVersion(applicationInfo);
    }

    @Override
    public VersionStatus getVersionStatus() {
        final Version savedVersion = readVersionFromFile(versionFilePath);
        final Version currentVersion = fetchVersion();
        if (savedVersion.toString().equals(currentVersion.toString())) {
            return VersionStatus.NOT_CHANGED;
        } else if (!savedVersion.getMajor().equals(currentVersion.getMajor())) {
            return VersionStatus.MAJOR_CHANGED;
        }
        return VersionStatus.MINOR_CHANGED;
    }

    @Override
    public void storeVersion(Version version) {
        updateVersionInFile(version, versionFilePath);
    }

    public Version buildVersion(final ApplicationInfo applicationInfo) {
        return Optional.ofNullable(applicationInfo.getVersion())
                .map(Version::buildVersion)
                .orElseThrow(() -> new IllegalArgumentException("The application version is empty"));
    }
}
