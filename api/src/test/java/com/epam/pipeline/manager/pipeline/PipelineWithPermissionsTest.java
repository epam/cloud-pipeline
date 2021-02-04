/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.pipeline;

import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineWithPermissions;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.security.acl.AclPermissionEntry;
import com.epam.pipeline.manager.EntityManager;
import com.epam.pipeline.manager.security.GrantPermissionManager;
import com.epam.pipeline.mapper.PipelineWithPermissionsMapper;
import com.epam.pipeline.security.acl.AclPermission;
import com.epam.pipeline.test.acl.AbstractAclTest;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.util.Arrays;

import static com.epam.pipeline.manager.ObjectCreatorUtils.constructPipeline;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;

public class PipelineWithPermissionsTest extends AbstractAclTest {
    private static final String TEST_FOLDER1 = "testFolder1";
    private static final String TEST_FOLDER2 = "testFolder2";
    private static final String TEST_OWNER1 = "owner1";
    private static final String TEST_OWNER2 = "owner2";
    private static final String TEST_PIPELINE1 = "Pipeline1";
    private static final String TEST_PIPELINE2 = "Pipeline2";
    private static final String TEST_PIPELINE_REPO = "///";
    private static final String TEST_PIPELINE_REPO_SSH = "git@test";
    private static final String TEST_USER_1 = "user1";
    private static final String TEST_USER_2 = "user2";

    @Autowired
    private EntityManager mockEntityManager;

    @Autowired
    private PipelineWithPermissionsMapper mockPipelineWithPermissionsMapper;

    @Autowired
    private GrantPermissionManager permissionManager;

    private Folder folder1;
    private Folder folder2;
    private Pipeline pipeline1;
    private Pipeline pipeline2;

    @Before
    public void setUp() {
        folder1 = getFolder(TEST_FOLDER1, TEST_OWNER1);
        folder1.setId(1L);

        folder2 = getFolder(TEST_FOLDER2, TEST_OWNER2);
        folder2.setId(2L);
        folder2.setParent(folder1);
        folder2.setParentId(folder1.getId());

        folder1.setChildFolders(Collections.singletonList(folder2));

        pipeline1 = constructPipeline(TEST_PIPELINE1, TEST_PIPELINE_REPO, TEST_PIPELINE_REPO_SSH, folder2.getId());
        pipeline1.setOwner(TEST_OWNER1);
        pipeline1.setId(1L);

        pipeline2 = constructPipeline(TEST_PIPELINE2, TEST_PIPELINE_REPO, TEST_PIPELINE_REPO_SSH, folder1.getId());
        pipeline2.setOwner(TEST_OWNER1);
        pipeline2.setId(2L);

        pipeline1.setParentFolderId(folder2.getId());
        pipeline2.setParentFolderId(folder1.getId());
        pipeline1.setParent(folder2);
        pipeline2.setParent(folder1);

        folder1.setPipelines(Collections.singletonList(pipeline2));
        folder2.setPipelines(Collections.singletonList(pipeline1));
    }

    @Test
    public void shouldReturnPermissionsWhenGrantedToPipelineCase1() {
        final List<Pair<AbstractSecuredEntity, List<AbstractGrantPermission>>> pipelinesAndFolders = Arrays.asList(
                new ImmutablePair<>(pipeline1, Collections.singletonList(
                        new UserPermission(TEST_USER_1, AclPermission.WRITE.getMask()))),
                new ImmutablePair<>(pipeline2, Collections.singletonList(
                        new UserPermission(TEST_USER_1, AclPermission.READ.getMask()))),
                new ImmutablePair<>(folder1, Collections.singletonList(
                        new UserPermission(TEST_USER_1, AclPermission.READ.getMask()))),
                new ImmutablePair<>(folder2, Collections.singletonList(
                        new UserPermission(TEST_USER_1, AclPermission.READ.getMask()))));

        mockPipelineAndFolders(pipelinesAndFolders);


        // key - pipelineId, value - <sid, mask>
        final Map<Long, Map<String, Integer>> expectedMap = new HashMap<>();

        expectedMap.put(pipeline1.getId(), Collections.singletonMap(TEST_USER_1.toUpperCase(), 5));
        expectedMap.put(pipeline2.getId(), Collections.singletonMap(TEST_USER_1.toUpperCase(), 1));

        assertPipelineWithPermissions(expectedMap);
    }

    @Test
    public void shouldReturnPermissionsWhenGrantedToPipelineCase2() {
        final List<Pair<AbstractSecuredEntity, List<AbstractGrantPermission>>> pipelinesAndFolders = Arrays.asList(
                new ImmutablePair<>(pipeline1, Arrays.asList(
                        new UserPermission(TEST_USER_1, AclPermission.WRITE.getMask()),
                        new UserPermission(TEST_USER_2, AclPermission.WRITE.getMask()))),
                new ImmutablePair<>(pipeline2,
                        Collections.singletonList(new UserPermission(TEST_USER_1, AclPermission.READ.getMask()))),
                new ImmutablePair<>(folder1,
                        Collections.singletonList(new UserPermission(TEST_USER_1, AclPermission.READ.getMask()))),
                new ImmutablePair<>(folder2,
                        Collections.singletonList(new UserPermission(TEST_USER_1, AclPermission.READ.getMask()))));

        mockPipelineAndFolders(pipelinesAndFolders);

        final Map<Long, Map<String, Integer>> expectedMap = new HashMap<>();
        final Map<String, Integer> permissionsForPipeline1 = new HashMap<>();
        final Map<String, Integer> permissionsForPipeline2 = new HashMap<>();

        permissionsForPipeline1.put(TEST_USER_1.toUpperCase(), 5);
        permissionsForPipeline1.put(TEST_USER_2.toUpperCase(), 4);
        permissionsForPipeline2.put(TEST_USER_1.toUpperCase(), 1);

        expectedMap.put(pipeline1.getId(), permissionsForPipeline1);
        expectedMap.put(pipeline2.getId(), permissionsForPipeline2);

        assertPipelineWithPermissions(expectedMap);
    }

    @Test
    public void shouldReturnPermissionsWhenGrantedToPipelineCase3() {
        final List<Pair<AbstractSecuredEntity, List<AbstractGrantPermission>>> pipelinesAndFolders = Arrays.asList(
                new ImmutablePair<>(pipeline1, Arrays.asList(
                        new UserPermission(TEST_USER_1, AclPermission.WRITE.getMask()),
                        new UserPermission(TEST_USER_2, AclPermission.WRITE.getMask()))),
                new ImmutablePair<>(pipeline2,
                        Collections.singletonList(new UserPermission(TEST_USER_1, AclPermission.READ.getMask()))),
                new ImmutablePair<>(folder1, Arrays.asList(
                        new UserPermission(TEST_USER_1, AclPermission.READ.getMask()),
                        new UserPermission(TEST_USER_2, AclPermission.READ.getMask()))),
                new ImmutablePair<>(folder2,
                        Collections.singletonList(new UserPermission(TEST_USER_1, AclPermission.READ.getMask()))));

        mockPipelineAndFolders(pipelinesAndFolders);

        final Map<Long, Map<String, Integer>> expectedMap = new HashMap<>();
        final Map<String, Integer> permissionsForPipeline1 = new HashMap<>();
        final Map<String, Integer> permissionsForPipeline2 = new HashMap<>();

        permissionsForPipeline1.put(TEST_USER_1.toUpperCase(), 5);
        permissionsForPipeline1.put(TEST_USER_2.toUpperCase(), 5);
        permissionsForPipeline2.put(TEST_USER_2.toUpperCase(), 1);
        permissionsForPipeline2.put(TEST_USER_1.toUpperCase(), 1);

        expectedMap.put(pipeline1.getId(), permissionsForPipeline1);
        expectedMap.put(pipeline2.getId(), permissionsForPipeline2);

        assertPipelineWithPermissions(expectedMap);
    }

    @Test
    public void shouldReturnPermissionsWhenGrantedToPipelineCase4() {
        final List<Pair<AbstractSecuredEntity, List<AbstractGrantPermission>>> pipelinesAndFolders = Arrays.asList(
                new ImmutablePair<>(pipeline1, Arrays.asList(
                        new UserPermission(TEST_USER_1, AclPermission.WRITE.getMask()),
                        new UserPermission(TEST_USER_2, AclPermission.WRITE.getMask()))),
                new ImmutablePair<>(pipeline2,
                        Collections.singletonList(new UserPermission(TEST_USER_1, AclPermission.READ.getMask()))),
                new ImmutablePair<>(folder1, Arrays.asList(
                        new UserPermission(TEST_USER_1, AclPermission.READ.getMask()),
                        new UserPermission(TEST_USER_2, AclPermission.NO_READ.getMask()))),
                new ImmutablePair<>(folder2, Arrays.asList(
                        new UserPermission(TEST_USER_1, AclPermission.READ.getMask()),
                        new UserPermission(TEST_USER_2, AclPermission.READ.getMask()))));

        mockPipelineAndFolders(pipelinesAndFolders);

        final Map<Long, Map<String, Integer>> expectedMap = new HashMap<>();
        final Map<String, Integer> permissionsForPipeline1 = new HashMap<>();
        final Map<String, Integer> permissionsForPipeline2 = new HashMap<>();

        permissionsForPipeline1.put(TEST_USER_2.toUpperCase(), 5);
        permissionsForPipeline1.put(TEST_USER_1.toUpperCase(), 5);
        permissionsForPipeline2.put(TEST_USER_2.toUpperCase(), 2);
        permissionsForPipeline2.put(TEST_USER_1.toUpperCase(), 1);

        expectedMap.put(pipeline1.getId(), permissionsForPipeline1);
        expectedMap.put(pipeline2.getId(), permissionsForPipeline2);

        assertPipelineWithPermissions(expectedMap);
    }

    @Test
    public void shouldReturnPermissionsWhenGrantedToPipelineCase5() {
        final List<Pair<AbstractSecuredEntity, List<AbstractGrantPermission>>> pipelinesAndFolders = Arrays.asList(
                new ImmutablePair<>(pipeline1, Arrays.asList(
                        new UserPermission(TEST_USER_1, AclPermission.WRITE.getMask()),
                        new UserPermission(TEST_USER_2, AclPermission.NO_WRITE.getMask()))),
                new ImmutablePair<>(pipeline2,
                        Collections.singletonList(new UserPermission(TEST_USER_1, AclPermission.READ.getMask()))),
                new ImmutablePair<>(folder1, Arrays.asList(
                        new UserPermission(TEST_USER_1, AclPermission.READ.getMask()),
                        new UserPermission(TEST_USER_2, AclPermission.WRITE.getMask()))),
                new ImmutablePair<>(folder2, Arrays.asList(
                        new UserPermission(TEST_USER_1, AclPermission.READ.getMask()),
                        new UserPermission(TEST_USER_2, AclPermission.READ.getMask()))));

        mockPipelineAndFolders(pipelinesAndFolders);

        final Map<Long, Map<String, Integer>> expectedMap = new HashMap<>();
        final Map<String, Integer> permissionsForPipeline1 = new HashMap<>();
        final Map<String, Integer> permissionsForPipeline2 = new HashMap<>();

        permissionsForPipeline1.put(TEST_USER_2.toUpperCase(), 9);
        permissionsForPipeline1.put(TEST_USER_1.toUpperCase(), 5);
        permissionsForPipeline2.put(TEST_USER_1.toUpperCase(), 1);
        permissionsForPipeline2.put(TEST_USER_2.toUpperCase(), 4);

        expectedMap.put(pipeline1.getId(), permissionsForPipeline1);
        expectedMap.put(pipeline2.getId(), permissionsForPipeline2);

        assertPipelineWithPermissions(expectedMap);
    }


    private void assertPipelineWithPermissions(Map<Long, Map<String, Integer>> expectedMap) {
        Set<PipelineWithPermissions> loaded = permissionManager.loadAllPipelinesWithPermissions(null, null)
                .getPipelines();
        loaded.forEach(pipelineWithPermissions -> {
            Map<String, Integer> expectedPermissions = expectedMap.get(pipelineWithPermissions.getId());
            Set<AclPermissionEntry> actualPermissions = pipelineWithPermissions.getPermissions();
            assertEquals(expectedPermissions.size(), actualPermissions.size());
            actualPermissions.forEach(permission -> {
                String sid = permission.getSid().getName().toUpperCase();
                assertEquals(expectedPermissions.get(sid), permission.getMask());
            });
        });
    }

    private void mockPipelineAndFolders(
            List<Pair<AbstractSecuredEntity, List<AbstractGrantPermission>>> pipelinesAndFolders) {

        initAclEntity(pipelinesAndFolders);

        doReturn(Arrays.asList(pipeline1, pipeline2))
                .when(mockEntityManager).loadAllWithParents(eq(AclClass.PIPELINE), any(), any());
        doReturn(Arrays.asList(folder1, folder2))
                .when(mockEntityManager).loadAllWithParents(eq(AclClass.FOLDER), any(), any());

        final PipelineWithPermissions pipelineWithPermissions1 = new PipelineWithPermissions();
        pipelineWithPermissions1.setId(pipeline1.getId());
        pipelineWithPermissions1.setOwner(pipeline1.getOwner());
        doReturn(pipelineWithPermissions1)
                .when(mockPipelineWithPermissionsMapper).toPipelineWithPermissions(eq(pipeline1));

        final PipelineWithPermissions pipelineWithPermissions2 = new PipelineWithPermissions();
        pipelineWithPermissions2.setId(pipeline2.getId());
        pipelineWithPermissions2.setOwner(pipeline2.getOwner());
        doReturn(pipelineWithPermissions2)
                .when(mockPipelineWithPermissionsMapper).toPipelineWithPermissions(eq(pipeline2));

        doReturn(2).when(mockEntityManager).loadTotalCount(eq(AclClass.PIPELINE));
        doReturn(2).when(mockEntityManager).loadTotalCount(AclClass.FOLDER);
    }

    private Folder getFolder(String name, String owner) {
        Folder folder = new Folder();
        folder.setName(name);
        folder.setOwner(owner);
        return folder;
    }
}
