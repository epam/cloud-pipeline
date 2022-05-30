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

import com.epam.pipeline.entity.cloudaccess.policy.CloudAccessPolicy;
import com.epam.pipeline.entity.cloudaccess.policy.CloudAccessPolicyAction;
import com.epam.pipeline.entity.cloudaccess.policy.CloudAccessPolicyEffect;
import com.epam.pipeline.entity.cloudaccess.policy.CloudAccessPolicyStatement;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;

@SuppressWarnings("PMD.AvoidDuplicateLiterals")
public class AWSPolicyMapperTest {

    @Test
    public void testToCloudUserAccessPolicySuccessReadPolicy() {
        String policy =  "{" +
                "\"Statement\": [" +
                "      {" +
                "        \"Effect\": \"Allow\"," +
                "        \"Action\": [" +
                "          \"s3:ListBucket\", \"s3:GetBucketLocation\"" +
                "        ]," +
                "        \"Resource\": \"arn:aws:s3:::bucket\"" +
                "      }," +
                "      {" +
                "        \"Effect\": \"Allow\"," +
                "        \"Action\": [" +
                "          \"s3:GetObject\", \"s3:GetObjectAcl\"" +
                "        ]," +
                "        \"Resource\": \"arn:aws:s3:::bucket/*\"" +
                "      }" +
                "    ]" +
                "}";
        CloudAccessPolicy cloudAccessPolicy = AWSPolicyMapper.toCloudUserAccessPolicy("", policy);
        Assert.assertFalse(cloudAccessPolicy.getStatements().isEmpty());
        Assert.assertFalse(cloudAccessPolicy.getStatements().get(0).getActions().isEmpty());
        Assert.assertEquals(cloudAccessPolicy.getStatements().get(0).getActions().get(0), CloudAccessPolicyAction.READ);
    }

    @Test
    public void testToCloudUserAccessPolicySuccessWritePolicy() {
        String policy =  "{" +
                "\"Statement\": [" +
                "      {" +
                "        \"Effect\": \"Allow\"," +
                "        \"Action\": [" +
                "          \"s3:PutObject\", \"s3:PutObjectAcl\", \"s3:DeleteObject\"" +
                "        ]," +
                "        \"Resource\": \"arn:aws:s3:::bucket/*\"" +
                "      }" +
                "    ]" +
                "}";
        CloudAccessPolicy cloudAccessPolicy = AWSPolicyMapper.toCloudUserAccessPolicy("", policy);
        Assert.assertFalse(cloudAccessPolicy.getStatements().isEmpty());
        Assert.assertFalse(cloudAccessPolicy.getStatements().get(0).getActions().isEmpty());
        Assert.assertEquals(cloudAccessPolicy.getStatements().get(0).getActions().get(0),
                CloudAccessPolicyAction.WRITE);
    }

    @Test
    public void testAWSPolicyMapperWillCreateEmptyAccessPolicyBecauseOfDeny() {
        String policy =  "{" +
                "\"Statement\": [" +
                "      {" +
                "        \"Effect\": \"Deny\"," +
                "        \"Action\": [" +
                "          \"s3:ListBucket\", \"s3:GetBucketLocation\"" +
                "        ]," +
                "        \"Resource\": \"arn:aws:s3:::bucket\"" +
                "      }," +
                "      {" +
                "        \"Effect\": \"Allow\"," +
                "        \"Action\": [" +
                "          \"s3:GetObject\", \"s3:GetObjectAcl\"" +
                "        ]," +
                "        \"Resource\": \"arn:aws:s3:::bucket/*\"" +
                "      }" +
                "    ]" +
                "}";
        CloudAccessPolicy cloudAccessPolicy = AWSPolicyMapper.toCloudUserAccessPolicy("", policy);
        Assert.assertTrue(cloudAccessPolicy.getStatements().isEmpty());
    }

    @Test
    public void testToCloudUserAccessPolicyWillCreateEmptyPolicy() {
        String policy =  "{" +
                "\"Statement\": [" +
                "      {" +
                "        \"Effect\": \"Allow\"," +
                "        \"Action\": [" +
                "          \"s3:ListBucket\", \"s3:GetBucketLocation\"" +
                "        ]," +
                "        \"Resource\": \"arn:aws:s3:::bucket\"" +
                "      }" +
                "    ]" +
                "}";
        CloudAccessPolicy cloudAccessPolicy = AWSPolicyMapper.toCloudUserAccessPolicy("", policy);
        Assert.assertTrue(cloudAccessPolicy.getStatements().isEmpty());
    }

    @Test
    public void testToPolicyDocumentWithReadPermissions() {
        CloudAccessPolicy accessPolicy = CloudAccessPolicy.builder()
                .statements(Collections.singletonList(
                        CloudAccessPolicyStatement.builder()
                                .effect(CloudAccessPolicyEffect.ALLOW)
                                .actions(Collections.singletonList(CloudAccessPolicyAction.READ))
                                .resource("bucket")
                                .build())
                ).build();

        String policy = AWSPolicyMapper.toPolicyDocument(accessPolicy);
        Assert.assertEquals("{" +
                "\"Version\":\"2012-10-17\"," +
                "\"Statement\":[" +
                "{" +
                "\"Effect\":\"Allow\"," +
                "\"Action\":[\"s3:GetObject\",\"s3:GetObjectAcl\"]," +
                "\"Resource\":\"arn:aws:s3:::bucket/*\"" +
                "}," +
                "{" +
                "\"Effect\":\"Allow\"," +
                "\"Action\":[\"s3:ListBucket\",\"s3:GetBucketLocation\"]," +
                "\"Resource\":\"arn:aws:s3:::bucket\"" +
                "}" +
                "]" +
                "}", policy);
    }

    @Test
    public void testToPolicyDocumentWithWritePermissions() {
        CloudAccessPolicy accessPolicy = CloudAccessPolicy.builder()
                .statements(Collections.singletonList(
                        CloudAccessPolicyStatement.builder()
                                .effect(CloudAccessPolicyEffect.ALLOW)
                                .actions(Collections.singletonList(CloudAccessPolicyAction.WRITE))
                                .resource("bucket")
                                .build())
                ).build();

        String policy = AWSPolicyMapper.toPolicyDocument(accessPolicy);
        Assert.assertEquals("{" +
                "\"Version\":\"2012-10-17\"," +
                "\"Statement\":[" +
                "{" +
                "\"Effect\":\"Allow\"," +
                "\"Action\":[\"s3:PutObject\",\"s3:DeleteObject\",\"s3:PutObjectAcl\"]," +
                "\"Resource\":\"arn:aws:s3:::bucket/*\"" +
                "}" +
                "]" +
                "}", policy);
    }

}
