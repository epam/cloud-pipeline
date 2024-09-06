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

import {RepositoryTypes} from '../../../special/git-repository-control';

const defaultValues = {
  [RepositoryTypes.GitLab]: {src: 'src/', docs: 'docs/'},
  [RepositoryTypes.GitHub]: {src: 'src/', docs: 'docs/'},
  [RepositoryTypes.BitBucket]: {src: '/', docs: ''},
  [RepositoryTypes.ButBucketCloud]: {src: '/', docs: ''}
};

function getPipelineDefaultPaths (preferences) {
  const result = {};
  [
    RepositoryTypes.GitHub,
    RepositoryTypes.GitLab,
    RepositoryTypes.BitBucket,
    RepositoryTypes.ButBucketCloud
  ].forEach((aType) => {
    const srcPreference = `${aType.toLowerCase()}.default.src.directory`;
    const docsPreference = `${aType.toLowerCase()}.default.doc.directory`;
    const src = preferences.getPreferenceValue(srcPreference) || defaultValues[aType].src;
    const docs = preferences.getPreferenceValue(docsPreference) || defaultValues[aType].docs;
    result[aType] = {src, docs};
  });
  return result;
}

export {
  defaultValues,
  getPipelineDefaultPaths
};
