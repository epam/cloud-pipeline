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

package com.epam.dockercompscan.owasp.analyzer;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.NameFileFilter;
import org.owasp.dependencycheck.Engine;
import org.owasp.dependencycheck.analyzer.AbstractFileTypeAnalyzer;
import org.owasp.dependencycheck.analyzer.AnalysisPhase;
import org.owasp.dependencycheck.analyzer.Experimental;
import org.owasp.dependencycheck.analyzer.JarAnalyzer;
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
public class RPackageAnalyzer extends AbstractFileTypeAnalyzer {

    public static final String DEPENDENCY_ECOSYSTEM = "R.Pkg";

    public static final String ANALYZER_R_PACKAGE_ENABLED = AnalyzeEnabler.ANALYZER_R_PACKAGE.getValue();

    /**
     * The name of the analyzer.
     */
    private static final String ANALYZER_NAME = "R Package Analyzer";

    /**
     * The phase that this analyzer is intended to run in.
     */
    private static final AnalysisPhase ANALYSIS_PHASE = AnalysisPhase.INFORMATION_COLLECTION;

    /**
     * The set of file extensions supported by this analyzer.
     */
    private static final String[] EXTENSIONS = {"DESCRIPTION"};

    /**
     * Name of R package description files to analyze.
     */
    private static final String DESCRIPTION = "DESCRIPTION";

    /**
     * Filter that detects files named "DESCRIPTION".
     */
    private static final NameFileFilter DESCRIPTION_FILTER = new NameFileFilter(DESCRIPTION);

    /**
     * The file filter used to determine which files this analyzer supports.
     */
    private static final FileFilter FILTER = FileFilterBuilder.newInstance().addFileFilters(DESCRIPTION_FILTER)
            .addExtensions(EXTENSIONS).build();

    /**
     * The pattern used to determine that DESCRIPTION file is from R package.
     */
    private static final Pattern BUILT_FIELD_PATTERN = Pattern.compile(".*Built: R .*");

    private static final Pattern VERSION_PATTERN = Pattern.compile(".*Version: ([^\n]*)\n.*");
    private static final Pattern DESCRIPTION_PATTERN = Pattern.compile(".*Description: ([^\n]*)\n.*");
    private static final Pattern PACKAGE_TITLE_PATTERN = Pattern.compile(".*Package: ([^\n]*)\n.*");
    private static final Pattern AUTHOR_PATTERN = Pattern.compile(".*Author: ([^\n]*)\n.*");

    @Override
    protected FileFilter getFileFilter() {
        return FILTER;
    }

    @Override
    protected void prepareFileTypeAnalyzer(Engine engine) throws InitializationException {
    }

    @Override
    protected String getAnalyzerEnabledSettingKey() {
        return ANALYZER_R_PACKAGE_ENABLED;
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
    protected void analyzeDependency(Dependency dependency, Engine engine) throws AnalysisException {
        final File actualFile = dependency.getActualFile();
        try {
            String contents = FileUtils.readFileToString(actualFile, Charset.defaultCharset()).trim();
            if (BUILT_FIELD_PATTERN.matcher(contents).find()) {
                dependency.setEcosystem(DEPENDENCY_ECOSYSTEM);
                collectDescriptionData(dependency, actualFile.getName(), contents);
            }
        } catch (IOException e) {
            throw new AnalysisException("Problem occurred while reading dependency file.", e);
        }
    }

    private void collectDescriptionData(Dependency dependency, String source, String contents) {
        if (!contents.isEmpty()) {
            contents = contents.replaceAll("\n\\s+", " ");
            gatherEvidence(dependency, EvidenceType.VERSION, VERSION_PATTERN, contents,
                    source, "Version", Confidence.HIGH);
            gatherEvidence(dependency, EvidenceType.PRODUCT, PACKAGE_TITLE_PATTERN, contents,
                    source, "Package", Confidence.HIGH);
            gatherEvidence(dependency, EvidenceType.VENDOR, AUTHOR_PATTERN, contents,
                    source, "Author", Confidence.LOW);
            addSummaryInfo(dependency, contents, source);
            if (dependency.getName() != null && dependency.getVersion() != null) {
                dependency.setDisplayFileName(dependency.getName() + ":" + dependency.getVersion());
            }
        }
    }

    /**
     * Adds summary information to the dependency
     *
     * @param dependency the dependency being analyzed
     * @param contents the data being analyzed
     * @param source the source name to use when recording the evidence
     */
    private void addSummaryInfo(Dependency dependency, String contents, String source) {
        final Matcher matcher = RPackageAnalyzer.DESCRIPTION_PATTERN.matcher(contents);
        final boolean found = matcher.find();
        if (found) {
            JarAnalyzer.addDescription(dependency, matcher.group(1), source, "Description");
        }
    }

    /**
     * Gather evidence from a Python source file using the given string
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
    private void gatherEvidence(Dependency dependency, EvidenceType type, Pattern pattern, String contents,
                                String source, String name, Confidence confidence) {
        final Matcher matcher = pattern.matcher(contents);
        final boolean found = matcher.find();
        if (found) {
            if (type == EvidenceType.VERSION) {
                dependency.setVersion(matcher.group(1));
            }else if (type == EvidenceType.PRODUCT) {
                dependency.setName(matcher.group(1));
            }
            dependency.addEvidence(type, source, name, matcher.group(1), confidence);
        }
    }
}
