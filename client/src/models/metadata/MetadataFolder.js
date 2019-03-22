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

import {SERVER} from '../../config';
import Remote from '../basic/Remote';
import {action} from 'mobx';
import {authorization} from '../basic/Authorization';

export default class MetadataFolder extends Remote {

  constructor (parentId) {
    super();
    if (parentId) {
      this.url = `/metadata/folder?parentFolderId=${parentId}`;
    } else {
      this.url = '/metadata/folder';
    }
  }

  @action
  update (value) {
    this._response = value;
    if (value.status && value.status === 401) {
      this.error = value.message;
      this.failed = true;
      if (authorization.isAuthorized()) {
        authorization.setAuthorized(false);
        console.log('Changing authorization to: ' + authorization.isAuthorized());
        window.location = `${SERVER}/saml/logout`;
      }
    } else if (value.status && value.status === 'OK') {
      this._value = this.postprocess(value);
      this._loaded = true;
      this.error = undefined;
      this.failed = false;
      if (!authorization.isAuthorized()) {
        authorization.setAuthorized(true);
        console.log('Changing authorization to: ' + authorization.isAuthorized());
      }
    } else {
      this.error = value.message;
      this.failed = true;
      this._loaded = true;
      this._value = [];
    }
  }
}
