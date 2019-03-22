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

import com.epam.dockercompscan.AbstractSpringTest;
import com.epam.dockercompscan.owasp.analyzer.RPackageAnalyzer;
import com.epam.dockercompscan.scan.domain.Dependency;
import org.junit.Assert;
import org.junit.Test;
import org.owasp.dependencycheck.exception.ExceptionCollection;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.net.URISyntaxException;
import java.util.List;

public class DependencyCheckServiceTest extends AbstractSpringTest {

    private ClassLoader classLoader = DependencyCheckServiceTest.class.getClassLoader();

    @Autowired
    private DependencyCheckService dependencyCheckService;

    @Test
    public void dependencyCheckServiceTest() throws URISyntaxException, ExceptionCollection {
        List<Dependency> dependencies =
                dependencyCheckService.runScan(new File(classLoader.getResource("owasp/analyzer").toURI()));

        Assert.assertEquals(1, dependencies.size());
        Assert.assertEquals(RPackageAnalyzer.DEPENDENCY_ECOSYSTEM, dependencies.get(0).getEcosystem());
        Assert.assertEquals("PositiveTest", dependencies.get(0).getName());
    }
}