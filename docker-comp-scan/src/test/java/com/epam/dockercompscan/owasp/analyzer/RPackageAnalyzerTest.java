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

public class RPackageAnalyzerTest {

    private RPackageAnalyzer rPackageAnalyzer;

    @Before
    public void setUp() {
        rPackageAnalyzer = new RPackageAnalyzer();
    }

    @Test
    public void analyzeDependencyTestPositive() throws AnalysisException {
        Dependency dependency = new Dependency();
        dependency.setActualFilePath(this.getClass().getClassLoader().getResource(
                "owasp/analyzer/positive/DESCRIPTION").getPath());
        rPackageAnalyzer.analyzeDependency(dependency, null);

        Assert.assertEquals(RPackageAnalyzer.DEPENDENCY_ECOSYSTEM, dependency.getEcosystem());
        Assert.assertEquals("PositiveTest", dependency.getName());
        Assert.assertEquals("1.0", dependency.getVersion());
    }

    @Test
    public void analyzeDependencyTestNegative() throws AnalysisException {
        Dependency dependency = new Dependency();
        dependency.setActualFilePath(this.getClass().getClassLoader().getResource(
                "owasp/analyzer/negative/DESCRIPTION").getPath());
        rPackageAnalyzer.analyzeDependency(dependency, null);

        Assert.assertNull(dependency.getEcosystem());
    }
}