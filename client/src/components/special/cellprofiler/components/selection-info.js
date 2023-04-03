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
import {observer, inject} from 'mobx-react';
import classNames from 'classnames';
import {Alert, Input} from 'antd';
import styles from './cell-profiler.css';

function countLabel (count, label, showSingle = true) {
  if (count === 0) {
    return null;
  }
  if (count === 1 && !showSingle) {
    return null;
  }
  if (count === 1) {
    return (<span className={styles.count}>1 {label}</span>);
  }
  return (<span className={styles.count}>{count} {label}s</span>);
}

const Wrapper = ({className, style, children, showAsAlert}) => {
  if (showAsAlert) {
    return (
      <Alert
        message={children}
        type="info"
        showIcon
        className={className}
        style={style}
      />
    );
  }
  return (
    <div
      className={className}
      style={style}
    >
      {children}
    </div>
  );
};

class SelectionInfo extends React.Component {
  state = {
    info: false
  };

  toggleInfo = () => {
    this.setState({
      info: !this.state.info
    });
  };

  renderInfo = () => {
    const {info} = this.state;
    if (info) {
      const {
        onOpenEvaluations
      } = this.props;
      let evaluations = (<b>Evaluations</b>);
      if (typeof onOpenEvaluations === 'function') {
        evaluations = (
          <a onClick={() => onOpenEvaluations()}>
            Evaluations
          </a>
        );
      }
      return (
        <div style={{marginTop: 5}}>
          Analysis will be submitted as a <b>batch job</b> that can be
          found at {evaluations} tab.<br />
          For fine-tuning pipeline select a single well, field, and time point.
          {' '}
          <a onClick={this.toggleInfo}>Less info...</a>
        </div>
      );
    }
    return (
      <div style={{marginTop: 5}}>
        Analysis will be submitted as a <b>batch job</b>.
        {' '}
        <a onClick={this.toggleInfo}>More info...</a>
      </div>
    );
  };

  onChangeAnalysisAlias = (e) => {
    const {
      analysis
    } = this.props;
    if (!analysis) {
      return;
    }
    analysis.alias = e.target.value;
  };

  render () {
    const {
      className,
      style,
      analysis,
      showOnlyIfMultipleSelection,
      showAsAlert
    } = this.props;
    if (!analysis || !analysis.namesAndTypes) {
      return null;
    }
    /**
     * @type {NamesAndTypes}
     */
    const namesAndTypesModule = analysis.namesAndTypes;
    if (showOnlyIfMultipleSelection && !namesAndTypesModule.multipleFields) {
      return null;
    }
    return (
      <Wrapper
        className={
          classNames(
            className,
            styles.block,
            styles.analysisSelectionInfo
          )
        }
        style={style}
        showAsAlert={showAsAlert}
      >
        <div>
          <div>
            <b style={{whiteSpace: 'pre'}}>Selection: </b>
            {countLabel(namesAndTypesModule.wells.length, 'well')}
            {countLabel(namesAndTypesModule.commonFields.length, 'field')}
            {countLabel(namesAndTypesModule.timePoints.length, 'time point')}
            {countLabel(namesAndTypesModule.zCoordinates.length, 'z-plane', false)}
          </div>
          {this.renderInfo()}
          <div style={{marginTop: 5}}>
            <Input
              placeholder="Analysis alias"
              value={analysis.alias}
              onChange={this.onChangeAnalysisAlias}
            />
          </div>
        </div>
      </Wrapper>
    );
  }
}

SelectionInfo.propTypes = {
  analysis: PropTypes.object,
  className: PropTypes.string,
  style: PropTypes.object,
  showOnlyIfMultipleSelection: PropTypes.bool,
  showAsAlert: PropTypes.bool,
  onOpenEvaluations: PropTypes.func
};

const hcsAnalysisInjected = inject('hcsAnalysis')(observer(SelectionInfo));

export {
  hcsAnalysisInjected as HcsAnalysisSelectionInfo
};
export default observer(SelectionInfo);
