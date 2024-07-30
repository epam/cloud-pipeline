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

import {PipelineRunCommitCheck} from '../../../../../models/pipelines/PipelineRunCommitCheck';

/**
 * @param {number|string} runId
 * @returns {Promise<{result:boolean}>} true if default check passed, false otherwise
 */
export default async function commitCheck (runId, options, skipContainerCheck = false) {
  const checkRequest = new PipelineRunCommitCheck(runId);
  await checkRequest.fetch();
  if (checkRequest.loaded) {
    const {enoughSpace = {}, containerSize = {}} = checkRequest.value || {};
    const sizeCheck = `${containerSize.result || ''}`.toLowerCase() === 'ok';
    const spaceCheck = `${enoughSpace.result || ''}`.toLowerCase() === 'ok' ||
      `${enoughSpace.result || ''}`.toLowerCase() === 'warn';
    return {
      result: sizeCheck && spaceCheck,
      message: [
        !skipContainerCheck && containerSize.message ? {
          type: sizeCheck ? 'warning' : 'error',
          text: containerSize.message,
          checkType: 'size'
        } : null,
        enoughSpace.message ? {
          type: spaceCheck ? 'warning' : 'error',
          text: enoughSpace.message,
          checkType: 'space'
        } : null
      ].filter(Boolean)
    };
  }
  return {
    result: true,
    message: undefined
  };
}
