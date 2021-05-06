/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import {Icon} from 'antd';
import LineStates from './conflicted-file/line-states';
import ModificationType from './changes/types';
import ChangeStatuses from './changes/statuses';
import getBranchCodeFromSelection from './branch-code-selection';
import modificationsRenderConfig from './modifications-render-config';
import styles from '../conflicts.css';

function getClassNameForModificationType (type, status, isFirst, isLast, hidden) {
  return classNames(
    styles.line,
    {
      [styles.modification]: !!type,
      [styles.firstLine]: !!type && isFirst,
      [styles.lastLine]: !!type && isLast,
      [styles.applied]: status === ChangeStatuses.applied,
      [styles.discarded]: status === ChangeStatuses.discarded,
      [styles.hidden]: hidden
    }
  );
}

function getStyleForModificationType (type, status, hidden) {
  if (hidden) {
    return {
      backgroundColor: 'transparent',
      borderColor: 'transparent'
    };
  }
  let config;
  switch (type) {
    case ModificationType.edition: config = modificationsRenderConfig.edition; break;
    case ModificationType.conflict: config = modificationsRenderConfig.conflict; break;
    case ModificationType.deletion: config = modificationsRenderConfig.deletion; break;
    case ModificationType.insertion: config = modificationsRenderConfig.insertion; break;
    default:
      break;
  }
  const applied = status !== ChangeStatuses.prepared;
  if (config) {
    return {
      backgroundColor: applied ? 'transparent' : (config.background || config.color),
      borderColor: applied ? config.applied : config.color
    };
  }
  return {
    backgroundColor: 'transparent',
    borderColor: 'transparent'
  };
}

class BranchCode extends React.PureComponent {
  static defaultContentRenderer = item =>
    (item.line || '').replace(/\n/g, '') || '';

  container;

  componentWillUnmount () {
    this.detachEventHandlers();
    this.container = null;
  }

  componentDidMount () {
    const {onInitialized} = this.props;
    onInitialized && onInitialized(this.container);
  }

  attachEventHandlers = () => {
    if (this.container) {
      this.container.addEventListener('copy', this.copy);
    }
  };

  detachEventHandlers = () => {
    if (this.container) {
      this.container.removeEventListener('copy', this.copy);
    }
  };

  copy = () => {
    const selection = document.getSelection();
    if (selection) {
      console.log('COPY', getBranchCodeFromSelection(selection));
    }
  };

  initializeContainer = (container) => {
    this.detachEventHandlers();
    this.container = container;
    this.attachEventHandlers();
    const {onInitialized} = this.props;
    onInitialized && onInitialized(this.container);
  };

  renderCodeLine = (line) => {
    const {
      branch,
      lineHeight,
      lineStyle = {},
      modificationsBranch,
      renderContent = BranchCode.defaultContentRenderer,
      renderOptions
    } = this.props;
    if (line.meta.start) {
      return [];
    }
    let modification = line.change[branch];
    if (modificationsBranch) {
      modification = line.change[modificationsBranch] || modification;
    }
    const modificationAction = line.isChangeMarker[branch];
    const modificationType = modification ? modification.type : undefined;
    const modificationStatus = modification ? modification.status : undefined;
    const isFirstLineOfModification = modification && modification.items[0] === line;
    const isFirstActualLineOfModification = modification && modification.first(branch) === line;
    const isLastLineOfModification = modification &&
      modification.items[modification.items.length - 1] === line;
    const state = line.state[branch];
    const hidden = modification &&
      modificationsBranch &&
      (
        modification.branch !== modificationsBranch &&
        modification.branch !== branch
      );
    const firstMarker = modification && isFirstLineOfModification && (
      <div
        data-vs-skip-line="true"
        data-vs-line={line.key}
        key={`${line.key}-first-line-modification`}
        style={
          Object.assign(
            {},
            {
              height: 1,
              ...lineStyle
            },
            getStyleForModificationType(modificationType, modificationStatus, hidden)
          )
        }
        className={
          getClassNameForModificationType(
            modificationType,
            modificationStatus,
            true,
            false,
            hidden
          )
        }
      >
        {''}
      </div>
    );
    const lastMarker = modification && isLastLineOfModification && (
      <div
        data-vs-skip-line="true"
        data-vs-line={line.key}
        key={`${line.key}-last-line-modification`}
        style={
          Object.assign(
            {},
            {
              height: 1,
              ...lineStyle
            },
            getStyleForModificationType(modificationType, modificationStatus, hidden)
          )
        }
        className={
          getClassNameForModificationType(
            modificationType,
            modificationStatus,
            false,
            true,
            hidden
          )
        }
      >
        {''}
      </div>
    );
    let content;
    if (
      state === LineStates.original ||
      state === LineStates.inserted
    ) {
      content = (
        <div
          data-vs-line={line.key}
          className={
            getClassNameForModificationType(
              modificationType,
              modificationStatus,
              false,
              false,
              hidden
            )
          }
          key={line.key}
          style={
            Object.assign(
              {},
              {
                height: `${lineHeight}px`,
                lineHeight: `${lineHeight}px`,
                ...lineStyle
              },
              getStyleForModificationType(modificationType, modificationStatus, hidden)
            )
          }
        >
          {
            renderContent(
              line,
              {
                ...this.props,
                modification,
                modificationAction,
                isFirstLineOfModification,
                isLastLineOfModification,
                isFirstActualLineOfModification,
                hidden,
                ...(renderOptions || {})
              }
            )
          }
        </div>
      );
    }
    return [
      firstMarker,
      content,
      lastMarker
    ].filter(Boolean);
  };

  render () {
    const {
      branch,
      className,
      lines,
      onMouseDown,
      onScroll,
      style
    } = this.props;
    return (
      <div
        className={className || styles.code}
        data-vs-branch={branch}
        ref={this.initializeContainer}
        style={style}
        onMouseDown={onMouseDown}
        onScroll={onScroll}
      >
        {
          lines
            .map(this.renderCodeLine)
            .reduce((result, array) => ([...result, ...array]))
        }
      </div>
    );
  }
}

BranchCode.propTypes = {
  branch: PropTypes.oneOfType([PropTypes.symbol, PropTypes.string]),
  className: PropTypes.string,
  lines: PropTypes.array,
  modificationsBranch: PropTypes.oneOfType([PropTypes.symbol, PropTypes.string]),
  lineHeight: PropTypes.number,
  lineStyle: PropTypes.object,
  onMouseDown: PropTypes.func,
  onInitialized: PropTypes.func,
  onRefresh: PropTypes.func,
  onScroll: PropTypes.func,
  renderContent: PropTypes.func,
  renderOptions: PropTypes.object,
  style: PropTypes.object
};

function renderLineNumberWithActions (line, props) {
  const {
    disabled = false,
    rtl,
    hideModificationActions,
    modificationAction: modification,
    branch,
    onRefresh
  } = props;
  const index = line.lineNumber[branch];
  if (index >= 0) {
    const wrapAction = action => e => {
      if (disabled) {
        return;
      }
      e.stopPropagation();
      e.preventDefault();
      if (modification && modification.conflictedFile) {
        modification.conflictedFile.registerUndoOperation(action());
      }
    };
    const actions = (
      modification &&
      !hideModificationActions &&
      modification.status === ChangeStatuses.prepared
    )
      ? [
        (
          <Icon
            className={styles.action}
            key="apply"
            type={rtl ? 'double-left' : 'double-right'}
            onClick={wrapAction(() => modification.apply(onRefresh))}
          />
        ),
        (
          <Icon
            className={styles.action}
            key="omit"
            type="close"
            onClick={wrapAction(() => modification.discard(onRefresh))}
          />
        )
      ]
      : [];
    if (rtl) {
      actions.reverse();
    }
    return [
      !hideModificationActions && (
        <div
          key="actions"
          className={styles.actions}
        >
          {actions}
        </div>
      ),
      <div
        key="line number"
        className={styles.number}
      >
        {index}
      </div>
    ].filter(Boolean);
  }
  return '';
}

function BranchCodeLineNumbers (
  {
    branch,
    disabled = false,
    hideModificationActions = false,
    lines,
    modificationsBranch,
    lineHeight = 20,
    onMouseDown,
    onRefresh,
    rtl = false,
    style
  }
) {
  return (
    <BranchCode
      branch={branch}
      className={styles.lineNumbers}
      lines={lines}
      modificationsBranch={modificationsBranch}
      lineHeight={lineHeight}
      onMouseDown={onMouseDown}
      onRefresh={onRefresh}
      renderContent={renderLineNumberWithActions}
      renderOptions={{disabled, rtl, hideModificationActions}}
      style={
        Object.assign(
          {
            backgroundColor: modificationsRenderConfig.background
          },
          style || {}
        )}
      lineStyle={undefined}
    />
  );
}

BranchCode.LineNumbers = BranchCodeLineNumbers;

export default BranchCode;
