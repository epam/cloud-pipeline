/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

import parseRunServiceUrl from '../parseRunServiceUrl';

export default function parseRunServiceUrlConfiguration (serviceUrl) {
  const result = [];
  Object.entries(serviceUrl || {})
    .map(([region, urls]) => {
      parseRunServiceUrl(urls)
        .forEach(urlConfiguration => {
          const urlName = urlConfiguration.name;
          let url = result.find(r => r.name === urlName);
          if (!url) {
            url = {
              name: urlName,
              isDefault: /^true$/i.test(`${urlConfiguration.isDefault}`),
              sameTab: /^true$/i.test(`${urlConfiguration.sameTab}`),
              url: {}
            };
            result.push(url);
          }
          url.url[region] = urlConfiguration.url;
        });
    });
  return result;
}
