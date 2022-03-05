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

import {message} from 'antd';
import FileSaver from 'file-saver';
import PipelineFile from '../../../../models/pipelines/PipelineFile';

export default async function downloadPipelineFile (pipelineId, version, path) {
  const name = (path || '').split(/[/\\]/).pop();
  const fileName = name || 'pipeline-file';
  const hide = message.loading(`Downloading ${name || 'pipeline file'}...`, 0);
  try {
    const pipelineFile = new PipelineFile(pipelineId, version, path);
    let res;
    await pipelineFile.fetch();
    res = pipelineFile.response;
    if (res.type?.includes('application/json') && res instanceof Blob) {
      this.checkForBlobErrors(res)
        .then(error => error
          ? message.error('Error downloading file', 5)
          : FileSaver.saveAs(res, fileName)
        );
    } else if (res) {
      FileSaver.saveAs(res, fileName);
    }
  } catch (e) {
    message.error('Failed to download file', 5);
  } finally {
    hide();
  }
}
