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

const id = 'generate-image-plugin';

const plugin = {
  id,
  afterRender: function (chart, configuration) {
    if (chart) {
      const {onImageReady, onImageError} = configuration;
      const {canvas} = chart;
      const {width, height} = canvas;
      if (width === 0 || height === 0) {
        onImageReady(null);
        return;
      }
      const canvasElement = document.createElement('canvas');
      canvasElement.width = width || 1;
      canvasElement.height = height || 1;
      const ctx = canvasElement.getContext('2d');
      ctx.fillStyle = 'white';
      ctx.fillRect(0, 0, width, height);
      const image = new Image();
      image.onload = function () {
        ctx.drawImage(this, 0, 0);
        const data = ctx.getImageData(0, 0, width, height);
        onImageReady(data);
      };
      image.onerror = function () {
        onImageError && onImageError('Error loading image');
      };
      image.src = chart.toBase64Image();
    }
  }
};

export {id, plugin};
