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

package com.epam.pipeline.manager;

import com.epam.pipeline.AbstractSpringTest;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;

import java.io.File;
import java.io.IOException;

/**
 * Created by kite on 10.03.17.
 */
public abstract class AbstractManagerTest extends AbstractSpringTest {

    private static final String TEMPLATES_PATH = "classpath:templates/";

    @Value("${working.directory}")
    private String workingDirPath;

    @Autowired
    private ApplicationContext context;

    @After
    public void tearDown() throws IOException {
        File file = new File(workingDirPath);
        FileUtils.deleteDirectory(file);
    }

    public File getTestFile(String path) throws IOException{
        return context.getResource(TEMPLATES_PATH + path).getFile();
    }
}
