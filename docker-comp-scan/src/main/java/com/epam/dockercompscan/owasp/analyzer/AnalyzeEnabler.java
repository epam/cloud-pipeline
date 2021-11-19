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

import org.owasp.dependencycheck.analyzer.CMakeAnalyzer;
import org.owasp.dependencycheck.analyzer.CocoaPodsAnalyzer;
import org.owasp.dependencycheck.analyzer.ComposerLockAnalyzer;
import org.owasp.dependencycheck.analyzer.JarAnalyzer;
import org.owasp.dependencycheck.analyzer.NodePackageAnalyzer;
import org.owasp.dependencycheck.analyzer.NspAnalyzer;
import org.owasp.dependencycheck.analyzer.NugetconfAnalyzer;
import org.owasp.dependencycheck.analyzer.NuspecAnalyzer;
import org.owasp.dependencycheck.analyzer.PythonDistributionAnalyzer;
import org.owasp.dependencycheck.analyzer.PythonPackageAnalyzer;
import org.owasp.dependencycheck.analyzer.RetireJsAnalyzer;
import org.owasp.dependencycheck.analyzer.RubyBundleAuditAnalyzer;
import org.owasp.dependencycheck.analyzer.RubyGemspecAnalyzer;
import org.owasp.dependencycheck.analyzer.SwiftPackageManagerAnalyzer;

public enum AnalyzeEnabler {

    ANALYZER_JAR("analyzer.jar.enabled", JarAnalyzer.DEPENDENCY_ECOSYSTEM),
    ANALYZER_ARCHIVE("analyzer.archive.enabled", AnalyzerConstants.SYSTEM),
    ANALYZER_NODE_PACKAGE("analyzer.node.package.enabled", NodePackageAnalyzer.DEPENDENCY_ECOSYSTEM),
    ANALYZER_PYTHON_DISTRIBUTION("analyzer.python.distribution.enabled",
            PythonDistributionAnalyzer.DEPENDENCY_ECOSYSTEM),
    ANALYZER_PYTHON_PACKAGE("analyzer.python.package.enabled", PythonPackageAnalyzer.DEPENDENCY_ECOSYSTEM),
    ANALYZER_AUTOCONF("analyzer.autoconf.enabled", AnalyzerConstants.SYSTEM),
    ANALYZER_CMAKE("analyzer.cmake.enabled", CMakeAnalyzer.DEPENDENCY_ECOSYSTEM),
    ANALYZER_NUSPEC("analyzer.nuspec.enabled", NuspecAnalyzer.DEPENDENCY_ECOSYSTEM),
    ANALYZER_NUGETCONF("analyzer.nugetconf.enabled", NugetconfAnalyzer.DEPENDENCY_ECOSYSTEM),
    ANALYZER_ASSEMBLY("analyzer.assembly.enabled", AnalyzerConstants.SYSTEM),
    ANALYZER_BUNDLE_AUDIT("analyzer.bundle.audit.enabled", RubyBundleAuditAnalyzer.DEPENDENCY_ECOSYSTEM),
    ANALYZER_OPENSSL("analyzer.openssl.enabled", AnalyzerConstants.SYSTEM),
    ANALYZER_COMPOSER_LOCK("analyzer.composer.lock.enabled", ComposerLockAnalyzer.DEPENDENCY_ECOSYSTEM),
    ANALYZER_NSP_PACKAGE("analyzer.nsp.package.enabled", NspAnalyzer.DEPENDENCY_ECOSYSTEM),
    ANALYZER_RETIREJS("analyzer.retirejs.filters", RetireJsAnalyzer.DEPENDENCY_ECOSYSTEM),
    ANALYZER_SWIFT_PACKAGE_MANAGER("analyzer.swift.package.manager.enabled",
            SwiftPackageManagerAnalyzer.DEPENDENCY_ECOSYSTEM),
    ANALYZER_COCOAPODS("analyzer.cocoapods.enabled", CocoaPodsAnalyzer.DEPENDENCY_ECOSYSTEM),
    ANALYZER_RUBY_GEMSPEC("analyzer.ruby.gemspec.enabled", RubyGemspecAnalyzer.DEPENDENCY_ECOSYSTEM),
    ANALYZER_CENTRAL("analyzer.central.enabled", AnalyzerConstants.SYSTEM),
    ANALYZER_NEXUS("analyzer.nexus.enabled", AnalyzerConstants.SYSTEM),
    ANALYZER_R_PACKAGE("analyzer.r.package.enabled", "R.Pkg"),
    ANALYZER_OS_PACKAGE("analyzer.os.enabled", "OS");


    private final String value;
    private final String ecosystem;

    AnalyzeEnabler(String value, String ecosystem) {
        this.value = value;
        this.ecosystem = ecosystem;
    }

    public String getEcosystem() {
        return ecosystem;
    }

    public String getValue() {
        return value;
    }
}
