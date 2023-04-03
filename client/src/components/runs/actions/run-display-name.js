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
import React from 'react';
import {inject, observer} from 'mobx-react';
import AppLocalization from '../../../utils/localization';

function RunDisplayName ({className, style, run, localization}) {
  if (!run) {
    return localization.localizedString('pipeline');
  }
  if (run.pipelineName && run.version) {
    return (
      <span
        className={className}
        style={style}
      >
        <b>{run.pipelineName}</b>
        {' '}
        ({run.version})
      </span>
    );
  }
  if (run.pipelineName) {
    return (
      <span
        className={className}
        style={style}
      >
        <b>{run.pipelineName}</b>
      </span>
    );
  }
  if (run.dockerImage) {
    const [,, image = ''] = run.dockerImage.split('/');
    const imageName = image.split(':')[0] || 'run';
    return (
      <span
        className={className}
        style={style}
      >
        <b>{imageName}</b>
      </span>
    );
  }
  return (
    <span
      className={className}
      style={style}
    >
      {localization.localizedString('pipeline')}
    </span>
  );
}

export default inject(() => ({localization: AppLocalization.localization}))(
  observer(RunDisplayName)
);
