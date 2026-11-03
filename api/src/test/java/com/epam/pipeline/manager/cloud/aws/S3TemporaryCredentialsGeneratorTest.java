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

package com.epam.pipeline.manager.cloud.aws;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.datastorage.DataStorageAction;
import com.epam.pipeline.entity.datastorage.DataStorageItemType;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class S3TemporaryCredentialsGeneratorTest {

    private static final String BUCKET = "vol-test-abcdef-ext-s3alias";
    private static final String PREFIX = "prefix";
    private static final String FILE = "dir/file.txt";
    private static final String ACCESS_POINT_ARN = "arn:aws:s3:eu-west-3:123456789012:accesspoint/vol-test";
    private static final String BUCKET_ARN = "arn:aws:s3:::" + BUCKET;
    private static final String LIST_BUCKET = "s3:ListBucket";
    private static final String GET_OBJECT = "s3:GetObject";

    private final S3TemporaryCredentialsGenerator generator = new S3TemporaryCredentialsGenerator(
            mock(CloudRegionManager.class), mock(PreferenceManager.class), mock(MessageHelper.class));
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void shouldGrantOnlyBucketResourcesWithoutAccessPoint() throws IOException {
        final List<JsonNode> statements = policyStatements(readAction(BUCKET, null));

        assertEquals(2, statements.size());
        assertEquals(Collections.singletonList(BUCKET_ARN), resources(statements.get(0)));
        assertEquals(Collections.singletonList(BUCKET_ARN + "/*"), resources(statements.get(1)));
    }

    @Test
    public void shouldGrantAccessPointResourcesForWholeBucket() throws IOException {
        final List<JsonNode> statements = policyStatements(readAction(BUCKET, ACCESS_POINT_ARN));

        assertEquals(2, statements.size());
        assertEquals(Collections.singletonList(LIST_BUCKET), actions(statements.get(0)));
        assertEquals(Arrays.asList(BUCKET_ARN, ACCESS_POINT_ARN), resources(statements.get(0)));
        assertEquals(GET_OBJECT, actions(statements.get(1)).get(0));
        assertEquals(Arrays.asList(BUCKET_ARN + "/*", ACCESS_POINT_ARN + "/object/*"),
                resources(statements.get(1)));
    }

    @Test
    public void shouldGrantAccessPointResourcesForStoragePrefix() throws IOException {
        final List<JsonNode> statements = policyStatements(
                readAction(BUCKET + "/" + PREFIX, ACCESS_POINT_ARN));

        assertEquals(Arrays.asList(BUCKET_ARN, ACCESS_POINT_ARN), resources(statements.get(0)));
        assertEquals(Arrays.asList(BUCKET_ARN + "/" + PREFIX + "/*",
                        ACCESS_POINT_ARN + "/object/" + PREFIX + "/*"),
                resources(statements.get(1)));
    }

    @Test
    public void shouldGrantAccessPointResourcesForFileItem() throws IOException {
        final DataStorageAction action = readAction(BUCKET + "/" + PREFIX, ACCESS_POINT_ARN);
        action.setItemPath(FILE);
        action.setItemType(DataStorageItemType.File);

        final List<JsonNode> statements = policyStatements(action);

        assertEquals(Arrays.asList(BUCKET_ARN, ACCESS_POINT_ARN), resources(statements.get(0)));
        assertEquals(FILE, statements.get(0).path("Condition").path("StringLike").path("s3:prefix").asText());
        assertEquals(Arrays.asList(BUCKET_ARN + "/" + PREFIX + "/" + FILE,
                        ACCESS_POINT_ARN + "/object/" + PREFIX + "/" + FILE),
                resources(statements.get(1)));
    }

    @Test
    public void shouldGrantOnlyAccessPointListingForListAction() throws IOException {
        final DataStorageAction action = readAction(BUCKET, ACCESS_POINT_ARN);
        action.setRead(false);
        action.setList(true);

        final List<JsonNode> statements = policyStatements(action);

        assertEquals(1, statements.size());
        assertEquals(Arrays.asList(BUCKET_ARN, ACCESS_POINT_ARN), resources(statements.get(0)));
    }

    private DataStorageAction readAction(final String path, final String accessPointArn) {
        final DataStorageAction action = new DataStorageAction();
        action.setId(1L);
        action.setRead(true);
        action.setBucketName(BUCKET);
        action.setPath(path);
        action.setAccessPointArn(accessPointArn);
        return action;
    }

    private List<JsonNode> policyStatements(final DataStorageAction action) throws IOException {
        final String policy = generator.createPolicyWithPermissions(
                Collections.singletonList(action), Collections.emptyList());
        final List<JsonNode> statements = new ArrayList<>();
        objectMapper.readTree(policy).path("Statement").forEach(statements::add);
        return statements;
    }

    private List<String> resources(final JsonNode statement) {
        return values(statement.path("Resource"));
    }

    private List<String> actions(final JsonNode statement) {
        return values(statement.path("Action"));
    }

    private List<String> values(final JsonNode array) {
        final List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.asText()));
        return values;
    }
}
