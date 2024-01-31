/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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

import {Project} from '../../../../../../utils/pipeline-builder';
import VersionFile from '../../../../../../models/pipelines/VersionFile';
import {base64toString} from '../../../../../../utils/base64';

export default function buildWdlContentsResolver (pipelineId, version) {
  return async (uri) => {
    if (!uri) {
      return '';
    }
    if (/^(https?|ftp):\/\//i.test(uri)) {
      return Project.default.defaultContentsResolver(uri);
    }
    const request = new VersionFile(pipelineId, uri, version);
    await request.fetch();
    if (request.error) {
      throw new Error(request.error);
    }
    return base64toString(request.response);
  };
}
