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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.owasp.dependencycheck.analyzer.exception.AnalysisException;
import org.owasp.dependencycheck.dependency.Dependency;

public class OSAnalyzerTest {

    private OSVersionAnalyzer osVersionAnalyzer;

    @Before
    public void setUp() {
        osVersionAnalyzer = new OSVersionAnalyzer();
    }

    @Test
    public void analyzeDependencyTestPositiveOsRelease() throws AnalysisException {
        Dependency dependency = new Dependency();
        dependency.setActualFilePath(this.getClass().getClassLoader().getResource(
                "owasp/analyzer/positive/os/os-case1/os-release").getPath());
        osVersionAnalyzer.analyzeDependency(dependency, null);

        Assert.assertEquals(OSVersionAnalyzer.DEPENDENCY_ECOSYSTEM, dependency.getEcosystem());
        Assert.assertEquals("fedora", dependency.getName());
        Assert.assertEquals("31", dependency.getVersion());
    }

    @Test
    public void analyzeDependencyTestPositiveOsRelease2() throws AnalysisException {
        Dependency dependency = new Dependency();
        dependency.setActualFilePath(this.getClass().getClassLoader().getResource(
                "owasp/analyzer/positive/os/os-case2/os-release").getPath());
        osVersionAnalyzer.analyzeDependency(dependency, null);

        Assert.assertEquals(OSVersionAnalyzer.DEPENDENCY_ECOSYSTEM, dependency.getEcosystem());
        Assert.assertEquals("ubuntu", dependency.getName());
        Assert.assertEquals("18.04", dependency.getVersion());
    }

    @Test
    public void analyzeDependencyTestPositiveCentosRelease() throws AnalysisException {
        Dependency dependency = new Dependency();
        dependency.setActualFilePath(this.getClass().getClassLoader().getResource(
                "owasp/analyzer/positive/os/centos-release").getPath());
        osVersionAnalyzer.analyzeDependency(dependency, null);

        Assert.assertEquals(OSVersionAnalyzer.DEPENDENCY_ECOSYSTEM, dependency.getEcosystem());
        Assert.assertEquals("centos", dependency.getName());
        Assert.assertEquals("6.10", dependency.getVersion());
    }

    @Test
    public void analyzeDependencyTestNegativeOsRelease() throws AnalysisException {
        Dependency dependency = new Dependency();
        dependency.setActualFilePath(this.getClass().getClassLoader().getResource(
                "owasp/analyzer/negative/os-release").getPath());
        osVersionAnalyzer.analyzeDependency(dependency, null);

        Assert.assertNull(dependency.getEcosystem());
    }

    @Test
    public void analyzeDependencyTestNegativeCentosRelease() throws AnalysisException {
        Dependency dependency = new Dependency();
        dependency.setActualFilePath(this.getClass().getClassLoader().getResource(
                "owasp/analyzer/negative/centos-release").getPath());
        osVersionAnalyzer.analyzeDependency(dependency, null);

        Assert.assertNull(dependency.getEcosystem());
    }

}