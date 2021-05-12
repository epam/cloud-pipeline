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

function trimTrailingSlash (o) {
  if (!o || !o.endsWith('/')) {
    return o;
  }
  return o.slice(0, -1);
}

class PipelineFileUpdate extends RemotePost {
  constructor (id) {
    super();
    this.url = `/pipeline/${id}/file`;
  }

  static uploadUrl (id, folder, options = {}) {
    const {
      trimTrailingSlash: trimTrailingSlashOption = false
    } = options;
    const folderCorrected = trimTrailingSlashOption
      ? trimTrailingSlash(folder)
      : folder;
    const queryParameters = [
      folderCorrected && folderCorrected.length > 0
        ? `path=${folderCorrected}`
        : undefined
    ]
      .filter(Boolean)
      .join('&');
    const query = queryParameters.length > 0 ? `?${queryParameters}` : '';
    return `${this.prefix}/pipeline/${id}/file/upload${query}`;
  }
}

export default PipelineFileUpdate;
