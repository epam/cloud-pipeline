#!/bin/bash
# Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

echo "SQS trigger started"

while :
do
    sleep $SQS_POLL_SEC

    MESSAGES=$(aws sqs receive-message --queue-url "$SQS_QUEUE" \
                                        --max-number-of-messages 1 \
                                        --visibility-timeout 30)
    if [ -z "$MESSAGES" ]; then
        continue
    fi

    echo "Processing message"
    RECEIPT_HANDLE=$(jq -r '.Messages[].ReceiptHandle' <<< $MESSAGES)
    BODY=$(jq -r '.Messages[].Body | fromjson.Records[]' <<< $MESSAGES)
    BUCKET_NAME=$(jq -r '.s3.bucket.name' <<< $BODY)
    DISTRIBUTION_PREFIX=$(jq -r '.s3.object.key' <<< $BODY)
    DISTRIBUTION_URL="https://s3.amazonaws.com/$BUCKET_NAME/$DISTRIBUTION_PREFIX"

    echo "RECEIPT_HANDLE: $RECEIPT_HANDLE"
    echo "BUCKET_NAME: $BUCKET_NAME"
    echo "DISTRIBUTION_PREFIX: $DISTRIBUTION_PREFIX"
    echo "DISTRIBUTION_URL: $DISTRIBUTION_URL"

    curl --silent --fail -X POST "http://${JENKINS_USER}:${JENKINS_PASS}@${JENKINS_HOST}:${JENKINS_PORT}/job/${JENKINS_JOB_NAME}/buildWithParameters?token=${JENKINS_JOB_TOKEN}&API_DIST_URL=$DISTRIBUTION_URL"
    if [ $? -eq 0 ]; then
        echo "Jenkins job $JENKINS_JOB_NAME started with API_DIST_URL=$DISTRIBUTION_URL"
        aws sqs delete-message  --queue-url "$SQS_QUEUE" \
                                --receipt-handle "$RECEIPT_HANDLE"
    fi
    echo "Done message processing. Sleeping $SQS_POLL_SEC seconds"
    echo
    echo
done
