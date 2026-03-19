/*
 * Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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
package com.epam.pipeline.manager.datastorage.providers.nfs;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.datastorage.DataStorageDao;
import com.epam.pipeline.entity.datastorage.FileShareMount;
import com.epam.pipeline.entity.datastorage.MountType;
import com.epam.pipeline.entity.datastorage.nfs.NFSDataStorage;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.manager.CmdExecutor;
import com.epam.pipeline.manager.datastorage.FileShareMountManager;
import com.epam.pipeline.manager.region.CloudRegionManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.internal.util.reflection.Whitebox;

import java.util.Collections;
import java.util.concurrent.*;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

public class NFSStorageMounterTest {

    private static final long SHARE_MOUNT_ID_A = 1L;
    private static final long SHARE_MOUNT_ID_B = 2L;
    private static final long REGION_ID = 10L;
    private static final String MOUNT_ROOT_A = "server-a";
    private static final String MOUNT_ROOT_B = "server-b";
    private static final long TIMEOUT_SEC = 1L;
    private static final long LOCK_WAIT_MS = 100L;
    private static final String ROOT_DIR_1 = ":root/dir1";
    private static final String ROOT_DIR_2 = ":root/dir2";

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Mock
    private MessageHelper messageHelper;
    @Mock
    private DataStorageDao dataStorageDao;
    @Mock
    private CloudRegionManager regionManager;
    @Mock
    private FileShareMountManager shareMountManager;
    @Mock
    private CmdExecutor mockCmdExecutor;

    private NFSStorageMounter mounter;
    private ExecutorService executor;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mounter = new NFSStorageMounter(
                messageHelper, dataStorageDao, regionManager, shareMountManager,
                tempFolder.getRoot().getAbsolutePath(), 0L);
        Whitebox.setInternalState(mounter, "cmdExecutor", mockCmdExecutor);

        final AwsRegion region = new AwsRegion();
        region.setId(REGION_ID);
        region.setProvider(CloudProvider.AWS);
        when(regionManager.load(REGION_ID)).thenReturn(region);

        when(shareMountManager.load(SHARE_MOUNT_ID_A))
                .thenReturn(createFileShareMount(SHARE_MOUNT_ID_A, MOUNT_ROOT_A));
        when(shareMountManager.load(SHARE_MOUNT_ID_B))
                .thenReturn(createFileShareMount(SHARE_MOUNT_ID_B, MOUNT_ROOT_B));

        executor = Executors.newFixedThreadPool(2);
    }

    @After
    public void tearDown() {
        executor.shutdownNow();
    }

    @Test
    public void mountShouldSerializeOperationsOnSameShareRoot() throws Exception {
        final CountDownLatch firstMountEntered = new CountDownLatch(1);
        final CountDownLatch firstMountCanProceed = new CountDownLatch(1);

        when(mockCmdExecutor.executeCommand(anyString(), anyLong()))
                .thenAnswer(invocation -> {
                    firstMountEntered.countDown();
                    firstMountCanProceed.await(TIMEOUT_SEC, TimeUnit.SECONDS);
                    return "";
                });

        final NFSDataStorage storage1 = createStorage(1L, MOUNT_ROOT_A + ROOT_DIR_1, SHARE_MOUNT_ID_A);
        final NFSDataStorage storage2 = createStorage(2L, MOUNT_ROOT_A + ROOT_DIR_2, SHARE_MOUNT_ID_A);

        final Future<?> future1 = executor.submit(() -> mounter.mount(storage1));

        assertTrue("First mount should enter executeCommand",
                firstMountEntered.await(TIMEOUT_SEC, TimeUnit.SECONDS));

        final Future<?> future2 = executor.submit(() -> mounter.mount(storage2));

        Thread.sleep(LOCK_WAIT_MS);
        assertFalse("Second mount should be blocked while first holds the lock",
                future2.isDone());

        firstMountCanProceed.countDown();

        future1.get(TIMEOUT_SEC, TimeUnit.SECONDS);
        future2.get(TIMEOUT_SEC, TimeUnit.SECONDS);
    }

    @Test
    public void mountShouldAllowParallelOperationsOnDifferentShareRoots() throws Exception {
        final CountDownLatch bothMountsEntered = new CountDownLatch(2);
        final CountDownLatch canProceed = new CountDownLatch(1);

        when(mockCmdExecutor.executeCommand(anyString(), anyLong()))
                .thenAnswer(invocation -> {
                    bothMountsEntered.countDown();
                    canProceed.await(TIMEOUT_SEC, TimeUnit.SECONDS);
                    return "";
                });

        final NFSDataStorage storageA = createStorage(1L, MOUNT_ROOT_A + ROOT_DIR_1, SHARE_MOUNT_ID_A);
        final NFSDataStorage storageB = createStorage(2L, MOUNT_ROOT_B + ROOT_DIR_1, SHARE_MOUNT_ID_B);

        final Future<?> futureA = executor.submit(() -> mounter.mount(storageA));
        final Future<?> futureB = executor.submit(() -> mounter.mount(storageB));

        assertTrue("Both mounts should enter executeCommand concurrently",
                bothMountsEntered.await(TIMEOUT_SEC, TimeUnit.SECONDS));

        canProceed.countDown();

        futureA.get(TIMEOUT_SEC, TimeUnit.SECONDS);
        futureB.get(TIMEOUT_SEC, TimeUnit.SECONDS);
    }

    @Test
    public void mountAndUnmountShouldShareLockForSameShareRoot() throws Exception {
        final CountDownLatch mountEntered = new CountDownLatch(1);
        final CountDownLatch mountCanProceed = new CountDownLatch(1);

        when(mockCmdExecutor.executeCommand(anyString(), anyLong()))
                .thenAnswer(invocation -> {
                    mountEntered.countDown();
                    mountCanProceed.await(TIMEOUT_SEC, TimeUnit.SECONDS);
                    return "";
                });
        when(mockCmdExecutor.executeCommand(anyString()))
                .thenReturn("");

        final NFSDataStorage storage = createStorage(1L, MOUNT_ROOT_A + ROOT_DIR_1, SHARE_MOUNT_ID_A);
        when(dataStorageDao.loadDataStoragesByFileShareMountID(SHARE_MOUNT_ID_A))
                .thenReturn(Collections.singletonList(storage));

        final Future<?> mountFuture = executor.submit(() -> mounter.mount(storage));

        assertTrue("Mount should enter executeCommand",
                mountEntered.await(TIMEOUT_SEC, TimeUnit.SECONDS));

        final Future<?> unmountFuture = executor.submit(() -> mounter.unmountNFSIfEmpty(storage));

        Thread.sleep(LOCK_WAIT_MS);
        assertFalse("Unmount should be blocked while mount holds the lock",
                unmountFuture.isDone());

        mountCanProceed.countDown();

        mountFuture.get(TIMEOUT_SEC, TimeUnit.SECONDS);
        unmountFuture.get(TIMEOUT_SEC, TimeUnit.SECONDS);
    }

    private NFSDataStorage createStorage(final long id, final String path, final long fileShareMountId) {
        final NFSDataStorage storage = new NFSDataStorage(id, "storage-" + id, path);
        storage.setFileShareMountId(fileShareMountId);
        return storage;
    }

    private FileShareMount createFileShareMount(final long id, final String mountRoot) {
        final FileShareMount mount = new FileShareMount();
        mount.setId(id);
        mount.setMountRoot(mountRoot);
        mount.setMountType(MountType.NFS);
        mount.setRegionId(REGION_ID);
        return mount;
    }
}
