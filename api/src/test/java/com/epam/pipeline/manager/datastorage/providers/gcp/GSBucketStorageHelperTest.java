package com.epam.pipeline.manager.datastorage.providers.gcp;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.vo.data.storage.RestoreFolderVO;
import com.epam.pipeline.entity.datastorage.gcp.GSBucketStorage;
import com.epam.pipeline.entity.region.GCPRegion;
import com.epam.pipeline.manager.cloud.gcp.GCPClient;
import com.google.api.gax.paging.Page;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
public class GSBucketStorageHelperTest {
    private static final String EMPTY_PREFIX = "";
    private static final String STORAGE_PATH = "storagePath";
    private static final String BUCKET = "bucket";
    private static final String OLD_PATH = "oldPath";
    private static final String NEW_PATH = "newPath";
    private static final Long VERSION = 123456789L;
    private static final String FIRST_FILE_PATH = OLD_PATH + "/firstFile.jpg";
    private static final String SECOND_FILE_PATH = OLD_PATH + "/" + NEW_PATH + "/secondFile.png";

    private final MessageHelper messageHelper = mock(MessageHelper.class);
    private final GCPRegion region = new GCPRegion();
    private GCPClient gcpClient = mock(GCPClient.class);
    private GSBucketStorage dataStorage = mock(GSBucketStorage.class);

    private final GSBucketStorageHelper storageHelper = spy(new GSBucketStorageHelper(messageHelper, region, gcpClient));
    private final Storage client = mock(Storage.class);

    @Before
    public void setUp() throws Exception {
        doReturn(client).when(gcpClient).buildStorageClient(region);
        when(dataStorage.getPath()).thenReturn(STORAGE_PATH);
    }

    @Test
    public void testRestoreFolderWithRecursionShouldLoopAllFolders() {
        storageHelper.restoreFolder(dataStorage, OLD_PATH, presettingForRestoreFolderMethodTests(true, null, null));
        verify(client).copy(argThat(hasSourceAndDestination(BlobId.of(BUCKET, FIRST_FILE_PATH, VERSION), FIRST_FILE_PATH)));
        verify(client).copy(argThat(hasSourceAndDestination(BlobId.of(BUCKET, SECOND_FILE_PATH, VERSION), SECOND_FILE_PATH)));

    }

    private BaseMatcher<Storage.CopyRequest> hasSourceAndDestination(BlobId blobId, String destination) {
        return new BaseMatcher<Storage.CopyRequest>() {
            @Override
            public boolean matches(final Object item) {
                final Storage.CopyRequest copyRequest = (Storage.CopyRequest) item;
                return Objects.equals(copyRequest.getSource(), blobId)
                        && Objects.equals(copyRequest.getTarget().getName(), destination);
            }

            @Override
            public void describeTo(Description description) {
                description.appendText(String.format("Copy blob request doesn't have required blob Id='%s' and blob Info", blobId));
            }
        };
    }

    private RestoreFolderVO presettingForRestoreFolderMethodTests(final boolean recursive,
                                                                  final List<String> includeList,
                                                                  final List<String> excludeList) {
        final String firstFolderPath = OLD_PATH;
        final BlobId firstFolderID = BlobId.of(BUCKET, firstFolderPath);
        final Blob firstFolder = client.create(BlobInfo.newBuilder(firstFolderID).build());
        firstFolder.isDirectory();
        final BlobId firstFileID = BlobId.of(BUCKET, FIRST_FILE_PATH, VERSION);
        final Blob firstFile = client.create(BlobInfo.newBuilder(firstFileID).build());

        final String secondFolderPath = NEW_PATH;
        final BlobId secondFolderID = BlobId.of(BUCKET, secondFolderPath);
        final Blob secondFolder = client.create(BlobInfo.newBuilder(secondFolderID).build());
        firstFolder.isDirectory();
        final BlobId secondFileID = BlobId.of(BUCKET, SECOND_FILE_PATH, VERSION);
        final Blob secondFile = client.create(BlobInfo.newBuilder(secondFileID).build());

        final Page<Blob> firstFolderBlobs = (Page<Blob>) spy(Page.class);
        when(firstFolderBlobs.getValues()).thenReturn(Arrays.asList(firstFile, secondFolder));

        final Page<Blob> secondFolderBlobs = (Page<Blob>) spy(Page.class);
        when(secondFolderBlobs.getValues()).thenReturn(Collections.singletonList(secondFile));

        when(client.get(firstFolderID)).thenReturn(firstFolder);
        when(client.get(firstFileID)).thenReturn(firstFile);
        when(client.list(BUCKET, Storage.BlobListOption.versions(true),
                Storage.BlobListOption.currentDirectory(),
                Storage.BlobListOption.prefix(firstFolderPath),
                Storage.BlobListOption.pageToken(EMPTY_PREFIX),
                Storage.BlobListOption.pageSize(Integer.MAX_VALUE))).thenReturn(firstFolderBlobs);

        when(client.get(secondFolderID)).thenReturn(secondFolder);
        when(client.get(secondFileID)).thenReturn(secondFile);
        when(client.list(BUCKET, Storage.BlobListOption.versions(true),
                Storage.BlobListOption.currentDirectory(),
                Storage.BlobListOption.prefix(secondFolderPath),
                Storage.BlobListOption.pageToken(EMPTY_PREFIX),
                Storage.BlobListOption.pageSize(Integer.MAX_VALUE))).thenReturn(secondFolderBlobs);

        final RestoreFolderVO restoreFolderVO = new RestoreFolderVO();
        restoreFolderVO.setRecursively(recursive);
        restoreFolderVO.setIncludeList(includeList);
        restoreFolderVO.setExcludeList(excludeList);

        return restoreFolderVO;
    }
}