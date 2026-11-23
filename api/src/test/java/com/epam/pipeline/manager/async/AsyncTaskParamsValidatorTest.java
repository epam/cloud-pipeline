/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.manager.async;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dto.async.AsyncTaskParams;
import com.epam.pipeline.dto.async.UnpackArchiveParams;
import com.epam.pipeline.manager.security.storage.StoragePermissionManager;
import com.epam.pipeline.security.acl.AclPermission;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class AsyncTaskParamsValidatorTest {

    private static final Long STORAGE_ID = 1L;
    private static final String ARCHIVE = "incoming/data.zip";

    private final StoragePermissionManager storagePermissionManager =
            Mockito.mock(StoragePermissionManager.class);
    private final MessageHelper messageHelper = Mockito.mock(MessageHelper.class);

    private final AsyncTaskParamsValidator validator =
            new AsyncTaskParamsValidator(storagePermissionManager, messageHelper);

    private final AsyncTaskParams params = UnpackArchiveParams.builder().build();

    @Test
    public void shouldReturnAFilePathAsTheCallerWroteIt() {
        assertEquals(ARCHIVE, validator.asFilePath(ARCHIVE));
        assertEquals("data.zip", validator.asFilePath("data.zip"));
        assertEquals("a/b/c/data.tar.gz", validator.asFilePath("a/b/c/data.tar.gz"));
    }

    @Test
    public void shouldRefuseAFolderAsAFilePath() {
        assertThrows(() -> validator.asFilePath("incoming/"));
    }

    @Test
    public void shouldAddOneTrailingSlashToAFolderPrefix() {
        assertEquals("out/", validator.asFolderPrefix("out"));
        assertEquals("out/", validator.asFolderPrefix("out/"));
        assertEquals("a/b/", validator.asFolderPrefix("a/b"));
    }

    @Test
    public void shouldTreatAnEmptyFolderPathAsTheRootOfTheStorage() {
        assertEquals("", validator.asFolderPrefix(""));
        assertEquals("", validator.asFolderPrefix("   "));
        assertEquals("", validator.asFolderPrefix(null));
    }

    @Test
    public void shouldRefuseEverySlashSpellingOfTheRoot() {
        for (final String spelling : List.of("/", "//", ".", "./", "/.", "/./")) {
            assertThrows(() -> validator.asFolderPrefix(spelling));
        }
    }

    @Test
    public void shouldRefuseAPathThatEscapesItsStorage() {
        for (final String escaping : List.of("../../etc/passwd", "a/../../b.zip", "..", "a/..",
                "../a.zip")) {
            assertThrows(() -> validator.asFilePath(escaping));
            assertThrows(() -> validator.asFolderPrefix(escaping));
        }
    }

    @Test
    public void shouldRefuseALeadingSlashRatherThanStripIt() {
        assertThrows(() -> validator.asFilePath("/" + ARCHIVE));
        assertThrows(() -> validator.asFolderPrefix("/out"));
    }

    @Test
    public void shouldRefuseADriveLetterHiddenBehindALeadingSlash() {
        for (final String disguised : List.of("/C:/Windows/Temp", "//C:/evil.zip", "/c:evil")) {
            assertThrows(() -> validator.asFilePath(disguised));
        }
    }

    @Test
    public void shouldRefuseTheSpellingsAFilesystemWouldCollapse() {
        for (final String spelling : List.of("pub//data.zip", "pub/./data.zip", "./pub/data.zip")) {
            assertThrows(() -> validator.asFilePath(spelling));
            assertThrows(() -> validator.asFolderPrefix(spelling));
        }
    }

    @Test
    public void shouldRefuseALeadingTilde() {
        for (final String tilde : List.of("~/data.zip", "~root/.ssh/id_rsa", "~")) {
            assertThrows(() -> validator.asFilePath(tilde));
        }
    }

    @Test
    public void shouldAllowATildeThatIsNotLeading() {
        assertEquals("incoming/data~.zip", validator.asFilePath("incoming/data~.zip"));
    }

    @Test
    public void shouldRefuseAPathWithAControlCharacterOrLineTerminator() {
        for (final String hostile : List.of("data\u0000.zip", "data\n/etc/passwd", "data\r\n.zip",
                "data\u0085/etc/passwd", "data\u2028.zip", "data\u2029.zip")) {
            assertThrows(() -> validator.asFilePath(hostile));
        }
    }

    @Test
    public void shouldRefuseABlankFilePath() {
        assertThrows(() -> validator.asFilePath(""));
        assertThrows(() -> validator.asFilePath("   "));
        assertThrows(() -> validator.asFilePath(null));
    }

    @Test
    public void shouldAllowDotsInsideANameRatherThanRejectingTheSubstring() {
        for (final String ordinary : List.of("incoming/my..file.txt", "incoming/v1..v2/data.zip",
                "incoming/data...zip", "..leading-dots.zip")) {
            assertEquals(ordinary, validator.asFilePath(ordinary));
        }
    }

    @Test
    public void shouldAcceptWhatTheExecutorIsNowResponsibleFor() {
        for (final String tolerated : List.of("a\\b.zip", "C:/data.zip", "incoming/.. /data.zip",
                "incoming/.../data.zip", "invoice\u202Efdp.zip", "data\uFEFF.zip")) {
            assertEquals(tolerated, validator.asFilePath(tolerated));
        }
    }

    @Test
    public void shouldRefuseWhenTheStoragePermissionIsNotGranted() {
        Mockito.when(storagePermissionManager.hasStoragePermission(STORAGE_ID, AclPermission.READ_NAME))
                .thenReturn(false);

        assertThrows(() -> validator.assertStoragePermission(STORAGE_ID, AclPermission.READ_NAME));
    }

    @Test
    public void shouldAllowWhenTheStoragePermissionIsGranted() {
        Mockito.when(storagePermissionManager.hasStoragePermission(STORAGE_ID, AclPermission.WRITE_NAME))
                .thenReturn(true);

        validator.assertStoragePermission(STORAGE_ID, AclPermission.WRITE_NAME);

        Mockito.verify(storagePermissionManager).hasStoragePermission(STORAGE_ID, AclPermission.WRITE_NAME);
    }

    @Test
    public void shouldNameTheFieldAndTheTaskTypeInTheRequiredMessage() {
        validator.fieldRequiredMessage(params, "sourcePath");

        Mockito.verify(messageHelper).getMessage(Mockito.anyString(), Mockito.eq("sourcePath"),
                Mockito.eq(params.getType()));
    }
}
