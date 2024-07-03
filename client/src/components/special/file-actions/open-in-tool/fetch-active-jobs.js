/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

import PipelineRunFilter from '../../../../models/pipelines/PipelineRunSingleFilter';
import PipelineRunServices from '../../../../models/pipelines/PipelineRunServices';
import wrapRequest from './wrap-request';

function runsRequest (request, method, mapper = (o => o)) {
  return new Promise((resolve) => {
    wrapRequest(request, method)
      .then(request => {
        if (request.loaded) {
          resolve((request.value || []).map(mapper));
        } else {
          resolve([]);
        }
      })
      .catch(() => resolve([]));
  });
}

export default function fetchActiveJobs () {
  return new Promise((resolve) => {
    Promise.all([
      runsRequest(
        new PipelineRunServices({
          page: 1,
          pageSize: 100,
          userModified: false,
          statuses: ['RUNNING']
        }, false),
        'filter',
        o => ({...o, isService: true})
      ),
      runsRequest(
        new PipelineRunFilter({
          page: 1,
          pageSize: 100,
          userModified: false,
          statuses: ['RUNNING']
        }, false),
        'filter'
      )
    ])
      .then(payloads => {
        resolve(
          payloads
            .reduce((r, c) => ([...r, ...c]), [])
            .sort((a, b) => a.id - b.id)
        );
      })
      .catch(() => resolve([]));
  });
}
