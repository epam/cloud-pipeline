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

import org.junit.Assert;
import org.junit.Test;
import org.owasp.dependencycheck.analyzer.exception.AnalysisException;
import org.owasp.dependencycheck.dependency.Dependency;

public class NvidiaVersionAnalyzerTest {
    private final NvidiaVersionAnalyzer nvidiaVersionAnalyzer = new NvidiaVersionAnalyzer();

    @Test
    public void shouldAnalyzeNvidiaVersion() throws AnalysisException {
        final Dependency dependency = new Dependency();
        nvidiaVersionAnalyzer.analyzeDependency(dependency, null);

        Assert.assertEquals(NvidiaVersionAnalyzer.DEPENDENCY_ECOSYSTEM, dependency.getEcosystem());
        Assert.assertEquals(NvidiaVersionAnalyzer.DEPENDENCY_NAME, dependency.getName());
    }
}
