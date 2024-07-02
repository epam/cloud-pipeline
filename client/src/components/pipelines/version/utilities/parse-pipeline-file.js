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

import Papa from 'papaparse';
import VersionFile from '../../../../models/pipelines/VersionFile';
import {base64toString} from '../../../../utils/base64';

function process (result, path) {
  return new Promise((resolve, reject) => {
    try {
      const content = base64toString(result);
      // eslint-disable-next-line
      const isBinary = o => /[\x00-\x08\x0B-\x0C\x0E-\x1F]/.test(o);
      const binary = isBinary(content);
      const parseAsTabular = Papa.parse(content);
      const isTabular = !binary &&
        /\.(csv|tsv)$/i.test(path) &&
        parseAsTabular.errors.length === 0 &&
        !parseAsTabular.data.find(item => item.find(isBinary));
      resolve({
        content: binary ? undefined : content,
        binary: !isTabular && binary,
        tabular: isTabular
          ? parseAsTabular.data
          : false,
        error: undefined
      });
    } catch (e) {
      reject(new Error(`Error parsing file: ${e.message}`));
    }
  });
}

/**
 * @typedef {Object} PipelineFileContents
 * @property {string|undefined} content
 * @property {string|undefined} error
 * @property {boolean} binary
 * @property {string|Object} tabular
 */

/**
 * Fetch and parse pipeline file contents
 * @param {string|number} pipelineId
 * @param {string} version
 * @param {string} path
 * @param {number} [byteLimit]
 * @return {Promise<PipelineFileContents>}
 */
export default async function parsePipelineFile (pipelineId, version, path, byteLimit) {
  if (!pipelineId || !version || !path) {
    return {
      error: 'File path, pipeline or revision is not specified',
      content: undefined,
      binary: false,
      tabular: false
    };
  }
  const request = new VersionFile(
    pipelineId,
    path,
    version,
    byteLimit
  );
  try {
    await request.fetch();
    if (request.error) {
      throw new Error(request.error || `Error fetching ${path} content`);
    }
    return process(request.response, path);
  } catch (e) {
    return {
      error: e.message,
      content: undefined,
      binary: false,
      tabular: false
    };
  }
};
