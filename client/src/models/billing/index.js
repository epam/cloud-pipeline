/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

import * as quotas from './quotas';
import FetchBillingCenters from './billing-centers';
import GetBillingData from './get-billing-data';
import {
  GetGroupedBillingCenters,
  GetGroupedBillingCentersWithPrevious
} from './get-grouped-billing-centers';
import {GetGroupedResources, GetGroupedResourcesWithPrevious} from './get-grouped-resources';
import {GetGroupedStorages, GetGroupedStoragesWithPrevious} from './get-grouped-storages';
import {
  GetGroupedFileStorages,
  GetGroupedFileStoragesWithPrevious
} from './get-grouped-file-storages';
import {
  GetGroupedObjectStorages,
  GetGroupedObjectStoragesWithPrevious
} from './get-grouped-object-storages';
import {GetGroupedInstances, GetGroupedInstancesWithPrevious} from './get-grouped-instances';
import {GetGroupedPipelines, GetGroupedPipelinesWithPrevious} from './get-grouped-pipelines-data';
import {GetGroupedTools, GetGroupedToolsWithPrevious} from './get-grouped-tools-data';

export {
  quotas,
  FetchBillingCenters,
  GetBillingData,
  GetGroupedBillingCenters,
  GetGroupedBillingCentersWithPrevious,
  GetGroupedResources,
  GetGroupedResourcesWithPrevious,
  GetGroupedStorages,
  GetGroupedStoragesWithPrevious,
  GetGroupedFileStorages,
  GetGroupedFileStoragesWithPrevious,
  GetGroupedObjectStorages,
  GetGroupedObjectStoragesWithPrevious,
  GetGroupedInstances,
  GetGroupedInstancesWithPrevious,
  GetGroupedPipelines,
  GetGroupedPipelinesWithPrevious,
  GetGroupedTools,
  GetGroupedToolsWithPrevious
};
