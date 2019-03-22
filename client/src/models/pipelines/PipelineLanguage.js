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
import {action} from 'mobx';

class PipelineLanguage extends Remote {
  url;

  constructor (id, version) {
    super();
    this.url = `/pipeline/${id}/language?version=${version}`;
  };

  @action
  update (value) {
    this._response = value;
    if (value.status && value.status === 'OK') {
      this._value = this.postprocess(value);
      this._loaded = true;
      this.error = undefined;
      this.failed = false;
    } else {
      this._loaded = false;
      this.error = value.message;
      this._value = 'other';
      this.failed = true;
    }
  }
}

export default PipelineLanguage;
