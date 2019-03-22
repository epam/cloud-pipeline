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

package com.epam.dockercompscan.owasp;

import com.epam.dockercompscan.owasp.analyzer.AnalyzeEnabler;
import com.epam.dockercompscan.scan.domain.Dependency;
import org.owasp.dependencycheck.Engine;
import org.owasp.dependencycheck.exception.ExceptionCollection;
import org.owasp.dependencycheck.utils.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
public class DependencyCheckService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DependencyCheckService.class);

    @Value("#{'${enable.analyzers}'.split(',')}")
    private List<String> enabledAnalysers;

    public List<Dependency> runScan(File outputFolder) {
        LOGGER.debug("Start scanning: " + outputFolder.getName());
        Settings settings = populateSettings();
        Engine engine = new Engine(Engine.Mode.EVIDENCE_COLLECTION, settings);
        engine.scan(outputFolder);

        try {
            engine.analyzeDependencies();
        } catch (ExceptionCollection e) {
            LOGGER.warn("There were Exceptions while scan: " + e.getMessage(), e);
        } finally {
            engine.close();
            settings.cleanup();
            LOGGER.debug("Finish scanning: " + outputFolder.getName());
        }

        return Arrays.stream(engine.getDependencies())
                .filter(getDependencyFilter())
                .map(Dependency::convert)
                .collect(Collectors.toList());
    }

    private Predicate<org.owasp.dependencycheck.dependency.Dependency> getDependencyFilter() {
        return d -> d.getName() != null && d.getEcosystem() != null
                && (enabledAnalysers.stream()
                                    .map(AnalyzeEnabler::valueOf)
                                    .anyMatch(analyzer -> analyzer.getEcosystem().equals(d.getEcosystem())));
    }

    private Settings populateSettings() {
        Settings settings = new Settings();
        settings.setBooleanIfNotNull(Settings.KEYS.ANALYZER_EXPERIMENTAL_ENABLED, true);
        settings.setBooleanIfNotNull(Settings.KEYS.ANALYZER_RETIRED_ENABLED, true);
        settings.setBooleanIfNotNull(Settings.KEYS.ANALYZER_RETIRED_ENABLED, true);

        for (AnalyzeEnabler analyzer : AnalyzeEnabler.values()) {
            settings.setBoolean(analyzer.getValue(), false);
        }
        enabledAnalysers.forEach(enable -> settings.setBoolean(AnalyzeEnabler.valueOf(enable).getValue(), true));
        LOGGER.debug("Enable analyzers to scan: " + String.join(",", enabledAnalysers));
        return settings;
    }

}
