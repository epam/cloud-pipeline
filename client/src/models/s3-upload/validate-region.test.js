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

/* eslint-env jest */

import validateRegion from './validate-region';

describe('validateRegion', () => {
  it('accepts the AWS region names', () => {
    ['us-east-1', 'eu-central-1', 'us-gov-west-1', 'cn-northwest-1', 'ap-southeast-4']
      .forEach(region => expect(validateRegion(region)).toBe(region));
  });

  it('returns an empty region as it is', () => {
    expect(validateRegion(undefined)).toBe(undefined);
    expect(validateRegion(null)).toBe(null);
    expect(validateRegion('')).toBe('');
  });

  it('rejects a region that is not a host name label', () => {
    [
      'evil.example.com',
      'us-east-1.evil.com',
      'evil.com/',
      'us-east-1#',
      'us east 1',
      '-us-east-1',
      'us-east-1-',
      'a'.repeat(64)
    ].forEach(region => expect(() => validateRegion(region)).toThrow('Invalid storage region'));
  });
});
