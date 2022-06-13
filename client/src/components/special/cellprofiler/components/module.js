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
      cpModule,
      onExpandedChange,
      movable,
      removable
    } = this.props;
    if (!cpModule) {
      return null;
    }
    const prevent = (e) => {
      if (e) {
        e.stopPropagation();
      }
    };
    const moveUp = (e) => {
      prevent(e);
      cpModule.moveUp();
    };
    const moveDown = (e) => {
      prevent(e);
      cpModule.moveDown();
    };
    const remove = (e) => {
      prevent(e);
      cpModule.remove();
    };
    return (
      <div
        className={
          classNames(
            styles.cellProfilerCpModuleHeader,
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
          {cpModule.displayName}
          {
            cpModule.hasExecutionResults && (
              <Icon type="file" />
            )
          }
        </b>
        {
          !cpModule.hidden && movable && (
            <Button
              className={styles.action}
              size="small"
              disabled={cpModule.isFirst}
              onClick={moveUp}
            >
              <Icon
                type="up"
              />
            </Button>
          )
        }
        {
          !cpModule.hidden && movable && (
            <Button
              className={styles.action}
              size="small"
              disabled={cpModule.isLast}
              onClick={moveDown}
            >
              <Icon
                type="down"
              />
            </Button>
          )
        }
        {
          !cpModule.hidden && removable && (
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
      cpModule,
      expanded
    } = this.props;
    if (!cpModule) {
      return null;
    }
    const params = cpModule.getAllVisibleParameters();
    return (
      <div
        className={
          classNames(
            styles.cellProfilerCpModule,
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
              className={styles.cellProfilerCpModuleContent}
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
  cpModule: PropTypes.object,
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
