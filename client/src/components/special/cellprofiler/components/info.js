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
import PropTypes from 'prop-types';
import classNames from 'classnames';
import {observer} from 'mobx-react';
import {Input} from 'antd';
import styles from './cell-profiler.css';
import displayDate from '../../../../utils/displayDate';
import UserName from '../../UserName';

function AnalysisPipelineInfo (props) {
  const {
    pipeline
  } = props;
  if (!pipeline) {
    return null;
  }
  const renderEditableProperty = (options = {}) => {
    const {
      property,
      title
    } = options;
    if (!pipeline || !property || !title) {
      return null;
    }
    const onChange = (e) => {
      pipeline[property] = e.target.value;
    };
    return (
      <div
        className={
          classNames(
            styles.cellProfilerParameter
          )
        }
      >
        <div
          className={styles.cellProfilerParameterTitle}
        >
          {title}
        </div>
        <Input
          className={styles.cellProfilerParameterValue}
          value={pipeline[property]}
          onChange={onChange}
        />
      </div>
    );
  };
  return (
    <div>
      {renderEditableProperty({property: 'name', title: 'Name'})}
      {renderEditableProperty({property: 'description', title: 'Description'})}
      <div
        className={
          classNames(
            styles.cellProfilerParameter,
            'cp-text-not-important'
          )
        }
      >
        <span>Created {displayDate(pipeline.createdDate)}</span>
        {
          pipeline.author && (
            <span style={{whiteSpace: 'pre'}}>
              {` by `}
              <UserName
                userName={pipeline.author}
                showIcon
              />
            </span>
          )
        }
      </div>
    </div>
  );
}

AnalysisPipelineInfo.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  pipeline: PropTypes.object
};

export default observer(AnalysisPipelineInfo);
