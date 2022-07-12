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

function Circle ({className, pending, empty}) {
  return (
    <svg
      className={
        classNames(
          className,
          styles.cpModuleExecutionIndicator,
          {
            [styles.pending]: pending,
            [styles.empty]: empty
          }
        )
      }
      viewBox="0 0 6 6"
      width="6"
      height="6"
    >
      <circle
        cx="3"
        cy="3"
        r="2"
      />
    </svg>
  );
}

function CellProfilerModuleHeaderRenderer (props) {
  const {
    cpModule,
    movable,
    removable
  } = props;
  if (!cpModule) {
    return null;
  }
  const renderIcon = () => {
    if (!cpModule.statusReporting) {
      return null;
    }
    if (cpModule.pending || cpModule.done) {
      return (
        <Circle
          className={
            classNames({
              'cp-primary': cpModule.pending,
              'cp-success': cpModule.done
            })
          }
          pending={cpModule.pending}
        />
      );
    } else {
      return (
        <Circle
          className="cp-text-not-important"
          empty
        />
      );
    }
  };
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
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        flex: 1
      }}
    >
      <b style={{marginRight: 'auto'}}>
        {renderIcon()}
        {cpModule.displayName}
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

function CellProfilerModuleRenderer ({cpModule}) {
  if (!cpModule) {
    return null;
  }
  const params = cpModule.getAllVisibleParameters();
  return (
    <div>
      {
        params.map((parameter) => (
          <CellProfilerParameter
            key={parameter.parameter.id}
            parameterValue={parameter}
          />
        ))
      }
    </div>
  );
}

CellProfilerModuleRenderer.propTypes = {
  cpModule: PropTypes.object
};

CellProfilerModuleHeaderRenderer.propTypes = {
  cpModule: PropTypes.object,
  removable: PropTypes.bool,
  movable: PropTypes.bool
};

CellProfilerModuleHeaderRenderer.defaultProps = {
  removable: true,
  movable: true
};

const CellProfilerModule = observer(CellProfilerModuleRenderer);
const CellProfilerModuleHeader = observer(CellProfilerModuleHeaderRenderer);

export {
  CellProfilerModule,
  CellProfilerModuleHeader
};
