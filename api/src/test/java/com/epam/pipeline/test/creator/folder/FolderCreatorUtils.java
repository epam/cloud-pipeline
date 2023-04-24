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

package com.epam.pipeline.test.creator.folder;

import com.epam.pipeline.controller.Result;
import com.epam.pipeline.entity.configuration.RunConfiguration;
import com.epam.pipeline.entity.metadata.FolderWithMetadata;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.Collections;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_INT;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;

public final class FolderCreatorUtils {

    public static final TypeReference<Result<Folder>> FOLDER_TYPE = new TypeReference<Result<Folder>>() { };
    public static final TypeReference<Result<FolderWithMetadata>> FOLDER_WITH_METADATA_TYPE =
            new TypeReference<Result<FolderWithMetadata>>() { };

    private FolderCreatorUtils() {

    }

    public static Folder getFolder() {
        return getFolder(ID, TEST_STRING);
    }

    public static Folder getFolder(final String owner) {
        final Folder folder = new Folder();
        folder.setId(ID);
        folder.setOwner(owner);
        folder.setMetadata(Collections.singletonMap(TEST_STRING, TEST_INT));
        folder.setHasMetadata(true);
        return folder;
    }

    public static Folder getFolder(final Long id, final String owner) {
        final Folder folder = new Folder();
        folder.setId(id);
        folder.setOwner(owner);
        folder.setMetadata(Collections.singletonMap(TEST_STRING, TEST_INT));
        folder.setHasMetadata(true);
        folder.setPipelines(Collections.singletonList(new Pipeline()));
        return folder;
    }

    public static Folder getFolder(final Long id, final Long parentId, final String owner) {
        final Folder folder = new Folder();
        folder.setId(id);
        folder.setOwner(owner);
        folder.setParentId(parentId);
        return folder;
    }

    public static FolderWithMetadata getFolderWithMetadata() {
        return getFolderWithMetadata(ID, TEST_STRING);
    }

    public static FolderWithMetadata getFolderWithMetadata(final Long id, final String owner) {
        final FolderWithMetadata folderWithMetadata = new FolderWithMetadata();
        folderWithMetadata.setId(id);
        folderWithMetadata.setOwner(owner);
        folderWithMetadata.setMetadata(Collections.singletonMap(TEST_STRING, TEST_INT));
        folderWithMetadata.setHasMetadata(true);
        folderWithMetadata.setConfigurations(Collections.singletonList(new RunConfiguration()));
        folderWithMetadata.setPipelines(Collections.singletonList(new Pipeline()));
        return folderWithMetadata;
    }

    public static FolderWithMetadata getFolderWithMetadata(final Long id, final Long parentId, final String owner) {
        final FolderWithMetadata folderWithMetadata = new FolderWithMetadata();
        folderWithMetadata.setId(id);
        folderWithMetadata.setOwner(owner);
        folderWithMetadata.setParentId(parentId);
        return folderWithMetadata;
    }
}
