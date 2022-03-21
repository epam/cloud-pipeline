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

import { Remarkable } from 'remarkable';
import fetchMd from './fetch-md';
import fetchConfig from './fetch-config';
import attachAnimation from './animation';
import './styles.css';

async function displayMarkdown() {
  try {
    const mdContent = await fetchMd();
    disclaimerDiv.innerHTML = mdRenderer.render(mdContent);
    disclaimerDiv.classList.add('displayed');
  } catch (_) {
    console.error(_.message);
  }
}

async function safeFetchConfig() {
  try {
    return fetchConfig();
  } catch (_) {
    console.error(_.message);
  }
  return {};
}

const mdRenderer = new Remarkable();
const disclaimerDiv = document.getElementById('disclaimer');

(async function maintenance() {
  const [config] = await Promise.all([safeFetchConfig(), displayMarkdown()]);
  const {
    title,
    dark,
    useSystemColorScheme = true
  } = config || {};
  if (title) {
    document.title = title;
  }
  if (dark) {
    document.body.classList.add('dark');
  }
  if (!useSystemColorScheme) {
    document.body.classList.add('ignore-color-scheme');
  }
  attachAnimation(config);
})();
