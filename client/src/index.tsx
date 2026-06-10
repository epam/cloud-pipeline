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

import React from 'react';
import {createRoot} from 'react-dom/client';
import {configure} from 'mobx';
import {Main} from './pages/main';
import {initPipelineBuilder} from './utils/pipeline-builder';
import 'antd/dist/reset.css';
import './index.css';
import './staticStyles/markdown.css';
import './themes/styles/index.css';
import {initCloudPipelineApi} from './workflows/initialization/initialize-app.tsx';

initCloudPipelineApi();
configure({enforceActions: 'never'});
initPipelineBuilder();

let container = document.getElementById('root');
if (!container) {
  container = document.createElement('div');
  document.body.appendChild(container);
}
const root = createRoot(container);
root.render(<Main />);
