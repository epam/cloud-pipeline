/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.epam.pipeline.controller.datastorage;

import com.epam.pipeline.controller.vo.data.storage.UpdateDataStorageItemVO;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.ContentDisposition;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageFolder;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.datastorage.rules.DataStorageRule;
import com.epam.pipeline.acl.datastorage.DataStorageApiService;
import com.epam.pipeline.entity.datastorage.tags.DataStorageTag;
import com.epam.pipeline.test.creator.datastorage.DatastorageCreatorUtils;
import com.epam.pipeline.test.web.AbstractControllerTest;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@WebMvcTest(controllers = DataStorageController.class)
public abstract class AbstractDataStorageControllerTest extends AbstractControllerTest {

    protected static final long ID = 1L;
    protected static final int TEST_INT = 1;
    protected static final String TEST = "TEST";
    protected static final String DATASTORAGE_URL = SERVLET_PATH + "/datastorage";
    protected static final String LOAD_ALL_URL = DATASTORAGE_URL + "/loadAll";
    protected static final String LOAD_AVAILABLE_URL = DATASTORAGE_URL + "/available";
    protected static final String LOAD_AVAILABLE_WITH_MOUNTS = DATASTORAGE_URL + "/availableWithMounts";
    protected static final String LOAD_WRITABLE_URL = DATASTORAGE_URL + "/mount";
    protected static final String BY_ID_URL = DATASTORAGE_URL + "/%d";
    protected static final String LOAD_DATASTORAGE = BY_ID_URL + "/load";
    protected static final String FIND_URL = DATASTORAGE_URL + "/find";
    protected static final String FIND_BY_PATH_URL = DATASTORAGE_URL + "/findByPath";
    protected static final String DATASTORAGE_ITEMS_URL = BY_ID_URL + "/list";
    protected static final String DATASTORAGE_LISTING_URL = DATASTORAGE_ITEMS_URL + "/page";
    protected static final String DATASTORAGE_ITEMS_UPLOAD_URL = DATASTORAGE_ITEMS_URL + "/upload";
    protected static final String DATASTORAGE_UPLOAD_STREAM_URL = BY_ID_URL + "/upload/stream";
    protected static final String DATASTORAGE_DOWNLOAD_STREAM_URL = BY_ID_URL + "/download";
    protected static final String DATASTORAGE_ITEMS_CONTENT = BY_ID_URL + "/content";
    protected static final String DOWNLOAD_REDIRECT_URL = BY_ID_URL + "/downloadRedirect";
    protected static final String GENERATE_URL = BY_ID_URL + "/generateUrl";
    protected static final String GENERATE_UPLOAD_URL = BY_ID_URL + "/generateUploadUrl";
    protected static final String CONTENT_URL = BY_ID_URL + "/content";
    protected static final String RESTORE_VERSION_URL = DATASTORAGE_ITEMS_URL + "/restore";
    protected static final String DATASTORAGE_SAVE_URL= DATASTORAGE_URL + "/save";
    protected static final String DATASTORAGE_UPDATE_URL = DATASTORAGE_URL + "/update";
    protected static final String DATASTORAGE_POLICY_URL = DATASTORAGE_URL + "/policy";
    protected static final String DATASTORAGE_DELETE_URL = BY_ID_URL + "/delete";
    protected static final String DATASTORAGE_RULE_URL = DATASTORAGE_URL + "/rule";
    protected static final String SAVE_RULE_URL = DATASTORAGE_RULE_URL + "/register";
    protected static final String LOAD_RULES_URL = DATASTORAGE_RULE_URL + "/load";
    protected static final String DELETE_RULES_URL = DATASTORAGE_RULE_URL + "/delete";
    protected static final String TEMP_CREDENTIALS_URL = DATASTORAGE_URL + "/tempCredentials/";
    protected static final String TAGS_URL = BY_ID_URL + "/tags";
    protected static final String TAGS_BULK_URL = TAGS_URL + "/bulk";
    protected static final String TAGS_LIST_URL = TAGS_URL + "/list";
    protected static final String SHARED_LINK_URL = BY_ID_URL + "/sharedLink";
    protected static final String PERMISSION_URL = DATASTORAGE_URL + "/permission";
    protected static final String PATH_URL = DATASTORAGE_URL + "/path";
    protected static final String PATH_SIZE_URL = PATH_URL + "/size";
    protected static final String PATH_USAGE_URL = PATH_URL + "/usage";
    protected static final String SHARED_STORAGE_URL = DATASTORAGE_URL + "/sharedStorage";
    protected static final String OCTET_STREAM_CONTENT_TYPE = "application/octet-stream";
    protected static final String ID_AS_STRING = String.valueOf(ID);
    protected static final String TRUE_AS_STRING = String.valueOf(true);
    protected static final String FALSE_AS_STRING = String.valueOf(false);
    protected static final String FROM_REGION = "fromRegion";
    protected static final String ID_PARAM = "id";
    protected static final String PATH = "path";
    protected static final String SHOW_VERSION = "showVersion";
    protected static final String PAGE_SIZE = "pageSize";
    protected static final String MARKER = "marker";
    protected static final String VERSION = "version";
    protected static final String TOTALLY = "totally";
    protected static final String TEST_PATH = "localhost:root/";
    protected static final String CONTENT_DISPOSITION_PARAM = "contentDisposition";
    protected static final String CONTENT_DISPOSITION_HEADER = "Content-Disposition";
    protected static final String CLOUD = "cloud";
    protected static final String SKIP_POLICY = "skipPolicy";
    protected static final String PIPELINE_ID = "pipelineId";
    protected static final String FILE_MASK = "fileMask";
    protected static final String FILTER_MASK = "filterMask";
    protected static final String REWRITE = "rewrite";
    protected static final String EMPTY_STRING = "";
    protected static final String PAGE = "page";
    protected static final String RUN_ID = "runId";
    protected static final String FILE_NAME = "file.txt";
    protected static final String FILE_SIZE = "0 Kb";
    protected static final String MULTIPART_CONTENT_TYPE =
            "multipart/form-data; boundary=--------------------------boundary";
    protected static final String MULTIPART_CONTENT = "----------------------------boundary\r\n" +
            "Content-Disposition: form-data; name=\"file\"; filename=\"file.txt\"\r\n" +
            "Content-Type: application/octet-stream\r\n" +
            "\r\n" +
            TEST +
            "\r\n" +
            "----------------------------boundary--";
    protected static final ContentDisposition CONTENT_DISPOSITION = ContentDisposition.INLINE;
    protected static final Map<String, String> TAGS = Collections.singletonMap(TEST, TEST);
    protected static final DataStorageTag OBJECT_TAG = DatastorageCreatorUtils.getDataStorageTag();
    protected static final List<DataStorageTag> OBJECT_TAGS = Collections.singletonList(OBJECT_TAG);
    protected final DataStorageRule dataStorageRule = DatastorageCreatorUtils.getDataStorageRule();
    protected final S3bucketDataStorage s3Bucket = DatastorageCreatorUtils.getS3bucketDataStorage();
    protected final DataStorageFile file = DatastorageCreatorUtils.getDataStorageFile();
    protected final DataStorageFolder folder = DatastorageCreatorUtils.getDataStorageFolder();
    protected final UpdateDataStorageItemVO update = DatastorageCreatorUtils.getUpdateDataStorageItemVO();
    protected final List<Pair<AbstractDataStorage, TypeReference>> storageTypeReferenceList =
            DatastorageCreatorUtils.getRegularTypeStorages();
    protected final List<Pair<AbstractDataStorage, TypeReference>> securedStorageTypeReferenceList =
            DatastorageCreatorUtils.getSecuredTypeStorages();

    @Autowired
    protected DataStorageApiService mockStorageApiService;
}
