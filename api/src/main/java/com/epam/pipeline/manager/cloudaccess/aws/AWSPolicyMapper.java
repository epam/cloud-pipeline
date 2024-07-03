/*
 * Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.cloudaccess.aws;

import com.epam.pipeline.entity.cloudaccess.policy.CloudAccessPolicyAction;
import com.epam.pipeline.entity.cloudaccess.policy.CloudAccessPolicyEffect;
import com.epam.pipeline.entity.cloudaccess.policy.CloudAccessPolicy;
import com.epam.pipeline.entity.cloudaccess.policy.CloudAccessPolicyStatement;
import com.epam.pipeline.entity.cloudaccess.policy.aws.AWSAccessPolicyDocument;
import com.epam.pipeline.entity.cloudaccess.policy.aws.AWSAccessPolicyStatement;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Class to map cloud-pipeline specific object {@link CloudAccessPolicy} to AWS specific policy document and vise versa
 *
 * e.g. For each object of {@link CloudAccessPolicyStatement} wi will create number of AWS policy statements.
 * For example to provide full READ access to specific bucket we actually need to grant to user policy not only with
 * actions: AWS_S3_OBJECT_READ_ACTIONS but also AWS_S3_BUCKET_READ_ACTIONS to be able to list bucket.
 * */
public final class AWSPolicyMapper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static final String AWS_IAM_API_VERSION = "2012-10-17";
    public static final String AWS_EFFECT = "Effect";
    public static final String AWS_ACTION = "Action";
    public static final String AWS_RESOURCE = "Resource";
    public static final String AWS_STATEMENT = "Statement";
    public static final Pattern AWS_S3_ANY_RESOURCE_PATTERN = Pattern.compile("arn:aws:s3:::([a-z\\d\\-]{3,63})/?.*");
    public static final Pattern AWS_S3_BUCKET_PATTERN = Pattern.compile("arn:aws:s3:::([a-z\\d\\-]{3,63})");
    public static final Pattern AWS_S3_OBJECTS_PATTERN = Pattern.compile("arn:aws:s3:::([a-z\\d\\-]{3,63})/.*");
    public static final Set<String> AWS_S3_OBJECT_READ_ACTIONS = new HashSet<>(
            Arrays.asList("s3:GetObject", "s3:GetObjectAcl"));
    public static final Set<String> AWS_S3_BUCKET_READ_ACTIONS = new HashSet<>(
            Arrays.asList("s3:ListBucket", "s3:GetBucketLocation"));
    public static final Set<String> AWS_S3_OBJECT_WRITE_ACTIONS = new HashSet<>(
            Arrays.asList("s3:PutObject", "s3:PutObjectAcl", "s3:DeleteObject"));

    public static final Set<String> AWS_KMS_ACTIONS = new HashSet<>(
            Arrays.asList("kms:Decrypt", "kms:Encrypt", "kms:DescribeKey", "kms:ReEncrypt*",
                    "kms:GenerateDataKey*", "kms:ListKeys"));
    public static final String ANY_RESOURCE = "*";

    private AWSPolicyMapper() {
    }


    public static CloudAccessPolicy toCloudUserAccessPolicy(final String policyName,
                                                            final String policyDocument) {
        return CloudAccessPolicy.builder().name(policyName)
                .statements(parsePolicyDocumentToCloudPolicyStatements(policyDocument)).build();
    }

    public static String toPolicyDocument(final CloudAccessPolicy policy) {
        try {
            final List<AWSAccessPolicyStatement> statements = policy
                    .getStatements()
                    .stream()
                    .flatMap(statement -> mapToAWSPolicyStatements(statement).stream())
                    .collect(Collectors.toList());
            return OBJECT_MAPPER.writeValueAsString(AWSAccessPolicyDocument.builder()
                    .version(AWS_IAM_API_VERSION)
                    .statements(statements).build());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<CloudAccessPolicyStatement> parsePolicyDocumentToCloudPolicyStatements(
            final String policyDocument) {
        try {
            final Map<String, List<AWSAccessPolicyStatement>> statementsByResource =
                    parseAndGroupPolicyDocumentByResource(policyDocument);

            // Go through statements grouped by storage (f.e. statements for read and write access for same bucket)
            // check if there are statements for READ access (list bucket + read objects)
            // and WRITE access (write objects)
            return statementsByResource.entrySet()
                    .stream()
                    .map(statementsEntry -> {
                        final String storage = statementsEntry.getKey();
                        final List<AWSAccessPolicyStatement> statements = statementsEntry.getValue();
                        final List<CloudAccessPolicyAction> actions = new ArrayList<>();

                        boolean hasBucketRead = statements.stream()
                                .filter(awsAccessPolicyStatement ->
                                        CloudAccessPolicyEffect.ALLOW.awsValue.equals(
                                                awsAccessPolicyStatement.getEffect())
                                ).filter(st -> AWS_S3_BUCKET_READ_ACTIONS.containsAll(st.getActions()))
                                .anyMatch(st -> AWS_S3_BUCKET_PATTERN.matcher(st.getResource()).find());

                        boolean hasBucketObjectRead = statements.stream()
                                .filter(awsAccessPolicyStatement ->
                                        CloudAccessPolicyEffect.ALLOW.awsValue.equals(
                                                awsAccessPolicyStatement.getEffect())
                                ).filter(st -> AWS_S3_OBJECT_READ_ACTIONS.containsAll(st.getActions()))
                                .anyMatch(st -> AWS_S3_OBJECTS_PATTERN.matcher(st.getResource()).find());

                        if (hasBucketRead && hasBucketObjectRead) {
                            actions.add(CloudAccessPolicyAction.READ);
                        }

                        boolean hasBucketObjectWrite = statements.stream()
                                .filter(awsAccessPolicyStatement ->
                                        CloudAccessPolicyEffect.ALLOW.awsValue.equals(
                                                awsAccessPolicyStatement.getEffect())
                                ).filter(st -> AWS_S3_OBJECT_WRITE_ACTIONS.containsAll(st.getActions()))
                                .anyMatch(st -> AWS_S3_OBJECTS_PATTERN.matcher(st.getResource()).find());

                        if (hasBucketObjectWrite) {
                            actions.add(CloudAccessPolicyAction.WRITE);
                        }

                        return !actions.isEmpty()
                                ? CloudAccessPolicyStatement.builder().actions(actions)
                                    .effect(CloudAccessPolicyEffect.ALLOW).resource(storage).build()
                                : null;
                    }).filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static List<AWSAccessPolicyStatement> mapToAWSPolicyStatements(
            final CloudAccessPolicyStatement statement) {
        return statement.getActions().stream().flatMap(action -> {
            switch (action){
                case WRITE:
                    return Stream.of(
                            mapToAwsPolicyStatement(
                                    statement.getResource(),
                                    AWS_S3_OBJECT_WRITE_ACTIONS,
                                    AWSPolicyMapper::mapToS3BucketInternalResources
                            )
                    );
                case READ:
                    return Stream.of(
                            mapToAwsPolicyStatement(
                                    statement.getResource(),
                                    AWS_S3_OBJECT_READ_ACTIONS,
                                    AWSPolicyMapper::mapToS3BucketInternalResources
                            ),
                            mapToAwsPolicyStatement(
                                    statement.getResource(),
                                    AWS_S3_BUCKET_READ_ACTIONS,
                                    AWSPolicyMapper::mapToS3Bucket
                            )
                    );
                default:
                    throw new IllegalArgumentException(String.format("Unsupported action: %s", action));
            }
        }).collect(Collectors.toList());
    }

    public static String getKmsPolicyDocument() {
        try {
            return OBJECT_MAPPER.writeValueAsString(AWSAccessPolicyDocument.builder()
                    .version(AWS_IAM_API_VERSION)
                    .statements(Collections.singletonList(
                            mapToAwsPolicyStatement(
                                    ANY_RESOURCE,
                                    AWS_KMS_ACTIONS
                            )
                    )).build());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, List<AWSAccessPolicyStatement>> parseAndGroupPolicyDocumentByResource(
            final String policyDocument) throws IOException {

        final JsonNode statementsNode = Optional.ofNullable(OBJECT_MAPPER.readTree(policyDocument).get(AWS_STATEMENT))
                .filter(JsonNode::isArray)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Policy document can't be parsed: " + policyDocument));

        return StreamSupport
            .stream(
                    Spliterators.spliteratorUnknownSize(statementsNode.elements(), Spliterator.ORDERED),
                    false
            ).map(statement ->
                    AWSAccessPolicyStatement.builder()
                            .effect(statement.get(AWS_EFFECT).asText())
                            .actions(
                                    StreamSupport.stream(
                                            Spliterators.spliteratorUnknownSize(
                                                    statement.get(AWS_ACTION).elements(),
                                                    Spliterator.ORDERED
                                            ), false
                                    ).map(JsonNode::asText).collect(Collectors.toSet())
                            )
                            .resource(statement.get(AWS_RESOURCE).asText())
                            .build()
            ).collect(Collectors.groupingBy(
                statement -> parseStorageNameFromAWSResourcePolicy(statement.getResource())));
    }

    private static AWSAccessPolicyStatement mapToAwsPolicyStatement(final String resource,
                                                                    final Set<String> actions,
                                                                    final Function<String, String> resourceMapper) {
        return AWSAccessPolicyStatement.builder()
                .effect(CloudAccessPolicyEffect.ALLOW.awsValue)
                .actions(actions)
                .resource(resourceMapper.apply(resource))
                .build();
    }

    private static AWSAccessPolicyStatement mapToAwsPolicyStatement(final String resource, final Set<String> actions) {
        return AWSAccessPolicyStatement.builder()
                .effect(CloudAccessPolicyEffect.ALLOW.awsValue)
                .actions(actions)
                .resource(resource)
                .build();
    }

    private static String mapToS3BucketInternalResources(final String resource) {
        return String.format("arn:aws:s3:::%s/*", resource);
    }

    private static String mapToS3Bucket(final String resource) {
        return String.format("arn:aws:s3:::%s", resource);
    }

    private static String parseStorageNameFromAWSResourcePolicy(final String awsResource) {
        final Matcher resourceMatcher = AWS_S3_ANY_RESOURCE_PATTERN.matcher(awsResource);
        if (resourceMatcher.find()) {
            return resourceMatcher.group(1);
        } else {
            throw new IllegalArgumentException(String.format("Can't parse aws resource arn: %s", awsResource));
        }
    }
}
