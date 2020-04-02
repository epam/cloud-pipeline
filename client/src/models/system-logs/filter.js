/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import defer from '../../utils/defer';

const wait = (sec) => new Promise((resolve) => {
  setTimeout(resolve, sec * 1000);
});

const message = (i) => ({
  service_name: 'edge',
  message_timestamp: '2020-03-27T14:13:23.000Z',
  source: '/etc/nginx/logs/error.log',
  message: `${i + 1}) 2020/03/27 14:13:23 [warn] 32#0: *16871 [lua] validate_cookie_ssh.lua:52: [SECURITY] Application: SSH-/ssh/pipeline/3; User: PIPE_ADMIN; Status: Successfully autentificated., client: 127.0.0.1, server: , request: "GET /ssh/pipeline/3 HTTP/1.1", host: "52.29.248.48:31081", referrer: "https://52.29.248.48:31081/ssh/pipeline/3"`,
  type: 'security',
  hostname: 'cp-edge-3355421719-jdnwn',
  timestamp: '2020-03-27T14:13:26.645Z',
  application: 'SSH-/ssh/pipeline/3',
  user: 'PIPE_ADMIN'
});

class SystemLogsFilter extends RemotePost {
  async send (body) {
    if (!this._postIsExecuting) {
      this._pending = true;
      this._postIsExecuting = true;
      try {
        console.log('fake sending filters request', body);
        await defer();
        await wait(1);
        const messages = [];
        for (let i = 0; i < 1000; i++) {
          messages.push(message(i));
        }
        this.update({
          payload: {
            results: messages,
            total: 20000
          },
          status: 'OK'
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

export default SystemLogsFilter;
