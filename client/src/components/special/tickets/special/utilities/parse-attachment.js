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
import {SERVER, API_PATH} from '../../../../../config';

function generateAttachmentURL (secret) {
  // eslint-disable-next-line
  return `${SERVER + API_PATH}/issue/gitlab/attachment?secret=${secret}`;
}

export default function parseAttachment (attachment) {
  if (
    !attachment ||
    typeof attachment.fileName !== 'string' ||
    typeof attachment.markdown !== 'string' ||
    typeof attachment.secret !== 'string'
  ) {
    return undefined;
  }
  const name = attachment.fileName;
  const link = generateAttachmentURL(attachment.secret);

  return {
    name,
    link
  };
}
