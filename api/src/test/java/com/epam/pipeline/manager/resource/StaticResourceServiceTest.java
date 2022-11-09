/*
 * Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.manager.resource;

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.entity.datastorage.AbstractDataStorageItem;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageFolder;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.epam.pipeline.manager.resource.StaticResourcesService.buildHtml;
import static org.assertj.core.api.Assertions.assertThat;

public class StaticResourceServiceTest extends AbstractSpringTest {

    private static final String TEMPLATES_PATH = "classpath:/views/folder.vm";

    @Autowired
    private ApplicationContext context;

    @Test
    public void getHtmlContentTest() throws IOException {
        final List<AbstractDataStorageItem> items = new ArrayList<>();
        final DataStorageFile file1 = getDataStorageFile("file1", "path1",
                1234567899, "10/24/22, 9:27:20 PM");
        items.add(file1);
        final DataStorageFolder folder1 = getDataStorageFolder("folder1", "path1");
        items.add(folder1);
        final DataStorageFile file3 = getDataStorageFile("file3", "path3",
                1234567, "10/24/21, 9:27:20 PM");
        items.add(file3);
        final DataStorageFolder folder3 = getDataStorageFolder("folder3", "path3");
        items.add(folder3);
        final DataStorageFile file2 = getDataStorageFile("file2", "path2",
                123, "10/24/20, 9:27:20 PM");
        items.add(file2);

        assertThat(buildHtml(items, context.getResource(TEMPLATES_PATH).getFile().getAbsolutePath(), ""))
                .isNotBlank();
    }

    private DataStorageFolder getDataStorageFolder(final String name, final String path) {
        final DataStorageFolder folder = new DataStorageFolder();
        folder.setName(name);
        folder.setPath(path);
        return folder;
    }

    private DataStorageFile getDataStorageFile(final String name, final String path,
                                               final long size, final String changed) {
        final DataStorageFile file = new DataStorageFile();
        file.setName(name);
        file.setPath(path);
        file.setSize(size);
        file.setChanged(changed);
        return file;
    }
}
