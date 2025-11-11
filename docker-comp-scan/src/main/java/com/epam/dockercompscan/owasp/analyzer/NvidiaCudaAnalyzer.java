/*
 * Copyright 2023 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.dockercompscan.owasp.analyzer;

import com.epam.dockercompscan.owasp.analyzer.filter.FilePathGlobFilter;
import org.owasp.dependencycheck.Engine;
import org.owasp.dependencycheck.analyzer.AbstractFileTypeAnalyzer;
import org.owasp.dependencycheck.analyzer.AnalysisPhase;
import org.owasp.dependencycheck.analyzer.Experimental;
import org.owasp.dependencycheck.analyzer.exception.AnalysisException;
import org.owasp.dependencycheck.dependency.Confidence;
import org.owasp.dependencycheck.dependency.Dependency;
import org.owasp.dependencycheck.dependency.EvidenceType;
import org.owasp.dependencycheck.exception.InitializationException;
import org.owasp.dependencycheck.utils.FileFilterBuilder;

import java.io.FileFilter;

@Experimental
public class NvidiaCudaAnalyzer extends AbstractFileTypeAnalyzer {
    public static final String DEPENDENCY_ECOSYSTEM = "Nvidia";
    public static final String NVIDIA_VERSION_ANALYZER_ENABLED = AnalyzeEnabler.ANALYZER_NVIDIA_PACKAGE.getValue();
    static final String DEPENDENCY_NAME = "NvidiaCuda";
    private static final String NVIDIA_VERSION_ANALYZER_NAME = "Nvidia Cuda Analyzer";
    private static final String NVIDIA_VERSION_PATH = "/**/usr/local/cuda-*/targets/x86_64-linux/lib/libcuda*.so.*";
    private static final String EVIDENCE_SOURCE = "cuda";
    private static final String EVIDENCE_VALUE = "found";
    private static final FilePathGlobFilter NAME_FILE_FILTER = new FilePathGlobFilter(NVIDIA_VERSION_PATH);
    private static final FileFilter FILTER = FileFilterBuilder.newInstance().addFileFilters(NAME_FILE_FILTER).build();
    private static final AnalysisPhase ANALYSIS_PHASE = AnalysisPhase.INFORMATION_COLLECTION;

    @Override
    protected FileFilter getFileFilter() {
        return FILTER;
    }

    @Override
    protected void prepareFileTypeAnalyzer(final Engine engine) throws InitializationException {
        // no-op
    }

    @Override
    protected void analyzeDependency(final Dependency dependency, final Engine engine) throws AnalysisException {
        dependency.setEcosystem(DEPENDENCY_ECOSYSTEM);
        dependency.setName(DEPENDENCY_NAME);
        dependency.addEvidence(EvidenceType.PRODUCT, EVIDENCE_SOURCE, DEPENDENCY_NAME, EVIDENCE_VALUE, Confidence.HIGH);
    }

    @Override
    protected String getAnalyzerEnabledSettingKey() {
        return NVIDIA_VERSION_ANALYZER_ENABLED;
    }

    @Override
    public String getName() {
        return NVIDIA_VERSION_ANALYZER_NAME;
    }

    @Override
    public AnalysisPhase getAnalysisPhase() {
        return ANALYSIS_PHASE;
    }
}
