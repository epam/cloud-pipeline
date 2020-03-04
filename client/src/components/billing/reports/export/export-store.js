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
    this.imageListeners = [];
  }

  attach (listener) {
    if (this.listeners.indexOf(listener) === -1) {
      this.listeners.push(listener);
    }
  }

  attachImage (listener) {
    if (this.imageListeners.indexOf(listener) === -1) {
      this.imageListeners.push(listener);
    }
  }

  detach (listener) {
    const index = this.listeners.indexOf(listener);
    if (index >= 0) {
      this.listeners.splice(index, 1);
    }
  }

  detachImage (listener) {
    const index = this.imageListeners.indexOf(listener);
    if (index >= 0) {
      this.imageListeners.splice(index, 1);
    }
  }

  doCsvExport = (title) => {
    const promises = this.listeners.map(listener => listener.getExportData());
    Promise.all(promises)
      .then((sheets) => {
        sheets.forEach((sheet, index) => {
          const extra = sheets.length > 1 ? ` (${index + 1} of ${sheets.length})` : '';
          const name = `${title}${extra}.csv`;
          FileSaver.saveAs(sheet, name);
        });
      })
      .catch((error) => {
        message.error(error.toString(), 5);
      });
  };

  doImageExport = (title) => {
    const hide = message.loading('Creating an image...', 0);
    setTimeout(() => {
      const listeners = this.imageListeners.sort((a, b) => {
        const {order: aOrder} = a.props;
        const {order: bOrder} = b.props;
        return aOrder - bOrder;
      });
      const promises = listeners.map(listener => listener.getExportData());
      Promise.all(promises)
        .then((canvases) => {
          const filtered = canvases.filter(Boolean);
          const canvasElement = document.createElement('canvas');
          document.body.style.overflowY = 'hidden';
          document.body.appendChild(canvasElement);
          const titleHeight = 50;
          const width = Math.max(...filtered.map(({width}) => width), 0);
          const height = filtered.reduce((r, c) => r + c.height, titleHeight);
          canvasElement.width = width;
          canvasElement.height = height;
          const context = canvasElement.getContext('2d');
          context.fillStyle = 'white';
          context.fillRect(0, 0, width, height);
          context.fillStyle = 'rgb(89, 89, 89)';
          context.font = `bold 12pt sans-serif`;
          context.textAlign = 'center';
          context.textBaseline = 'middle';
          context.fillText(
            title,
            width / 2.0,
            titleHeight / 2.0
          );
          let y = titleHeight;
          filtered.forEach((canvasData) => {
            context.putImageData(canvasData, 0, y);
            y += canvasData.height;
          });
          canvasElement.toBlob((blob) => {
            FileSaver.saveAs(blob, `${title}.png`);
            document.body.removeChild(canvasElement);
            document.body.style.overflowY = 'unset';
            hide();
          });
        })
        .catch((error) => {
          message.error(error.toString(), 5);
          hide();
        });
    }, 250);
  };
}

export default new ExportStore();
