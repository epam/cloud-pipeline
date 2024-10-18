/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
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

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Experimental
public class OSVersionAnalyzer extends AbstractFileTypeAnalyzer {

    public static final String DEPENDENCY_ECOSYSTEM = "OS";

    public static final String ANALYZER_OS_ENABLED = AnalyzeEnabler.ANALYZER_OS_PACKAGE.getValue();

    /**
     * The name of the analyzer.
     */
    private static final String ANALYZER_NAME = "OS Version Analyzer";

    /**
     * The phase that this analyzer is intended to run in.
     */
    private static final AnalysisPhase ANALYSIS_PHASE = AnalysisPhase.INFORMATION_COLLECTION;

    /**
     * Names of OS version files to analyze.
     */
    public static final String OS_RELEASE = "etc/os-release";
    public static final String OS_RELEASE_USR_LIB = "usr/lib/os-release";
    public static final String REDHAT_RELEASE = "etc/redhat-release";
    public static final String SYSTEM_RELEASE = "etc/system-release";
    public static final String CENTOS_RELEASE = "etc/centos-release";

    private static final Pattern VERSION_PATTERN = Pattern.compile(".*\nVERSION_ID=\"?([^\n\"]*)\"?\n.*");
    private static final Pattern NAME_TITLE_PATTERN = Pattern.compile(".*\nID=\"?([^\n\"]*)\"?\n.*");
    private static final Pattern SYSTEM_NAME_TITLE_PATTERN = Pattern.compile("([^ ]+).*");
    private static final Pattern SYSTEM_VERSION_PATTERN = Pattern.compile("([\\d\\.\\-_]+)");
    public static final String OS_RELEASE_FILE = "os-release";

    @Override
    protected FileFilter getFileFilter() {
        final String searchBasePath = getSettings().getString(AnalyzerConstants.BASE_SEARCH_PATH_SETTING);
        final FilePathGlobFilter nameFileFilter = buildFilter(searchBasePath);
        return FileFilterBuilder.newInstance().addFileFilters(nameFileFilter).build();
    }

    @Override
    protected void prepareFileTypeAnalyzer(final Engine engine) throws InitializationException {
    }

    @Override
    protected String getAnalyzerEnabledSettingKey() {
        return ANALYZER_OS_ENABLED;
    }

    @Override
    public String getName() {
        return ANALYZER_NAME;
    }

    @Override
    public AnalysisPhase getAnalysisPhase() {
        return ANALYSIS_PHASE;
    }

    @Override
    protected void analyzeDependency(final Dependency dependency, final Engine engine) throws AnalysisException {
        final File actualFile = dependency.getActualFile();
        try {
            final String contents = FileUtils.readFileToString(actualFile, Charset.defaultCharset()).trim();
            collectDescriptionData(dependency, actualFile.getName(), contents);
        } catch (IOException e) {
            throw new AnalysisException("Problem occurred while reading dependency file.", e);
        }
    }

    private void collectDescriptionData(final Dependency dependency, final String source, final String contents) {
        if (contents.isEmpty()) {
            return;
        }
        final String cleanContent = contents.replaceAll("\n\\s+", " ");
        final Pattern namePattern = OS_RELEASE_FILE.equals(source) ? NAME_TITLE_PATTERN : SYSTEM_NAME_TITLE_PATTERN;
        final Pattern versionPattern = OS_RELEASE_FILE.equals(source) ? VERSION_PATTERN : SYSTEM_VERSION_PATTERN;

        gatherEvidence(dependency, EvidenceType.VERSION, versionPattern, cleanContent,
                source, "Version", Confidence.HIGH);
        gatherEvidence(dependency, EvidenceType.PRODUCT, namePattern, cleanContent,
                source, "Name", Confidence.HIGH);
        dependency.setEcosystem(DEPENDENCY_ECOSYSTEM);
        if (dependency.getName() != null && dependency.getVersion() != null) {
            dependency.setDisplayFileName(dependency.getName() + ":" + dependency.getVersion());
        }
    }

    /**
     * Gather evidence from os-release file using the given string
     * assignment regex pattern.
     *
     * @param dependency the dependency that is being analyzed
     * @param type the type of evidence
     * @param pattern to scan contents with
     * @param contents of Python source file
     * @param source for storing evidence
     * @param name of evidence
     * @param confidence in evidence
     */
    private void gatherEvidence(final Dependency dependency, final EvidenceType type, final Pattern pattern,
                                final String contents, final String source, String name, final Confidence confidence) {
        final Matcher matcher = pattern.matcher(contents);
        final boolean found = matcher.find();
        if (found) {
            if (type == EvidenceType.VERSION) {
                dependency.setVersion(matcher.group(1).toLowerCase());
            }else if (type == EvidenceType.PRODUCT) {
                dependency.setName(matcher.group(1).toLowerCase());
            }
            dependency.addEvidence(type, source, name, matcher.group(1), confidence);
        }
    }

    public FilePathGlobFilter buildFilter(final String searchBasePath) {
        return new FilePathGlobFilter(
                buildSearchPath(searchBasePath, OS_RELEASE),
                buildSearchPath(searchBasePath, OS_RELEASE_USR_LIB),
                buildSearchPath(searchBasePath, REDHAT_RELEASE),
                buildSearchPath(searchBasePath, SYSTEM_RELEASE),
                buildSearchPath(searchBasePath, CENTOS_RELEASE)
        );
    }

    private String buildSearchPath(final String searchBasePath, final String pattern) {
        return FilenameUtils.concat(searchBasePath, pattern);
    }
}
