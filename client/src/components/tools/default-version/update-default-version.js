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

import {message, Modal} from 'antd';
import ToolDefaultVersionUpdate from '../../../models/tools/ToolDefaultVersionUpdate';
import ToolDefaultVersionDelete from '../../../models/tools/ToolDefaultVersionDelete';

/**
 * Asks a user to confirm, that a version is set as a tool's default one, or is unset
 * @param {string} version
 * @param {boolean} [unset]
 * @returns {Promise<boolean>}
 */
export function confirmDefaultVersion (version, unset = false) {
  return new Promise((resolve) => {
    Modal.confirm({
      title: unset
        ? `Are you sure you want to unset version '${version}' as default?`
        : `Are you sure you want to set version '${version}' as default?`,
      content: unset
        ? 'The latest version is launched, if a version is not specified.'
        : 'This version is launched, if a version is not specified.',
      style: {
        wordWrap: 'break-word'
      },
      okText: 'Yes',
      cancelText: 'No',
      onOk: () => resolve(true),
      onCancel: () => resolve(false)
    });
  });
}

/**
 * Sets a version as a tool's default one, or unsets the default version,
 * if a version is not specified. Reports an error, if the request fails.
 * @param {number} toolId
 * @param {string} [version]
 * @returns {Promise<{error?: string, value?: Object}>} the request
 */
export async function updateDefaultVersion (toolId, version) {
  const hide = message.loading(
    version ? `Setting version '${version}' as default...` : 'Unsetting the default version...',
    0
  );
  const request = version
    ? new ToolDefaultVersionUpdate(toolId, version)
    : new ToolDefaultVersionDelete(toolId);
  await request.send({});
  hide();
  if (request.error) {
    message.error(request.error, 5);
  }
  return request;
}
