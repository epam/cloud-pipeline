/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

import {SERVER, API_PATH} from '../../config';

const TIMEOUT = 10000;

function timeout (ms, promise) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      reject(new Error('TIMEOUT'));
    }, ms);
    promise
      .then(value => {
        clearTimeout(timer);
        resolve(value);
      })
      .catch(reason => {
        clearTimeout(timer);
        reject(reason);
      });
  });
}

export default function fetchTempCredentials (id, permissions, timeoutMs = TIMEOUT) {
  const prefix = SERVER + API_PATH;
  const url = `${prefix}/datastorage/tempCredentials/`;
  return new Promise((resolve, reject) => {
    try {
      timeout(
        timeoutMs,
        fetch(
          url,
          {
            mode: 'cors',
            credentials: 'include',
            method: 'POST',
            headers: {
              'Content-Type': 'application/json; charset=UTF-8;'
            },
            body: JSON.stringify([{id, ...permissions}])
          }
        )
      )
        .then(response => {
          response
            .json()
            .then(o => {
              const {
                error,
                message,
                payload,
                status
              } = o;
              if (status === 401) {
                reject(new Error('Not authorized'));
              } else if (status === 'OK') {
                resolve({payload});
              } else {
                resolve({error: message || error});
              }
            })
            .catch(reject);
        })
        .catch(reject);
    } catch (e) {
      reject(e);
    }
  });
}
