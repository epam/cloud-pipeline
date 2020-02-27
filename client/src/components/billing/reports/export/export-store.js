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

import {message} from 'antd';
import FileSaver from 'file-saver';

class ExportStore {
  constructor () {
    this.listeners = [];
  }

  attach (listener) {
    if (this.listeners.indexOf(listener) === -1) {
      this.listeners.push(listener);
    }
  }

  detach (listener) {
    const index = this.listeners.indexOf(listener);
    if (index >= 0) {
      this.listeners.splice(index, 1);
    }
  }

  doExport = () => {
    const promises = this.listeners.map(listener => listener.getExportData());
    Promise.all(promises)
      .then((sheets) => {
        sheets.forEach((sheet, index) => {
          const extra = sheets.length > 1 ? ` (${index + 1} of ${sheets.length})` : '';
          const name = `Billing report${extra}.csv`;
          FileSaver.saveAs(sheet, name);
        });
      })
      .catch((error) => {
        message.error(error.toString(), 5);
      });
  };
}

export default new ExportStore();
