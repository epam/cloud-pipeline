/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

const StorageAggregate = {
  default: 'default',
  standard: 'standard',
  deepArchive: 'deep-archive',
  glacier: 'glacier',
  glacierIR: 'glacier-ir'
};

export {StorageAggregate};

export function getBillingGroupingOrderAggregate (aggregate) {
  switch (aggregate) {
    case StorageAggregate.deepArchive:
      return 'DEEP_ARCHIVE';
    case StorageAggregate.glacier:
      return 'GLACIER';
    case StorageAggregate.glacierIR:
      return 'GLACIER_IR';
    case StorageAggregate.standard:
      return 'STANDARD';
    default:
      return 'STORAGE';
  }
}

export function getAggregateByStorageClass (storageClass) {
  switch ((storageClass || '').toUpperCase()) {
    case 'DEEP_ARCHIVE': return StorageAggregate.deepArchive;
    case 'GLACIER': return StorageAggregate.glacier;
    case 'GLACIER_IR': return StorageAggregate.glacierIR;
    case 'STANDARD': return StorageAggregate.standard;
    default:
      return StorageAggregate.default;
  }
}

const DEFAULT_STORAGE_CLASS_ORDER = [
  'STANDARD',
  'DEEP_ARCHIVE',
  'GLACIER',
  'GLACIER_IR'
];

export {DEFAULT_STORAGE_CLASS_ORDER};

export function getStorageClassName (storageClass) {
  switch ((storageClass || '').toUpperCase()) {
    case 'DEEP_ARCHIVE': return 'Deep Archive';
    case 'GLACIER': return 'Glacier';
    case 'GLACIER_IR': return 'Glacier IR';
    case 'STANDARD':
    default:
      return 'Standard';
  }
}

export function getStorageClassByAggregate (aggregate) {
  return getBillingGroupingOrderAggregate(aggregate);
}

export function getStorageClassNameByAggregate (aggregate) {
  switch (aggregate) {
    case StorageAggregate.deepArchive: return 'Deep Archive';
    case StorageAggregate.glacier: return 'Glacier';
    case StorageAggregate.glacierIR: return 'Glacier IR';
    case StorageAggregate.standard: return 'Standard';
    default:
      return '';
  }
}

export function parseStorageAggregate (aggregate) {
  switch ((aggregate || '').toLowerCase()) {
    case StorageAggregate.standard:
      return StorageAggregate.standard;
    case StorageAggregate.deepArchive:
      return StorageAggregate.deepArchive;
    case StorageAggregate.glacier:
      return StorageAggregate.glacier;
    case StorageAggregate.glacierIR:
      return StorageAggregate.glacierIR;
    case StorageAggregate.default:
    default:
      return StorageAggregate.default;
  }
}
