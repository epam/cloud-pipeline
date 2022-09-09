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

function sequenceArraysAreEqual (a, b) {
  if (!a && !b) {
    return true;
  }
  if (!a || !b || a.length !== b.length) {
    return false;
  }
  const aSorted = a.slice().sort();
  const bSorted = b.slice().sort();
  for (let i = 0; i < aSorted.length; i++) {
    if (aSorted[i] !== bSorted[i]) {
      return false;
    }
  }
  return true;
}

class HcsSequenceSelector extends React.Component {
  state = {
    expandedKeys: []
  }

  componentDidMount () {
    const {selection = []} = this.props;
    this.onTogglePanel(selection.map(a => a.sequence));
  }

  componentDidUpdate (prevProps) {
    const {selection = []} = this.props;
    const currentSequences = selection.map(a => a.sequence);
    const previousSequences = (prevProps.selection || []).map(a => a.sequence);
    if (!sequenceArraysAreEqual(currentSequences, previousSequences)) {
      this.onTogglePanel(currentSequences);
    }
  }

  getSelectedTimePoints = (sequence) => {
    const {selection = []} = this.props;
    return selection
      .filter(o => o.sequence === sequence)
      .map(o => o.timePoint);
  }

  sequenceTimePointIsSelected = (sequence, timePoint) => {
    return this.getSelectedTimePoints(sequence).includes(timePoint);
  };

  onTogglePanel = (keys) => {
    return this.setState({expandedKeys: keys});
  };

  onChangeTimePoint = (sequenceId, timePoint, event) => {
    const {
      onChange,
      selection = [],
      multiple
    } = this.props;
    const append = multiple && event && event.nativeEvent && event.nativeEvent.shiftKey;
    if (typeof onChange === 'function') {
      if (append) {
        const exists = this.sequenceTimePointIsSelected(sequenceId, timePoint.id);
        if (exists && selection.length === 1) {
          return;
        }
        if (exists) {
          onChange(
            selection.filter(o => o.sequence !== sequenceId || o.timePoint !== timePoint.id)
          );
        } else {
          onChange([...selection, {sequence: sequenceId, timePoint: timePoint.id}]);
        }
      } else {
        onChange([{sequence: sequenceId, timePoint: timePoint.id}]);
      }
    }
  };

  renderCollapseHeader = (sequence = {}) => {
    const {selection = []} = this.props;
    const timePoints = (sequence.timeSeries || []).length;
    const selected = selection.some(o => o.sequence === sequence.id);
    return (
      <div>
        <span className="cp-title">
          {`Sequence ${sequence.sequence}`}
        </span>
        <span style={{marginLeft: '10px'}}>
          {`${timePoints} time point${timePoints > 1 ? 's' : ''}`}
        </span>
        {selected && (
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
      sequences
    } = this.props;
    if (
      !sequences ||
      !sequences.length ||
      (sequences.length === 1 && (sequences[0].timeSeries || []).length <= 1)
    ) {
      return null;
    }
    const {expandedKeys = []} = this.state;
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
                {(sequence.timeSeries || []).map(timePoint => {
                  const selected = this.sequenceTimePointIsSelected(sequence.id, timePoint.id);
                  return (
                    <div
                      className={
                        classNames(
                          styles.timepoint,
                          {
                            [styles.active]: selected,
                            'cp-timepoint-button-active': selected,
                            'cp-timepoint-button': !selected
                          }
                        )
                      }
                      key={timePoint.id}
                      onClick={
                        (event) => this.onChangeTimePoint(sequence.sequence, timePoint, event)
                      }
                    >
                      {timePoint.name}
                    </div>
                  );
                })}
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
  selection: PropTypes.oneOfType([PropTypes.object, PropTypes.array]),
  onChange: PropTypes.func,
  multiple: PropTypes.bool
};

export default HcsSequenceSelector;
