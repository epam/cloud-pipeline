/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

import AWS from 'aws-sdk/index';
import {stubApi} from '@test';
import S3Storage from './s3-storage';

const TEMP_CREDENTIALS = '/datastorage/tempCredentials/';

const credentials = (region) => ({
  keyID: 'key-id',
  accessKey: 'access-key',
  token: 'token',
  expiration: '2100-01-01 00:00:00',
  region
});

// The region and the credentials are set in the global configuration of aws-sdk
let region;
let awsCredentials;

beforeEach(() => {
  ({region, credentials: awsCredentials} = AWS.config);
});

afterEach(() => {
  AWS.config.update({region, credentials: awsCredentials});
});

describe('S3Storage.updateCredentials', () => {
  it('sets the region the credentials give', async () => {
    stubApi({[TEMP_CREDENTIALS]: credentials('eu-central-1')});

    await expect(new S3Storage({id: 1, region: 'us-east-1'}).updateCredentials())
      .resolves.toBe(true);
    expect(AWS.config.region).toBe('eu-central-1');
  });

  it('sets the region of the storage when the credentials give none', async () => {
    stubApi({[TEMP_CREDENTIALS]: credentials(undefined)});

    await expect(new S3Storage({id: 1, region: 'us-east-1'}).updateCredentials())
      .resolves.toBe(true);
    expect(AWS.config.region).toBe('us-east-1');
  });

  it('fails for a region that is not a host name label, and does not set it', async () => {
    stubApi({[TEMP_CREDENTIALS]: credentials('evil.example.com/')});

    await expect(new S3Storage({id: 1, region: 'us-east-1'}).updateCredentials())
      .rejects.toThrow('Invalid storage region: evil.example.com/');
    expect(AWS.config.region).toBe(region);
  });
});
