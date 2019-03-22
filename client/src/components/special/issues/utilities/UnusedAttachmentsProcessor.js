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

import IssueAttachmentLoad from '../../../../models/issues/IssueAttachmentLoad';
import IssueAttachmentDelete from '../../../../models/issues/IssueAttachmentDelete';

export async function processUnusedAttachments (text, attachments) {
  text = text || '';
  const realAttachments = [];
  for (let i = 0; i < (attachments || []).length; i++) {
    const attachment = attachments[i];
    const url = IssueAttachmentLoad.generateUrl(attachment.id).toLowerCase();
    if (text.toLowerCase().indexOf(url) === -1) {
      const request = new IssueAttachmentDelete(attachment.id);
      await request.fetch();
    } else {
      realAttachments.push(attachment.id);
    }
  }
  return realAttachments.map(id => ({id}));
}
