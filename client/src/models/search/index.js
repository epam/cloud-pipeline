/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

import RemotePost from '../basic/RemotePost';

export const SearchItemTypes = {
  run: 'PIPELINE_RUN',
  s3Bucket: 'S3_STORAGE',
  s3File: 'S3_FILE',
  NFSFile: 'NFS_FILE',
  NFSBucket: 'NFS_STORAGE',
  gsFile: 'GS_FILE',
  gsStorage: 'GS_STORAGE',
  azFile: 'AZ_BLOB_FILE',
  azStorage: 'AZ_BLOB_STORAGE',
  pipeline: 'PIPELINE',
  tool: 'TOOL',
  toolGroup: 'TOOL_GROUP',
  dockerRegistry: 'DOCKER_REGISTRY',
  issue: 'ISSUE',
  metadataEntity: 'METADATA_ENTITY',
  folder: 'FOLDER',
  configuration: 'CONFIGURATION',
  pipelineCode: 'PIPELINE_CODE'
};

export class Search extends RemotePost {
  query;
  page;
  pageSize;

  constructor () {
    super();
    this.url = '/search';
  }

  async send (query, page, pageSize, types = []) {
    if (query) {
      this.query = query;
      this.page = page;
      this.pageSize = pageSize;
      return super.send({
        query,
        offset: page * pageSize,
        pageSize,
        highlight: true,
        aggregate: true,
        filterTypes: types.length === 0 ? undefined : types
      });
    }
  }

  postprocess (value) {
    value.payload.documents = (value.payload.documents || []).map(processItem);
    return value.payload;
  }
}

export function processItem (item) {
  switch (item.type) {
    case SearchItemTypes.azFile:
    case SearchItemTypes.s3File:
    case SearchItemTypes.NFSFile:
    case SearchItemTypes.gsFile:
      return {
        ...item,
        name: item.name.split('/')[item.name.split('/').length - 1]
      };
  }
  return item;
}
