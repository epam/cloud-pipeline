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

import {fetchToken} from '../../../../models/user/UserToken';
import {ObjectStorage} from '../../../../utils/object-storage';

function setRequestUrl (payload) {
  const [image, cell] = payload.imageId.split(':');
  const path = `${payload.pathMask}/${payload.source}`;
  const channels = payload.channels.reduce((acc, curr) => {
    const key = Object.keys(curr);
    const values = Object.values(curr)[0].slice();
    acc[key] = values;
    return acc;
  }, {});
  const params = new URLSearchParams({
    cell,
    path,
    byField: payload.byField,
    sequenceId: payload.sequenceId,
    ...channels
  });
  const requestUrl = ''; //todo
  return requestUrl + params;
}

async function getResponse (url) {
  const token = await fetchToken();
  const response = await fetch(url, {
    method: 'GET',
    headers: {
      ...(token ? {bearer: token} : {})
    }
  });
  return response.text();
}

async function getResourceUrl (payload) {
  const url = setRequestUrl(payload);
  const response = await getResponse(url)
    .then(text => JSON.parse(text))
    .then(json => json.payload);
  const file = response.path.split('/').slice(3).join('/').replace(/\/\//g, '/');
  const objectStorage = new ObjectStorage({
    id: payload.storageId,
    type: 'S3',
    path: payload.path
  });
  const videoSrc = await objectStorage.generateFileUrl(file)
    .then(url => url);
  return videoSrc;
}

export async function getVideoSrc (payload) {
  const videoSrc = await getResourceUrl(payload);
  return new Promise((resolve, reject) => {
    setTimeout(() => {
      resolve(videoSrc);
    }, 1000);
  });
}
