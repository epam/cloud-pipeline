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

import Source from '../../../models/pipelines/Source';
import Docs from '../../../models/pipelines/Docs';

const SOURCE_FILE = 'source';
const DOCUMENT_FILE = 'document';

export const PipelineFileTypes = {
  document: DOCUMENT_FILE,
  source: SOURCE_FILE
};

export async function getPipelineFileInfo (pipelineId, pipelineVersion, pathToFile) {
  const folder = (pathToFile || '').split('/').slice(0, -1).join('/');
  const docsRequest = new Docs(pipelineId, pipelineVersion);
  await docsRequest.fetch();
  if (docsRequest.loaded) {
    const [file] = (docsRequest.value || [])
      .filter(f => (f.path || '').toLowerCase() === (pathToFile.toLowerCase() || ''));
    if (file) {
      return {
        id: pipelineId,
        version: pipelineVersion,
        path: folder,
        type: PipelineFileTypes.document
      };
    }
  }
  const sourcesRequest = new Source(pipelineId, pipelineVersion, folder, true);
  await sourcesRequest.fetch();
  if (sourcesRequest.loaded) {
    const [file] = (sourcesRequest.value || [])
      .filter(f => f.type === 'blob' && (f.path || '').toLowerCase() === (pathToFile.toLowerCase() || ''));
    if (file) {
      return {
        id: pipelineId,
        version: pipelineVersion,
        path: folder,
        type: PipelineFileTypes.source
      };
    }
  }
  return null;
}
