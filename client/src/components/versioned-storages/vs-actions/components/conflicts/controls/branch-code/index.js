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
import {
  compareSelectionsInfo,
  getBranchCodeRangeFromSelection
} from './utilities/branch-code-selection';
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
    marginLeft: 0,
    selection: undefined
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
      document.addEventListener('keydown', this.keyDown);
      document.addEventListener('keypress', this.keyPressed);
      document.addEventListener('selectionchange', this.onSelectionChange);
    }
  };

  detachEventHandlers = () => {
    if (this.container) {
      this.container.removeEventListener('copy', this.copy);
      document.removeEventListener('keydown', this.keyDown);
      document.removeEventListener('keypress', this.keyPressed);
      document.removeEventListener('selectionchange', this.onSelectionChange);
    }
  };

  copy = () => {
    const selection = document.getSelection();
    if (selection) {
      // todo: COPY getBranchCodeFromSelection(selection)
    }
  };

  onSelectionChange = () => {
    const selection = getBranchCodeRangeFromSelection(document.getSelection());
    const {file, branch, editable} = this.props;
    if (!editable) {
      return;
    }
    let selectionInfo;
    if (selection && file && branch) {
      const lines = file.getLines(branch, new Set([]));
      const {
        start,
        end
      } = selection;
      if (start && end) {
        const {
          lineKey: startLineKey
        } = start;
        const {
          lineKey: endLineKey
        } = end;
        const startLine = lines.find(line => line.key === startLineKey);
        const endLine = lines.find(line => line.key === endLineKey);
        if (startLine && endLine) {
          selectionInfo = {
            start: {
              line: startLine,
              ...start
            },
            end: {
              line: endLine,
              ...end
            }
          };
        }
      }
    }
    if (!compareSelectionsInfo(this.state.selection, selectionInfo)) {
      this.setState({selection: selectionInfo});
    }
  };

  clearSelection = () => {
    if (window.getSelection) {
      if (window.getSelection().empty) {
        // Chrome
        window.getSelection().empty();
      } else if (window.getSelection().removeAllRanges) {
        // Firefox
        window.getSelection().removeAllRanges();
      }
    } else if (document.selection) {
      // IE
      document.selection.empty();
    }
  };

  selectCode = (from, to) => {
    const {
      file,
      branch,
      editable,
      lineIdentifier
    } = this.props;
    if (
      editable &&
      file &&
      branch &&
      from &&
      to &&
      from.line &&
      to.line &&
      document.createRange
    ) {
      const fromNode = document.getElementById(`${branch}-${lineIdentifier(from.line)}`);
      const toNode = document.getElementById(`${branch}-${lineIdentifier(to.line)}`);
      if (!fromNode || !toNode) {
        return;
      }
      const range = document.createRange();
      const fromText = getLineTextWithoutBreak(from.line, branch);
      const toText = getLineTextWithoutBreak(to.line, branch);
      const getFirstTextChild = node => {
        if (!node) {
          return undefined;
        }
        if (node.nodeType === Node.TEXT_NODE) {
          return node;
        }
        if (!node.hasChildNodes()) {
          return undefined;
        }
        const children = node.childNodes;
        for (let c = 0; c < children.length; c++) {
          const textChild = getFirstTextChild(children[c]);
          if (textChild) {
            return textChild;
          }
        }
        return undefined;
      };
      const fromNodeTextChild = getFirstTextChild(fromNode);
      const toNodeTextChild = getFirstTextChild(toNode);
      if (fromText.length > 0 && fromNodeTextChild) {
        range.setStart(fromNodeTextChild, Math.max(0, Math.min(fromText.length, from.offset)));
      } else {
        range.setStart(fromNode, 0);
      }
      if (toText.length > 0 && toNodeTextChild) {
        range.setEnd(toNodeTextChild, Math.max(0, Math.min(toText.length, to.offset)));
      } else {
        range.setEnd(toNode, 1);
      }
      window.getSelection().removeAllRanges();
      window.getSelection().addRange(range);
    }
  };

  selectAll = () => {
    const {file, branch, editable} = this.props;
    if (editable && file && branch) {
      const lines = file.getLines(branch).slice(1);
      const start = lines[0];
      const end = lines[lines.length - 1];
      this.selectCode(
        {
          line: start,
          offset: 0
        },
        {
          line: end,
          offset: Infinity
        }
      );
    }
  };

  moveCursor = (options, callback) => {
    const {
      branch,
      file
    } = this.props;
    if (!file) {
      return false;
    }
    const lines = file.getLines(branch, new Set([]));
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
    const maxLineIndex = Math.max(
      ...lines.map(line => line.lineNumber[branch])
    );
    if (line > maxLineIndex) {
      line = maxLineIndex;
      offset = Infinity;
    } else if (line <= 0) {
      line = 1;
      offset = 0;
    }
    const newLine = lines.find(l => l.lineNumber[branch] === line);
    if (newLine) {
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
    const {
      branch,
      editable,
      lineHeight,
      verticalScroll
    } = this.props;
    const {focused, caret} = this.state;
    if (focused && editable && caret) {
      const {
        line: currentLine,
        offset: prevOffset = 0
      } = caret;
      let line = currentLine.lineNumber[branch];
      let offset = prevOffset;
      let handled = false;
      let isSelectionMode = e.shiftKey;
      const getPageSize = () => {
        let pageSize = 10;
        if (typeof verticalScroll === 'function') {
          const scroll = verticalScroll();
          if (scroll) {
            pageSize = Math.ceil(scroll.clientHeight / lineHeight);
          }
        }
        return pageSize;
      };
      switch (e.key) {
        case 'a':
          if (e.ctrlKey || e.metaKey) {
            handled = true;
            isSelectionMode = true;
            this.selectAll();
          }
          break;
        case 'Home':
          handled = true;
          line = 1;
          offset = 0;
          break;
        case 'End':
          handled = true;
          line = Infinity;
          offset = Infinity;
          break;
        case 'Down':
        case 'ArrowDown':
          handled = true;
          line += 1;
          break;
        case 'Up':
        case 'ArrowUp':
          handled = true;
          line -= 1;
          break;
        case 'Left':
        case 'ArrowLeft':
          handled = true;
          if (e.metaKey) {
            offset = 0;
          } else if (e.altKey) {
            const text = (currentLine.text[branch] || '').slice(0, offset);
            const result = /([A-Za-z\d]+|[^A-Za-z\d\s]+)\s*$/.exec(text);
            if (result) {
              offset = result.index;
            } else {
              line -= 1;
              offset = Infinity;
            }
          } else {
            offset -= 1;
          }
          if (offset < 0) {
            line -= 1;
            offset = Infinity;
          }
          break;
        case 'Right':
        case 'ArrowRight':
          handled = true;
          if (e.metaKey) {
            offset = getLineTextWithoutBreak(currentLine, branch).length;
          } else if (e.altKey) {
            const text = (currentLine.text[branch] || '').slice(offset);
            const result = /(\s?[A-Za-z\d]*\S)/.exec(text);
            if (result) {
              offset += (result.index + result[0].length);
            } else {
              line += 1;
              offset = 0;
            }
          } else {
            offset += 1;
          }
          if (currentLine && offset > getLineTextWithoutBreak(currentLine, branch).length) {
            line += 1;
            offset = 0;
          }
          break;
        case 'PageDown':
          handled = true;
          line += getPageSize();
          break;
        case 'PageUp':
          handled = true;
          line -= getPageSize();
          break;
        case 'Del':
        case 'Delete':
        case 'Backspace':
          return this.keyPressed(e);
        case 'Esc':
        case 'Escape':
          handled = true;
          isSelectionMode = false;
          break;
      }
      if (handled && !isSelectionMode) {
        this.clearSelection();
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
    const {branch, editable, file} = this.props;
    if (!file) {
      return;
    }
    const lines = file.getLines(branch, new Set([]));
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
      let offset = Math.max(0, Math.min(lineLength, prevOffset));
      let handled = false;
      const {key} = e;
      const before = (currentLine.text[branch] || '').slice(0, offset);
      const after = (currentLine.text[branch] || '').slice(offset);
      if (/^Enter$/i.test(key)) {
        // insert new line
        currentLine.text[branch] = `${before}\n`;
        const newLine = file.insertLine(currentLine, after, branch);
        if (newLine) {
          offset = 0;
          line = newLine.lineNumber[branch];
        }
        handled = true;
      } else if (/^Backspace$/i.test(key)) {
        if (before.length > 0) {
          // just remove symbol before
          handled = true;
          currentLine.text[branch] = `${before.slice(0, -1)}${after}`;
          offset -= 1;
        } else if (prevLine) {
          // join this line and previous one into 1 (remove line break)
          handled = true;
          currentLine.state[branch] = LineStates.omit;
          offset = getLineTextWithoutBreak(prevLine, branch).length;
          prevLine.text[branch] = `${getLineTextWithoutBreak(prevLine, branch)}${after}`;
          line = prevLine.lineNumber[branch];
        }
      } else if (/^(Delete|Del)$/i.test(key)) {
        const isLineBreakSymbol = after === '\n';
        if (after.length > 1) {
          // just remove symbol after
          handled = true;
          currentLine.text[branch] = `${before}${after.slice(1)}`;
        } else if (isLineBreakSymbol && nextLine) {
          // join this line and next one into 1 (remove line break)
          handled = true;
          currentLine.text[branch] = `${before}${nextLine.text[branch]}`;
          nextLine.state[branch] = LineStates.omit;
        } else if (isLineBreakSymbol) {
          // no next line; we must remove line break symbol
          handled = true;
          currentLine.text[branch] = before;
        } else if (after.length === 1) {
          // last symbol in the line, but it is not a line break.
          // just removing it
          handled = true;
          currentLine.text[branch] = before;
        } else if (nextLine) {
          // for some reason we don't have a line break;
          // normally, that would not happen.
          // But for safety reason, we'll join this lines anyway
          handled = true;
          currentLine.text[branch] = `${before}${nextLine.text[branch]}`;
          nextLine.state[branch] = LineStates.omit;
        }
      } else {
        // insert character
        handled = true;
        currentLine.text[branch] = `${before}${key}${after}`;
        offset += 1;
      }
      if (handled) {
        file.preProcessLines(branch);
        file.notify();
        e.stopPropagation();
        e.preventDefault();
        this.moveCursor({
          offset,
          line
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
      file,
      lineHeight,
      lineIdentifier,
      lineStyle = {},
      modificationsBranch,
      renderContent = BranchCode.defaultContentRenderer,
      renderOptions
    } = this.props;
    if (!file || line.meta.start) {
      return [];
    }
    const changeBranch = modificationsBranch || branch;
    let modification = line.change[branch];
    if (modificationsBranch) {
      modification = line.change[modificationsBranch] || modification;
    }
    const modificationAction = file.changes.find(
      change => change.branch === changeBranch &&
        change.lineIndex(changeBranch) === line.lineNumber[changeBranch] &&
        change.status === ChangeStatuses.prepared
    );
    const isFirstLineOfModification = modification && modification.start === line;
    const isFirstActualLineOfModification = modification && modification.first(branch) === line;
    const isLastLineOfModification = modification && modification.end === line;
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
          id={`${branch}-${lineIdentifier(line)}`}
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
          onMouseDown={
            editable
              ? (e) => this.onCaretUpdate(e, line)
              : undefined
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
      offset = Math.max(0, Math.min(getLineTextWithoutBreak(line, branch).length, offset));
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
      file,
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
    if (!file) {
      return null;
    }
    const lines = file.getLines(branch, new Set([]));
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
  file: PropTypes.object,
  editable: PropTypes.bool,
  modificationsBranch: PropTypes.oneOfType([PropTypes.symbol, PropTypes.string]),
  lineIdentifier: PropTypes.func,
  lineHeight: PropTypes.number,
  lineStyle: PropTypes.object,
  onCursorPositionChange: PropTypes.func,
  onMouseDown: PropTypes.func,
  onInitialized: PropTypes.func,
  onRefresh: PropTypes.func,
  onScroll: PropTypes.func,
  renderContent: PropTypes.func,
  renderOptions: PropTypes.object,
  style: PropTypes.object,
  verticalScroll: PropTypes.func
};

BranchCode.defaultProps = {
  lineIdentifier: line => `line-${line.key}`
};

function renderLineNumberWithActions (line, props) {
  const {
    disabled = false,
    file: conflictedFile,
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
      if (conflictedFile) {
        conflictedFile.registerUndoOperation(action());
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
    file,
    hideModificationActions = false,
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
      file={file}
      modificationsBranch={modificationsBranch}
      lineHeight={lineHeight}
      onMouseDown={onMouseDown}
      onRefresh={onRefresh}
      renderContent={renderLineNumberWithActions}
      renderOptions={{
        disabled,
        file,
        rtl,
        hideModificationActions
      }}
      style={
        Object.assign(
          {
            backgroundColor: modificationsRenderConfig.background
          },
          style || {}
        )}
      lineStyle={undefined}
      lineIdentifier={line => `line-numbers-${line.key}`}
    />
  );
}

BranchCode.LineNumbers = BranchCodeLineNumbers;

export default BranchCode;
