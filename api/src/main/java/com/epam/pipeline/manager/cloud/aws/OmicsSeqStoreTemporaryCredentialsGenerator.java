/*
 * Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.aws.AWSOmicsSequenceDataStorage;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OmicsSeqStoreTemporaryCredentialsGenerator
        extends AbstractAWSTemporaryCredentialsGenerator<AWSOmicsSequenceDataStorage> {

    private static final String GET_READSET_ACTION = "omics:GetReadSet*";
    private static final String LIST_READSET_ACTION = "omics:ListReadSet*";
    private static final String START_READSET_ACTION = "omics:StartReadSet*";
    private static final String ABORT_READSET_ACTION = "omics:Abort*ReadSet*";
    private static final String CREATE_READSET_ACTION = "omics:Create*ReadSet*";
    private static final String UPLOAD_READSET_ACTION = "omics:UploadReadSet*";
    private static final String DELETE_OBJECT_ACTION = "omics:*DeleteReadSet*";
    private static final String COMPLETE_READSET_ACTION = "omics:Complete*ReadSet*";

    private static final Pattern AWS_OMICS_PATH_PATTERN
            = Pattern.compile("(?<account>.*).storage.(?<region>.*).amazonaws.com/(?<store>.*)/readSet");
    private static final String AWS_OMICS_STORE_ARN_TEMPLATE = "arn:aws:omics:%s:%s:sequenceStore/%s";

    public OmicsSeqStoreTemporaryCredentialsGenerator(final CloudRegionManager cloudRegionManager,
                                                      final PreferenceManager preferenceManager,
                                                      final MessageHelper messageHelper) {
        super(cloudRegionManager, preferenceManager, messageHelper);
    }

    @Override
    public DataStorageType getStorageType() {
        return DataStorageType.AWS_OMICS_SEQ;
    }

    @Override
    void addListingPermissions(final DataStorageAction action, final ArrayNode statements) {
        final ObjectNode statement = getStatement();
        final ArrayNode actions = statement.putArray(ACTION);
        actions.add(LIST_READSET_ACTION);
        final ArrayNode resource = statement.putArray(RESOURCE);
        buildOmicsArn(action).forEach(resource::add);
        statements.add(statement);
    }

    @Override
    void addActionToStatement(final DataStorageAction action, final ArrayNode statements) {
        final ObjectNode statement = getStatement();
        final ArrayNode actions = statement.putArray(ACTION);
        if (action.isRead()) {
            actions.add(GET_READSET_ACTION);
            actions.add(LIST_READSET_ACTION);
        }
        if (action.isWrite()) {
            actions.add(START_READSET_ACTION);
            actions.add(CREATE_READSET_ACTION);
            actions.add(ABORT_READSET_ACTION);
            actions.add(UPLOAD_READSET_ACTION);
            actions.add(DELETE_OBJECT_ACTION);
            actions.add(COMPLETE_READSET_ACTION);
        }
        final ArrayNode resource = statement.putArray(RESOURCE);
        buildOmicsArn(action).forEach(resource::add);
        statements.add(statement);
    }

    private List<String> buildOmicsArn(final DataStorageAction action) {
        final Matcher omicsARNMatcher = AWS_OMICS_PATH_PATTERN.matcher(action.getPath());
        if (omicsARNMatcher.find()) {
            return Arrays.asList(
                String.format(AWS_OMICS_STORE_ARN_TEMPLATE,
                    omicsARNMatcher.group("region"), omicsARNMatcher.group("account"),
                    omicsARNMatcher.group("store")
                ),
                String.format(AWS_OMICS_STORE_ARN_TEMPLATE,
                        omicsARNMatcher.group("region"), omicsARNMatcher.group("account"),
                        omicsARNMatcher.group("store") + "/*"
                )
            );
        } else {
            throw new IllegalArgumentException();
        }
    }

}
