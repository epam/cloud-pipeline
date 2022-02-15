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
import {Collapse} from 'antd';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import styles from './hcs-sequence-selector.css';

class HcsSequenceSelector extends React.Component {
  state = {
    expandedKeys: []
  }

  componentDidMount () {
    const {selectedSequence} = this.props;
    if (selectedSequence) {
      this.onTogglePanel(selectedSequence);
    }
  }

  componentDidUpdate (prevProps) {
    const {selectedSequence} = this.props;
    if (!prevProps.selectedSequence && prevProps.selectedSequence !== selectedSequence) {
      this.onTogglePanel(selectedSequence);
    }
  }

  onTogglePanel = (keys) => {
    return this.setState({expandedKeys: keys});
  };

  onChangeTimepoint = (sequenceId, timepoint) => {
    const {onChangeTimepoint} = this.props;
    onChangeTimepoint && onChangeTimepoint(sequenceId, timepoint);
  };

  renderCollapseHeader = (sequence = {}) => {
    const {selectedSequence} = this.props;
    const timePoints = (sequence.timeSeries || []).length;
    return (
      <div>
        <span className="cp-title">
          {`Sequence ${sequence.sequence}`}
        </span>
        <span style={{marginLeft: '10px'}}>
          {`${timePoints} time point${timePoints > 1 ? 's' : ''}`}
        </span>
        {sequence.id === selectedSequence && (
          <span
            className={classNames(
              styles.activeBadge,
              'cp-timepoint-button-active'
            )}
          >
            Active
          </span>
        )}
      </div>
    );
  };

  render () {
    const {
      sequences,
      selectedSequence,
      selectedTimepoint
    } = this.props;
    if (
      !sequences ||
      !sequences.length ||
      (sequences.length === 1 && (sequences[0].timeSeries || []).length <= 1)
    ) {
      return null;
    }
    const {expandedKeys} = this.state;
    return (
      <div className={styles.container}>
        <span
          className={classNames(
            styles.header,
            'cp-title'
          )}
        >
          Time series
        </span>
        <Collapse
          className="cp-collapse-small"
          onChange={this.onTogglePanel}
          activeKey={expandedKeys}
        >
          {sequences.map(sequence => (
            <Collapse.Panel
              header={this.renderCollapseHeader(sequence)}
              key={sequence.sequence}
            >
              <div
                className={styles.timepointsContainer}
              >
                {(sequence.timeSeries || []).map(timepoint => (
                  <div
                    className={classNames(
                      styles.timepoint,
                      selectedTimepoint === timepoint && selectedSequence === sequence.id
                        ? 'cp-timepoint-button-active'
                        : 'cp-timepoint-button',
                      {
                        [styles.active]: selectedTimepoint === timepoint &&
                        selectedSequence === sequence.id
                      }
                    )}
                    key={timepoint}
                    onClick={() => this.onChangeTimepoint(sequence.sequence, timepoint)}
                  >
                    {timepoint}
                  </div>
                ))}
              </div>
            </Collapse.Panel>
          ))}
        </Collapse>
      </div>
    );
  }
}

HcsSequenceSelector.propTypes = {
  sequences: PropTypes.oneOfType([PropTypes.object, PropTypes.array]),
  selectedSequence: PropTypes.string,
  selectedTimepoint: PropTypes.string,
  onChangeTimepoint: PropTypes.func
};

export default HcsSequenceSelector;
