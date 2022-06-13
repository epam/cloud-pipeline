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
import {Button, Icon} from 'antd';
import {observer} from 'mobx-react';
import CellProfilerParameter from './parameter';
import styles from './cell-profiler.css';

class CellProfilerModule extends React.Component {
  renderHeader () {
    const {
      module,
      onExpandedChange,
      movable,
      removable
    } = this.props;
    if (!module) {
      return null;
    }
    const prevent = (e) => {
      if (e) {
        e.stopPropagation();
      }
    };
    const moveUp = (e) => {
      prevent(e);
      module.moveUp();
    };
    const moveDown = (e) => {
      prevent(e);
      module.moveDown();
    };
    const remove = (e) => {
      prevent(e);
      module.remove();
    };
    return (
      <div
        className={
          classNames(
            styles.cellProfilerModuleHeader,
            'cell-profiler-module-header'
          )
        }
        onClick={onExpandedChange}
      >
        <Icon
          type="right"
          className={styles.expandIndicator}
        />
        <b className={styles.title}>
          {module.displayName}
          {
            module.hasExecutionResults && (
              <Icon type="file" />
            )
          }
        </b>
        {
          !module.hidden && movable && (
            <Button
              className={styles.action}
              size="small"
              disabled={module.isFirst}
              onClick={moveUp}
            >
              <Icon
                type="up"
              />
            </Button>
          )
        }
        {
          !module.hidden && movable && (
            <Button
              className={styles.action}
              size="small"
              disabled={module.isLast}
              onClick={moveDown}
            >
              <Icon
                type="down"
              />
            </Button>
          )
        }
        {
          !module.hidden && removable && (
            <Button
              className={styles.action}
              size="small"
              type="danger"
              onClick={remove}
            >
              <Icon
                type="delete"
              />
            </Button>
          )
        }
      </div>
    );
  }

  render () {
    const {
      module,
      expanded
    } = this.props;
    if (!module) {
      return null;
    }
    const params = module.getAllVisibleParameters();
    return (
      <div
        className={
          classNames(
            styles.cellProfilerModule,
            'cell-profiler-module',
            {
              expanded,
              [styles.expanded]: expanded
            }
          )
        }
      >
        {
          this.renderHeader()
        }
        {
          expanded && (
            <div
              className={styles.cellProfilerModuleContent}
            >
              {
                params.map((parameter) => (
                  <CellProfilerParameter
                    key={parameter.parameter.id}
                    parameterValue={parameter}
                  />
                ))
              }
            </div>
          )
        }
      </div>
    );
  }
}

CellProfilerModule.propTypes = {
  module: PropTypes.object,
  expanded: PropTypes.bool,
  onExpandedChange: PropTypes.func,
  removable: PropTypes.bool,
  movable: PropTypes.bool
};

CellProfilerModule.defaultProps = {
  removable: true,
  movable: true
};

export default observer(CellProfilerModule);
