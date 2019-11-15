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

import Remote from '../basic/Remote';

class GenerateDownloadUrl extends Remote {
  url;

  static buildQuery (path, version, contentDisposition) {
    const query = [
      !!path && `path=${encodeURIComponent(path)}`,
      !!version && `version=${version}`,
      !!contentDisposition && `contentDisposition=${contentDisposition}`
    ]
      .filter(Boolean)
      .join('&');
    return `${!!query && query.length > 0 ? '?' : ''}${query}`;
  }

  static getRedirectUrl = (id, path, version) => {
    const prefix = this.prefix;
    const query = this.buildQuery(path, version, 'ATTACHMENT');
    return `${prefix}/datastorage/${id}/downloadRedirect${query}`;
  };

  constructor (id, path, version) {
    super();
    this.id = id;
    this.path = path;
    this.version = version;
    this.buildUrl();
  };

  buildUrl () {
    const query = this.constructor.buildQuery(this.path, this.version, 'ATTACHMENT');
    this.url = `/datastorage/${this.id}/generateUrl${query}`;
  }
}

export default GenerateDownloadUrl;
