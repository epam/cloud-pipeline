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

package com.epam.pipeline.controller.datastorage;

import com.epam.pipeline.entity.datastorage.FileShareMount;
import com.epam.pipeline.manager.datastorage.FileShareMountApiService;
import com.epam.pipeline.test.creator.datastorage.DatastorageCreatorUtils;
import com.epam.pipeline.test.web.AbstractControllerTest;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@WebMvcTest(controllers = FileShareMountController.class)
public class FileShareMountControllerTest extends AbstractControllerTest {

    private static final long ID = 1L;
    private static final String FILESHAREMOUNT_URL = SERVLET_PATH + "/filesharemount";
    private static final String FILESHAREMOUNT_ID_URL = FILESHAREMOUNT_URL + "/%d";

    @Autowired
    private FileShareMountApiService mockFileShareMountApiService;

    @Test
    public void shouldFailSaveForUnauthorizedUser() {
        performUnauthorizedRequest(post(FILESHAREMOUNT_URL));
    }

    @Test
    @WithMockUser
    public void shouldSave() throws Exception {
        final FileShareMount fileShareMount = DatastorageCreatorUtils.getFileShareMount();
        final String content = getObjectMapper().writeValueAsString(fileShareMount);
        Mockito.doReturn(fileShareMount).when(mockFileShareMountApiService).save(fileShareMount);

        final MvcResult mvcResult = performRequest(post(FILESHAREMOUNT_URL).content(content));

        Mockito.verify(mockFileShareMountApiService).save(fileShareMount);
        assertResponse(mvcResult, fileShareMount, DatastorageCreatorUtils.FILE_SHARE_MOUNT_TYPE);
    }

    @Test
    public void shouldFailDeleteForUnauthorizedUser() {
        performUnauthorizedRequest(delete(String.format(FILESHAREMOUNT_ID_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldDelete() {
        performRequestWithoutResponse(delete(String.format(FILESHAREMOUNT_ID_URL, ID)));

        Mockito.verify(mockFileShareMountApiService).delete(ID);
    }
}
