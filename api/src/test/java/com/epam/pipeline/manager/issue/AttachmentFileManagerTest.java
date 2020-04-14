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

package com.epam.pipeline.manager.issue;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageStreamingContent;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.issue.Attachment;
import com.epam.pipeline.entity.preference.Preference;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.AuthManager;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

public class AttachmentFileManagerTest {
    private static final String TEST_SYSTEM_DATA_STORAGE = "testStorage";
    private static final String TEST_ATTACHMENT_PATH = "somePath";
    private static final String TEST_ATTACHMENT_NAME = "testFile";
    private static final String TEST_USER = "testUser";

    @Mock
    private DataStorageManager dataStorageManager;
    @Mock
    private PreferenceManager preferenceManager;
    @Mock
    private AttachmentManager attachmentManager;
    @Mock
    private MessageHelper messageHelper;
    @Mock
    private AuthManager authManager;

    private AttachmentFileManager attachmentFileManager;

    private S3bucketDataStorage testSystemDataStorage = new S3bucketDataStorage(1L, TEST_SYSTEM_DATA_STORAGE, "test");

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        attachmentFileManager = new AttachmentFileManager(dataStorageManager, preferenceManager, attachmentManager,
                                                          messageHelper, authManager);

        Preference systemDataStorage = SystemPreferences.DATA_STORAGE_SYSTEM_DATA_STORAGE_NAME.toPreference();
        systemDataStorage.setName(TEST_SYSTEM_DATA_STORAGE);
        when(preferenceManager.getPreference(SystemPreferences.DATA_STORAGE_SYSTEM_DATA_STORAGE_NAME))
            .thenReturn(TEST_SYSTEM_DATA_STORAGE);

        when(dataStorageManager.loadByNameOrId(TEST_SYSTEM_DATA_STORAGE)).thenReturn(testSystemDataStorage);
        when(dataStorageManager.createDataStorageFile(Mockito.eq(1L), Mockito.anyString(), Mockito.anyString(),
                                                      Mockito.any(InputStream.class)))
            .then((Answer<DataStorageFile>) invocation -> {
                String path = invocation.getArgumentAt(1, String.class);
                String name = invocation.getArgumentAt(2, String.class);
                DataStorageFile file = new DataStorageFile();
                file.setPath(path + "/" + name);
                return file;
            });

        when(attachmentManager.load(Mockito.anyLong())).thenAnswer(invocation -> {
            Attachment attachment = new Attachment();
            attachment.setId(invocation.getArgumentAt(0, Long.class));
            attachment.setName(TEST_ATTACHMENT_NAME);
            attachment.setPath(TEST_ATTACHMENT_PATH);
            return attachment;
        });

        DataStorageStreamingContent content = new DataStorageStreamingContent(new ByteArrayInputStream(new byte[]{1}),
                                                                              TEST_ATTACHMENT_NAME);
        when(dataStorageManager.getStreamingContent(testSystemDataStorage.getId(), TEST_ATTACHMENT_PATH, null))
            .thenReturn(content);
        when(authManager.getAuthorizedUser()).thenReturn(TEST_USER);
    }

    @Test
    public void testUploadAttachment() {
        Attachment attachment = attachmentFileManager.uploadAttachment(new ByteArrayInputStream(new byte[]{1}),
                                                                       TEST_ATTACHMENT_NAME);
        Assert.assertNotNull(attachment.getPath());
        Assert.assertTrue(attachment.getPath().startsWith("attachments/"));
        Assert.assertTrue(attachment.getPath().endsWith(TEST_ATTACHMENT_NAME));
        Assert.assertEquals(TEST_ATTACHMENT_NAME, attachment.getName());

        verify(dataStorageManager).loadByNameOrId(TEST_SYSTEM_DATA_STORAGE);
        verify(dataStorageManager).createDataStorageFile(eq(testSystemDataStorage.getId()), eq("attachments"),
                                                         Mockito.endsWith(TEST_ATTACHMENT_NAME),
                                                         Mockito.any(InputStream.class));

        ArgumentCaptor<Attachment> attachmentCaptor = ArgumentCaptor.forClass(Attachment.class);
        verify(attachmentManager).create(attachmentCaptor.capture());
        Assert.assertEquals(TEST_USER, attachmentCaptor.getValue().getOwner());
    }

    @Test
    public void testDownloadAttachment() {
        DataStorageStreamingContent content = attachmentFileManager.downloadAttachment(1L);
        Assert.assertNotNull(content.getContent());
        Assert.assertEquals(TEST_ATTACHMENT_NAME, content.getName());

        verify(dataStorageManager).loadByNameOrId(TEST_SYSTEM_DATA_STORAGE);
        verify(attachmentManager).load(1L);
        verify(dataStorageManager).getStreamingContent(testSystemDataStorage.getId(), TEST_ATTACHMENT_PATH, null);
    }
}