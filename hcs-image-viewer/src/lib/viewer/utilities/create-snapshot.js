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

async function getCanvasImage(canvas) {
  const image = new Image();
  return new Promise((resolve) => {
    image.onerror = (ev) => {
      if (ev) {
        console.warn(ev);
      }
      resolve(undefined);
    };
    if (canvas instanceof OffscreenCanvas) {
      canvas.convertToBlob({ type: 'image/png', quality: 1 })
        .then((blob) => {
          const url = URL.createObjectURL(blob);
          image.onload = () => {
            resolve(image);
            URL.revokeObjectURL(url);
          };
          image.src = url;
        })
        .catch((error) => {
          console.warn(error);
          resolve(undefined);
        });
    } else {
      image.onload = () => {
        resolve(image);
      };
      image.src = canvas.toDataURL('image/png', 1);
    }
  });
}

async function getCanvasBlob(canvas) {
  if (canvas instanceof OffscreenCanvas) {
    return canvas.convertToBlob({ quality: 1 });
  }
  return new Promise((resolve) => {
    canvas.toBlob((blob) => {
      if (blob) {
        resolve(blob);
      } else {
        resolve(undefined);
      }
    }, 'image/png', 1);
  });
}

async function saveBlob(blob, fileName) {
  const anchor = document.createElement('a');
  anchor.download = fileName;
  anchor.target = '_blank';
  const reader = new FileReader();
  return new Promise((resolve, reject) => {
    reader.onload = () => {
      if (typeof reader.result === 'string') {
        anchor.href = reader.result;
        anchor.click();
        resolve();
      } else {
        reject(new Error('unknown data format'));
      }
    };
    reader.onerror = () => {
      reject(new Error('cannot save image'));
    };
    reader.readAsDataURL(blob);
  });
}

export default async function createSnapshot(canvas, name = 'image') {
  const blob = await getCanvasBlob(canvas);
  await saveBlob(blob, `${name}.png`);
}
