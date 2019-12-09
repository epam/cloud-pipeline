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

import RemotePost from '../../basic/RemotePost';
import defer from '../../../utils/defer';

function wait (seconds) {
  return new Promise((resolve) => {
    setTimeout(resolve, seconds * 1000);
  });
}

class TemplateRemove extends RemotePost {
  async send (body) {
    if (!this._postIsExecuting) {
      this._pending = true;
      this._postIsExecuting = true;
      try {
        await defer();
        await wait(0.5);
        const {id} = body;
        const templates = JSON.parse(localStorage.getItem('quota-templates') || '[]');
        const [existingTemplate] = templates.filter(q => +(q.id) === +id);
        if (existingTemplate) {
          const index = templates.indexOf(existingTemplate);
          templates.splice(index, 1);
        }
        localStorage.setItem('quota-templates', JSON.stringify(templates));
        this.update({
          status: 'OK',
          payload: body
        });
      } catch (e) {
        this.failed = true;
        this.error = e.toString();
      } finally {
        this._postIsExecuting = false;
      }

      this._pending = false;
      this._fetchIsExecuting = false;
    }
  }
}

export default TemplateRemove;
