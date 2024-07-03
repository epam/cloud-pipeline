/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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

function arrayBufferToBase64 (buffer) {
  let binary = '';
  const bytes = new Uint8Array(buffer);
  for (let i = 0; i < bytes.byteLength; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary);
}

export default async function blobFilesToBase64 (files = []) {
  const promises = files.map(file => new Promise((resolve) => {
    try {
      const reader = new FileReader();
      reader.onloadend = () => resolve({[file.name]: arrayBufferToBase64(reader.result)});
      reader.readAsArrayBuffer(file);
    } catch {
      resolve(null);
    }
  }));
  const values = await Promise.all(promises);
  const filtered = values.filter(Boolean);
  return Object.entries(filtered.reduce((a, c) => ({...a, ...c})))
    .map(([fileName, content]) => ({
      fileName,
      content
    }));
}
