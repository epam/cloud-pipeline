package com.epam.pipeline.manager.datastorage.providers.gcp;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.vo.data.storage.RestoreFolderVO;
import com.epam.pipeline.entity.datastorage.gcp.GSBucketStorage;
import com.epam.pipeline.entity.region.GCPRegion;
import com.epam.pipeline.manager.cloud.gcp.GCPClient;
import com.google.api.gax.paging.Page;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.CopyWriter;
import com.google.cloud.storage.Storage;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.joda.time.LocalDate;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "PMD.TooManyStaticImports"})
public class GSBucketStorageHelperTest {
    private static final String EMPTY_PREFIX = "";
    private static final String BUCKET = "bucket";
    private static final String OLD_PATH = "oldPath/";
    private static final String NEW_PATH = "newPath/";
    private static final Long VERSION = 123456789L;
    private static final String FIRST_FILE_PATH = OLD_PATH + "firstFile.jpg";
    private static final String SECOND_FILE_PATH = OLD_PATH + NEW_PATH + "secondFile.png";
    private static final String JPG_PATTERN = "*.jpg";
    private static final Long DATE_IN_MILLISECONDS = new Date().getTime();

    private final MessageHelper messageHelper = mock(MessageHelper.class);
    private final GCPRegion region = new GCPRegion();
    private final GCPClient gcpClient = mock(GCPClient.class);
    private final GSBucketStorage dataStorage = mock(GSBucketStorage.class);

    private final GSBucketStorageHelper storageHelper = spy(
            new GSBucketStorageHelper(messageHelper, region, gcpClient));
    private final Storage client = mock(Storage.class);

    @Before
    public void setUp() throws Exception {
        doReturn(client).when(gcpClient).buildStorageClient(region);
        when(dataStorage.getPath()).thenReturn(BUCKET);
    }

    @Test
    public void testRestoreFolderWithExcludeListShouldNotRestoreExcludeFiles() {
        storageHelper.restoreFolder(dataStorage, OLD_PATH,
                presettingForRestoreFolderMethodTests(true, null,
                        Collections.singletonList(JPG_PATTERN), null));
        verify(client).copy(argThat(hasSourceAndDestination(
                BlobId.of(BUCKET, SECOND_FILE_PATH, VERSION), SECOND_FILE_PATH)));
    }

    @Test
    public void testRestoreFolderWithIncludeListAndWithRecursionShouldRestoreOnlyIncludeFiles() {
        storageHelper.restoreFolder(dataStorage, OLD_PATH,
                presettingForRestoreFolderMethodTests(true,
                        Collections.singletonList("*.png"), null, null));
        verify(client).copy(argThat(hasSourceAndDestination(
                BlobId.of(BUCKET, SECOND_FILE_PATH, VERSION), SECOND_FILE_PATH)));
    }

    @Test
    public void testRestoreFolderWithIncludeListAndNoRecursionShouldRestoreOnlyIncludeFiles() {
        storageHelper.restoreFolder(dataStorage, OLD_PATH,
                presettingForRestoreFolderMethodTests(false,
                        Collections.singletonList(JPG_PATTERN), null, null));
        verify(client).copy(argThat(hasSourceAndDestination(
                BlobId.of(BUCKET, FIRST_FILE_PATH, VERSION), FIRST_FILE_PATH)));
    }

    @Test
    public void testRestoreFolderShouldRestoreOnlyFilesWithDeleteMarker() {
        final BlobId withoutDeleteMarkerFileID = BlobId.of(BUCKET, FIRST_FILE_PATH, VERSION);
        final Blob withoutDeleteMarkerFile = mock(Blob.class);
        when(withoutDeleteMarkerFile.getName()).thenReturn(FIRST_FILE_PATH);
        when(withoutDeleteMarkerFile.getGeneration()).thenReturn(VERSION);
        when(withoutDeleteMarkerFile.getBlobId()).thenReturn(withoutDeleteMarkerFileID);
        when(withoutDeleteMarkerFile.getDeleteTime()).thenReturn(null); //exclusion condition
        when(client.get(withoutDeleteMarkerFileID)).thenReturn(withoutDeleteMarkerFile);

        storageHelper.restoreFolder(dataStorage, OLD_PATH,
                presettingForRestoreFolderMethodTests(true, null, null, withoutDeleteMarkerFile));
        verify(client).copy(argThat(hasSourceAndDestination(
                BlobId.of(BUCKET, FIRST_FILE_PATH, VERSION), FIRST_FILE_PATH)));
        verify(client).copy(argThat(hasSourceAndDestination(
                BlobId.of(BUCKET, SECOND_FILE_PATH, VERSION), SECOND_FILE_PATH)));
    }

    @Test
    public void testRestoreFolderShouldRestoreOnlyLastFileVersion() {
        final BlobId firstFileOldVersionID = BlobId.of(BUCKET, FIRST_FILE_PATH, VERSION);
        final Blob firstFileOldVersion = mock(Blob.class);
        when(firstFileOldVersion.getName()).thenReturn(FIRST_FILE_PATH);
        when(firstFileOldVersion.getGeneration()).thenReturn(VERSION);
        when(firstFileOldVersion.getBlobId()).thenReturn(firstFileOldVersionID);
        when(firstFileOldVersion.getUpdateTime()).thenReturn(LocalDate.parse("1995-01-24").toDate().getTime());
        when(firstFileOldVersion.getDeleteTime()).thenReturn(DATE_IN_MILLISECONDS);
        when(client.get(firstFileOldVersionID)).thenReturn(firstFileOldVersion);

        storageHelper.restoreFolder(dataStorage, OLD_PATH,
                presettingForRestoreFolderMethodTests(true, null, null, firstFileOldVersion));
        verify(client).copy(argThat(hasSourceAndDestination(
                BlobId.of(BUCKET, FIRST_FILE_PATH, VERSION), FIRST_FILE_PATH)));
        verify(client).copy(argThat(hasSourceAndDestination(
                BlobId.of(BUCKET, SECOND_FILE_PATH, VERSION), SECOND_FILE_PATH)));
    }

    @Test
    public void testRestoreFolderWithRecursionShouldLoopAllFolders() {
        storageHelper.restoreFolder(dataStorage, OLD_PATH,
                presettingForRestoreFolderMethodTests(true, null, null, null));
        verify(client).copy(argThat(hasSourceAndDestination(
                BlobId.of(BUCKET, FIRST_FILE_PATH, VERSION), FIRST_FILE_PATH)));
        verify(client).copy(argThat(hasSourceAndDestination(
                BlobId.of(BUCKET, SECOND_FILE_PATH, VERSION), SECOND_FILE_PATH)));

    }

    private RestoreFolderVO presettingForRestoreFolderMethodTests(final boolean recursive,
                                                                  final List<String> includeList,
                                                                  final List<String> excludeList,
                                                                  final Blob optionalBlob) {
        final String firstFolderPath = OLD_PATH;
        final BlobId firstFolderID = BlobId.of(BUCKET, firstFolderPath, null);
        final Blob firstFolder = mock(Blob.class);
        when(firstFolder.isDirectory()).thenReturn(true);
        when(firstFolder.getName()).thenReturn(firstFolderPath);
        when(client.get(firstFolderID)).thenReturn(firstFolder);

        final BlobId firstFileID = BlobId.of(BUCKET, FIRST_FILE_PATH, VERSION);
        final Blob firstFile = mock(Blob.class);
        when(firstFile.getName()).thenReturn(FIRST_FILE_PATH);
        when(firstFile.getGeneration()).thenReturn(VERSION);
        when(firstFile.getBlobId()).thenReturn(firstFileID);
        when(firstFile.getUpdateTime()).thenReturn(DATE_IN_MILLISECONDS);
        when(firstFile.getDeleteTime()).thenReturn(DATE_IN_MILLISECONDS);
        when(client.get(firstFileID)).thenReturn(firstFile);

        final String secondFolderPath = OLD_PATH + NEW_PATH;
        final BlobId secondFolderID = BlobId.of(BUCKET, secondFolderPath, null);
        final Blob secondFolder = mock(Blob.class);
        when(secondFolder.isDirectory()).thenReturn(true);
        when(secondFolder.getName()).thenReturn(secondFolderPath);
        when(client.get(secondFolderID)).thenReturn(secondFolder);

        final BlobId secondFileID = BlobId.of(BUCKET, SECOND_FILE_PATH, VERSION);
        final Blob secondFile = mock(Blob.class);
        when(secondFile.getName()).thenReturn(SECOND_FILE_PATH);
        when(secondFile.getGeneration()).thenReturn(VERSION);
        when(secondFile.getBlobId()).thenReturn(secondFileID);
        when(secondFile.getUpdateTime()).thenReturn(DATE_IN_MILLISECONDS);
        when(secondFile.getDeleteTime()).thenReturn(DATE_IN_MILLISECONDS);
        when(client.get(secondFileID)).thenReturn(secondFile);

        final Page<Blob> firstFolderBlobs = (Page<Blob>) spy(Page.class);
        final List<Blob> firstFolderBlobsList = Optional.ofNullable(optionalBlob)
                .map(blob -> Arrays.asList(firstFile, blob, secondFolder))
                .orElse(Arrays.asList(firstFile, secondFolder));
        when(firstFolderBlobs.getValues()).thenReturn(firstFolderBlobsList);
        when(firstFolderBlobs.iterateAll()).thenReturn(firstFolderBlobsList);

        final Page<Blob> secondFolderBlobs = (Page<Blob>) spy(Page.class);
        when(secondFolderBlobs.getValues()).thenReturn(Collections.singletonList(secondFile));
        when(secondFolderBlobs.iterateAll()).thenReturn(Collections.singletonList(secondFile));

        when(client.list(BUCKET, Storage.BlobListOption.versions(true),
                Storage.BlobListOption.currentDirectory(),
                Storage.BlobListOption.prefix(firstFolderPath),
                Storage.BlobListOption.pageToken(EMPTY_PREFIX),
                Storage.BlobListOption.pageSize(Integer.MAX_VALUE))).thenReturn(firstFolderBlobs);

        when(client.list(BUCKET, Storage.BlobListOption.versions(true),
                Storage.BlobListOption.currentDirectory(),
                Storage.BlobListOption.prefix(secondFolderPath),
                Storage.BlobListOption.pageToken(EMPTY_PREFIX),
                Storage.BlobListOption.pageSize(Integer.MAX_VALUE))).thenReturn(secondFolderBlobs);

        final CopyWriter firstFileCopyWriter = mock(CopyWriter.class);
        when(firstFileCopyWriter.getResult()).thenReturn(firstFile);

        final CopyWriter secondFileCopyWriter = mock(CopyWriter.class);
        when(secondFileCopyWriter.getResult()).thenReturn(secondFile);

        when(client.copy(any(Storage.CopyRequest.class))).thenReturn(secondFileCopyWriter, firstFileCopyWriter);

        final RestoreFolderVO restoreFolderVO = new RestoreFolderVO();
        restoreFolderVO.setRecursively(recursive);
        restoreFolderVO.setIncludeList(includeList);
        restoreFolderVO.setExcludeList(excludeList);

        return restoreFolderVO;
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
                description.appendText(
                        String.format("Copy blob request doesn't have required blob Id='%s' and blob Info", blobId));
            }
        };
    }
}