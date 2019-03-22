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

package com.epam.pipeline.manager.metadata;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.metadata.MetadataEntity;
import com.epam.pipeline.manager.metadata.writer.MetadataWriter;
import com.epam.pipeline.manager.metadata.writer.MetadataWriterProvider;
import com.epam.pipeline.manager.pipeline.FolderManager;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import java.io.InputStream;
import java.io.Writer;
import java.util.Collections;
import java.util.List;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

public class MetadataDownloadManagerTest {

    private static final String TSV = "tsv";
    private static final String SAMPLE = "sample";
    private static final long FOLDER_ID = 10L;

    private final MetadataEntityManager metadataEntityManager = mock(MetadataEntityManager.class);
    private final FolderManager folderManager = mock(FolderManager.class);
    private final MessageHelper messageHelper = mock(MessageHelper.class);
    private final MetadataWriterProvider metadataWriterProvider = mock(MetadataWriterProvider.class);
    private final MetadataDownloadManager manager =
            new MetadataDownloadManager(metadataEntityManager, folderManager, messageHelper, metadataWriterProvider);

    @Test(expected = IllegalArgumentException.class)
    public void getInputStreamShouldThrowIfFolderDoesNotExist() {
        when(folderManager.load(FOLDER_ID)).thenThrow(new IllegalArgumentException());

        manager.getInputStream(FOLDER_ID, SAMPLE, TSV);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getInputStreamShouldThrowIfMetadataEntityDoesNotExistInParentFolder() {
        when(metadataEntityManager.loadMetadataEntityByClassNameAndFolderId(FOLDER_ID, SAMPLE))
                .thenReturn(Collections.emptyList());

        manager.getInputStream(FOLDER_ID, SAMPLE, TSV);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getInputStreamShouldThrowIfRequestedFileFormatIsNotSupported() {
        final List<MetadataEntity> entities = Collections.singletonList(new MetadataEntity());
        when(metadataEntityManager.loadMetadataEntityByClassNameAndFolderId(FOLDER_ID, SAMPLE)).thenReturn(entities);

        manager.getInputStream(FOLDER_ID, SAMPLE, "unsupportedFileFormat");
    }

    @Test
    public void getInputStreamShouldRetrieveMetadataWriterFromProvider() {
        final List<MetadataEntity> entities = Collections.singletonList(new MetadataEntity());
        when(metadataEntityManager.loadMetadataEntityByClassNameAndFolderId(FOLDER_ID, SAMPLE)).thenReturn(entities);
        final MetadataWriter metadataWriter = mock(MetadataWriter.class);
        when(metadataWriterProvider.getMetadataWriter(any(), any())).thenReturn(metadataWriter);

        manager.getInputStream(FOLDER_ID, SAMPLE, TSV);

        verify(metadataWriter).writeEntities(eq(SAMPLE), eq(entities));
    }

    @Test
    public void getInputStreamShouldReadFromWriterThatWasPassedToMetadataWriter() {
        final List<MetadataEntity> entities = Collections.singletonList(new MetadataEntity());
        when(metadataEntityManager.loadMetadataEntityByClassNameAndFolderId(FOLDER_ID, SAMPLE)).thenReturn(entities);
        final MetadataWriter metadataWriter = mock(MetadataWriter.class);
        final ArgumentCaptor<Writer> writerCaptor = ArgumentCaptor.forClass(Writer.class);
        when(metadataWriterProvider.getMetadataWriter(writerCaptor.capture(), any())).thenReturn(metadataWriter);
        doAnswer(invocation -> writeMessageAndReturnNothing(writerCaptor.getValue(), "message"))
                .when(metadataWriter).writeEntities(any(), any());

        final InputStream actualInputStream = manager.getInputStream(FOLDER_ID, SAMPLE, TSV);

        Assert.assertEquals("message", inputStreamAsString(actualInputStream));
    }

    @SneakyThrows
    private String inputStreamAsString(final InputStream inputStream) {
        return IOUtils.toString(inputStream);
    }

    @SneakyThrows
    private Object writeMessageAndReturnNothing(final Writer writer, final String message) {
        writer.write(message);
        return null;
    }
}
