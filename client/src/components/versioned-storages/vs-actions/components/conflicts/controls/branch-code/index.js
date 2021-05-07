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
import Caret from './caret';
import getClassNameForChange from './utilities/class-name-for-change';
import getStyleForChange from './utilities/style-for-change';
import getBranchCodeFromSelection from './utilities/branch-code-selection';
import LineStates from '../../utilities/conflicted-file/line-states';
import ChangeStatuses from '../../utilities/changes/statuses';
import modificationsRenderConfig from '../changes-display-config';
import styles from '../../conflicts.css';

function getLineTextWithoutBreak (line, branch) {
  return (line?.text[branch] || '').replace(/\n/g, '');
}

function findBranchCodeLine (element) {
  if (!element) {
    return element;
  }
  if (element.dataset && element.dataset.hasOwnProperty('branchCodeLine')) {
    return element;
  }
  if (element.hasChildNodes()) {
    for (let c = 0; c < element.children.length; c++) {
      const child = findBranchCodeLine(element.children[c]);
      if (child) {
        return child;
      }
    }
  }
  return undefined;
}

class BranchCode extends React.PureComponent {
  static defaultContentRenderer = (item, props) => (
    <div
      data-branch-code-line={item.key}
    >
      {getLineTextWithoutBreak(item, props?.branch) || ''}
    </div>
  );

  state = {
    caret: undefined,
    focused: false,
    characterSize: 0,
    marginLeft: 0
  };

  container;
  character;

  componentWillUnmount () {
    this.detachEventHandlers();
    this.container = null;
  }

  componentDidMount () {
    const {onInitialized} = this.props;
    onInitialized && onInitialized(this.container);
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    this.correctCharacterSizeInfo();
  }

  attachEventHandlers = () => {
    if (this.container) {
      this.container.addEventListener('copy', this.copy);
      this.container.addEventListener('keydown', this.keyDown);
      this.container.addEventListener('keypress', this.keyPressed);
    }
  };

  detachEventHandlers = () => {
    if (this.container) {
      this.container.removeEventListener('copy', this.copy);
      this.container.removeEventListener('keydown', this.keyDown);
      this.container.removeEventListener('keypress', this.keyPressed);
    }
  };

  copy = () => {
    const selection = document.getSelection();
    if (selection) {
      console.log('COPY', getBranchCodeFromSelection(selection));
    }
  };

  moveCursor = (options, callback) => {
    const {
      branch,
      lines = []
    } = this.props;
    const {
      caret
    } = this.state;
    if (!caret) {
      return false;
    }
    const {
      line: prevLine,
      offset: prevOffset
    } = caret;
    let {
      line,
      offset
    } = options;
    let correctOffset = false;
    const maxLineIndex = Math.max(
      ...lines.map(line => line.lineNumber[branch])
    );
    if (line > maxLineIndex) {
      line = maxLineIndex;
      offset = Infinity;
      correctOffset = true;
    } else if (line <= 0) {
      line = 1;
      offset = 0;
      correctOffset = true;
    }
    const newLine = lines.find(l => l.lineNumber[branch] === line);
    if (correctOffset && newLine) {
      const length = getLineTextWithoutBreak(newLine, branch).length;
      offset = Math.max(0, Math.min(length, offset));
    }
    if (newLine !== prevLine || offset !== prevOffset) {
      this.setState({
        caret: {
          line: newLine,
          offset
        }
      }, () => {
        // eslint-disable-next-line
        callback && callback(true);
      });
      return true;
    }
    // eslint-disable-next-line
    callback && callback(false);
    return false;
  };

  keyDown = e => {
    const {branch, editable} = this.props;
    const {focused, caret} = this.state;
    if (focused && editable && caret) {
      const {
        line: currentLine,
        offset: prevOffset = 0
      } = caret;
      let line = currentLine.lineNumber[branch];
      let offset = prevOffset;
      let handled = false;
      console.log(
        'key down',
        e.key,
        [
          e.shiftKey && 'SHIFT',
          e.ctrlKey && 'CTRL',
          e.altKey && 'ALT',
          e.metaKey && 'META'
        ].filter(Boolean).join(' ')
      );
      switch (e.key) {
        case 'ArrowDown':
          handled = true;
          line += 1;
          break;
        case 'ArrowUp':
          handled = true;
          line -= 1;
          break;
        case 'ArrowLeft':
          handled = true;
          if (e.metaKey) {
            offset = 0;
          } else {
            offset -= 1;
          }
          if (offset < 0) {
            line -= 1;
            offset = Infinity;
          }
          break;
        case 'ArrowRight':
          handled = true;
          if (e.metaKey) {
            offset = getLineTextWithoutBreak(currentLine, branch).length;
          } else {
            offset += 1;
          }
          if (currentLine && offset > getLineTextWithoutBreak(currentLine, branch).length) {
            line += 1;
            offset = 0;
          }
          break;
          // todo: page up, page down, etc
        case 'Backspace':
          return this.keyPressed(e);
      }
      if (handled) {
        e.stopPropagation();
        e.preventDefault();
        this.moveCursor({
          line,
          offset
        });
        return false;
      }
    }
  };

  keyPressed = e => {
    const {branch, editable, lines = []} = this.props;
    const {focused, caret} = this.state;
    if (focused && editable && caret) {
      const {
        line: currentLine,
        offset: prevOffset = 0
      } = caret;
      let line = currentLine.lineNumber[branch];
      const nextLine = lines.find(l => l.lineNumber[branch] === line + 1);
      const prevLine = lines.find(l => l.lineNumber[branch] === line - 1);
      const lineLength = getLineTextWithoutBreak(currentLine, branch).length;
      let offset = prevOffset;
      let handled = false;
      const {key} = e;
      if (/^Enter$/i.test(key)) {
        // insert new line
        // const before = (currentLine.text[branch] || '').slice(0, offset - 1);
        // currentLine.text[branch] = (currentLine.text[branch] || '').slice(offset);
        // if (currentLine.file) {
        //   const newLine = currentLine.file.insertLine(currentLine, before, branch);
        //   offset = 0;
        //   // if (newLine) {
        //   //   line = newLine.lineNumber[branch];
        //   //   offset = 0;
        //   // }
        // }
        handled = true;
      } else if (/^Backspace$/i.test(key)) {
        console.log('BACKSPACE!');
        if (offset > 0) {
          handled = true;
          const before = (currentLine.text[branch] || '').slice(0, offset - 1);
          const after = (currentLine.text[branch] || '').slice(offset);
          currentLine.text[branch] = `${before}${after}`;
          offset -= 1;
        } else {
          // todo: join this line and previous one into 1 (remove line break)
        }
      } else if (/^Delete$/i.test(key)) {
        if (offset < lineLength) {
          handled = true;
          const before = (currentLine.text[branch] || '').slice(0, offset);
          const after = (currentLine.text[branch] || '').slice(offset + 1);
          currentLine.text[branch] = `${before}${after}`;
        } else {
          // todo: join this line and next one into 1 (remove line break)
        }
      } else {
        // insert character
        handled = true;
        const before = (currentLine.text[branch] || '').slice(0, offset);
        const after = (currentLine.text[branch] || '').slice(offset);
        currentLine.text[branch] = `${before}${key}${after}`;
        offset += 1;
      }
      if (handled) {
        e.stopPropagation();
        e.preventDefault();
        this.moveCursor({
          offset,
          line
        }, (moved) => {
          if (currentLine.file) {
            currentLine.file.notify();
          }
        });
        return false;
      }
    }
  };

  initializeContainer = (container) => {
    this.detachEventHandlers();
    this.container = container;
    this.attachEventHandlers();
    const {onInitialized} = this.props;
    onInitialized && onInitialized(this.container);
  };

  correctCharacterSizeInfo = () => {
    const span = this.character;
    if (span && span.textContent && span.textContent.length) {
      const round = o => Math.floor(o * 100) / 100.0;
      const characterRect = span.getBoundingClientRect();
      const parentRect = span.parentElement?.getBoundingClientRect();
      const characterSize = round(characterRect.width / (span.textContent || '').length);
      const marginLeft = parentRect
        ? round(characterRect.left - parentRect.left)
        : this.state.marginLeft;
      if (
        this.state.characterSize !== characterSize ||
        this.state.marginLeft !== marginLeft
      ) {
        this.setState({
          characterSize,
          marginLeft
        });
      }
    }
  };

  initializeCharacter = (span) => {
    if (span) {
      this.character = span;
      this.correctCharacterSizeInfo();
    }
  };

  renderCodeLine = (line) => {
    const {
      branch,
      editable,
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
            getStyleForChange(modification, hidden)
          )
        }
        className={
          getClassNameForChange(
            modification,
            {
              isFirst: true,
              hidden
            }
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
            getStyleForChange(modification, hidden)
          )
        }
        className={
          getClassNameForChange(
            modification,
            {
              isLast: true,
              hidden
            }
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
            getClassNameForChange(
              modification,
              {hidden}
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
              getStyleForChange(modification, hidden)
            )
          }
          onMouseUp={
            editable
              ? (e) => this.onCaretUpdate(e, line)
              : undefined
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

  onCaretUpdate = (e, line) => {
    if (e && line) {
      const {branch} = this.props;
      const {characterSize = 0} = this.state;
      let offset = 0;
      const branchCodeLine = findBranchCodeLine(e.currentTarget);
      if (branchCodeLine && characterSize) {
        const {left} = branchCodeLine.getBoundingClientRect();
        const x = e.clientX - left;
        offset = Math.round(x / characterSize);
      }
      offset = Math.max(0, Math.min((line.text[branch] || '').length, offset));
      this.setState({
        caret: {
          line,
          offset
        }
      });
    }
  };

  onFocus = () => {
    this.setState({
      focused: true
    });
  };

  onBlur = () => {
    this.setState({
      focused: false
    });
  };

  render () {
    const {
      branch,
      className,
      editable,
      lines = [],
      lineHeight,
      onCursorPositionChange,
      onMouseDown,
      onScroll,
      style,
      lineStyle
    } = this.props;
    const {
      caret,
      focused,
      marginLeft,
      characterSize
    } = this.state;
    const cursorLine = caret?.line;
    return (
      <div
        className={
          classNames(
            className || styles.code,
            {
              [styles.focused]: focused
            }
          )
        }
        data-vs-branch={branch}
        ref={this.initializeContainer}
        style={style}
        onMouseDown={onMouseDown}
        onScroll={onScroll}
        onFocus={editable ? this.onFocus : undefined}
        onBlur={editable ? this.onBlur : undefined}
        tabIndex={editable ? 0 : undefined}
      >
        <div
          className={getClassNameForChange()}
          style={
            Object.assign(
              {},
              lineStyle,
              {
                height: 0,
                overflow: 'hidden',
                visibility: 'hidden'
              }
            )
          }
        >
          <span
            ref={this.initializeCharacter}
          >
            {caret?.text || '_'}
          </span>
        </div>
        {
          caret && cursorLine && (
            <Caret
              characterSize={characterSize}
              line={cursorLine.lineNumber[branch]}
              lineHeight={lineHeight}
              marginLeft={marginLeft}
              marginTop={(cursorLine.changesBefore[branch] || 0) * 2}
              offset={caret.offset}
              onPositionChanged={focused ? onCursorPositionChange : undefined}
              text={getLineTextWithoutBreak(cursorLine, branch)}
            />
          )
        }
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
  editable: PropTypes.bool,
  lines: PropTypes.array,
  modificationsBranch: PropTypes.oneOfType([PropTypes.symbol, PropTypes.string]),
  lineHeight: PropTypes.number,
  lineStyle: PropTypes.object,
  onCursorPositionChange: PropTypes.func,
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
            onMouseDown={e => e.stopPropagation()}
            onClick={wrapAction(() => modification.apply(onRefresh))}
            tabIndex={0}
          />
        ),
        (
          <Icon
            className={styles.action}
            key="omit"
            type="close"
            onMouseDown={e => e.stopPropagation()}
            onClick={wrapAction(() => modification.discard(onRefresh))}
            tabIndex={0}
          />
        )
      ]
      : [];
    if (rtl) {
      actions.reverse();
    }
    const controls = [
      <div
        key="line number"
        className={styles.number}
      >
        {index}
      </div>,
      <div
        key="actions"
        className={styles.actions}
      >
        {actions}
      </div>
    ];
    if (rtl) {
      controls.reverse();
    }
    return controls;
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
