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
import {inject, observer} from 'mobx-react';
import {Alert} from 'antd';
import CellProfiler from '../../cellprofiler/components';

function HcsImageAnalysis (props) {
  const {
    hcsAnalysis,
    className,
    style,
    expandSingle,
    onToggleResults,
    resultsVisible,
    batchMode,
    toggleBatchMode,
    availableModes
  } = props;
  if (!hcsAnalysis || !hcsAnalysis.available) {
    return (
      <div
        className={className}
        style={style}
      >
        <Alert
          showIcon
          type="warning"
          message="Analysis not available"
        />
      </div>
    );
  }
  return (
    <CellProfiler
      analysis={hcsAnalysis}
      className={className}
      style={style}
      expandSingle={expandSingle}
      onToggleResults={onToggleResults}
      resultsVisible={resultsVisible}
      batchMode={batchMode}
      toggleBatchMode={toggleBatchMode}
      availableModes={availableModes}
    />
  );
}

HcsImageAnalysis.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  expandSingle: PropTypes.bool,
  onToggleResults: PropTypes.func,
  resultsVisible: PropTypes.bool,
  batchMode: PropTypes.bool,
  toggleBatchMode: PropTypes.func,
  availableModes: PropTypes.oneOfType([
    PropTypes.string,
    PropTypes.arrayOf(PropTypes.string)
  ])
};

export default inject('hcsAnalysis')(observer(HcsImageAnalysis));
