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

import {getInstanceFamily, getInstanceFamilyByName} from './instance-family';

describe('getInstanceFamilyByName on AWS', () => {
  it('takes everything before the dot', () => {
    expect(getInstanceFamilyByName('m5.large', 'aws')).toBe('m5');
    expect(getInstanceFamilyByName('p3.2xlarge', 'aws')).toBe('p3');
    expect(getInstanceFamilyByName('c5n.18xlarge', 'aws')).toBe('c5n');
  });

  it('gives up on a name with no dot', () => {
    expect(getInstanceFamilyByName('m5', 'aws')).toBeUndefined();
  });
});

describe('getInstanceFamilyByName on GCP', () => {
  it('keeps series and class for a predefined type, dropping the CPU count', () => {
    expect(getInstanceFamilyByName('n1-standard-2', 'gcp')).toBe('n1-standard');
    expect(getInstanceFamilyByName('n1-highmem-96', 'gcp')).toBe('n1-highmem');
  });

  it('keeps a two-part name whole when there is nothing to drop', () => {
    expect(getInstanceFamilyByName('e2-medium', 'gcp')).toBe('e2-medium');
  });

  it('recognises a custom type by its own pattern', () => {
    expect(getInstanceFamilyByName('custom-2-4096', 'gcp')).toBe('custom');
    expect(getInstanceFamilyByName('n2-custom-4-8192', 'gcp')).toBe('n2-custom');
  });

  it('keeps the accelerator on a GPU custom type', () => {
    expect(getInstanceFamilyByName('gpu-n1-custom-4-8192-k80-1', 'gcp')).toBe('gpu-n1-custom-k80');
  });
});

describe('getInstanceFamilyByName on Azure', () => {
  it('drops the SKU prefix and the size digits, keeping the letters around them', () => {
    expect(getInstanceFamilyByName('Standard_B2s', 'azure')).toBe('Bs');
    expect(getInstanceFamilyByName('Standard_D2_v3', 'azure')).toBe('Dv3');
  });

  it('works on a name that carries no prefix', () => {
    expect(getInstanceFamilyByName('B2s', 'azure')).toBe('Bs');
  });

  it('gives up when nothing follows the digits', () => {
    // The pattern requires a non-empty suffix group, so a bare size has no
    // family at all rather than falling back to the letter.
    expect(getInstanceFamilyByName('Standard_B2', 'azure')).toBeUndefined();
  });
});

describe('getInstanceFamilyByName provider matching', () => {
  it('matches the provider case-insensitively', () => {
    expect(getInstanceFamilyByName('m5.large', 'AWS')).toBe('m5');
    expect(getInstanceFamilyByName('n1-standard-2', 'GCP')).toBe('n1-standard');
    expect(getInstanceFamilyByName('Standard_B2s', 'Azure')).toBe('Bs');
  });

  it('gives up on a provider it does not know', () => {
    expect(getInstanceFamilyByName('m5.large', 'openstack')).toBeUndefined();
  });

  it('gives up when either argument is missing', () => {
    expect(getInstanceFamilyByName(undefined, 'aws')).toBeUndefined();
    expect(getInstanceFamilyByName('m5.large', undefined)).toBeUndefined();
    expect(getInstanceFamilyByName('', 'aws')).toBeUndefined();
  });
});

describe('getInstanceFamily', () => {
  it('reads the name off the instance', () => {
    expect(getInstanceFamily({name: 'm5.large'}, 'aws')).toBe('m5');
  });

  it('gives up on an instance with no name, and on no instance at all', () => {
    expect(getInstanceFamily(undefined, 'aws')).toBeUndefined();
    expect(getInstanceFamily({}, 'aws')).toBeUndefined();
  });
});
