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

import LineStates from '../conflicted-file/line-states';
import Branches from '../conflicted-file/branches';
import {
  getBranchCodeRangeFromSelection
} from '../../controls/branch-code/utilities/branch-code-selection';

function getLineTextWithoutBreak (line, branch) {
  return (line?.text[branch] || '').replace(/\n/g, '');
}

function fireCaretUpdateEvent (file, branch, line, offset) {
  file.fireEvent(
    'caretUpdate',
    {
      line: line.hasOwnProperty('lineNumber')
        ? line.lineNumber[branch]
        : line,
      offset
    },
    branch
  );
}

const wrapApplyFunction = (file, branch, newPosition, oldPosition, apply, revert) => {
  const {
    line: newLine,
    offset: newOffset
  } = newPosition || {};
  const {
    line: oldLine,
    offset: oldOffset
  } = oldPosition || {};
  return (finalOperation = true) => {
    apply();
    if (finalOperation) {
      file.preProcessLines(branch);
      file.notify();
      fireCaretUpdateEvent(file, branch, newLine, newOffset);
    }
    return (finalRevertOperation = true) => {
      revert();
      if (finalRevertOperation) {
        file.preProcessLines(branch);
        file.notify();
        fireCaretUpdateEvent(file, branch, oldLine, oldOffset);
      }
    };
  };
};

const wrapOperation = (
  operationInfo,
  file,
  branch,
  newPosition,
  oldPosition,
  apply,
  revert
) => {
  const applyFn = wrapApplyFunction(
    file,
    branch,
    newPosition,
    oldPosition,
    apply,
    revert
  );
  const revertFn = applyFn();
  return {
    handled: true,
    operation: {
      apply: applyFn,
      revert: revertFn,
      isFinite: false,
      ...(operationInfo || {})
    }
  };
};

export const INSERT_TEXT_KEY = 'paste-text';
export const INSERTED_TEXT = Symbol('inserted text');

export default function inputOperation (event, file, branch, currentLine, offset) {
  const {key} = event;
  let inputOperation;
  const parameters = [];
  if (/^Enter$/i.test(key)) {
    inputOperation = wrapSelectionRemoval(insertLineBreak);
  } else if (/^Backspace$/i.test(key)) {
    inputOperation = wrapSelectionRemoval(deleteSymbolBefore, true);
  } else if (/^(Delete|Del)$/i.test(key)) {
    inputOperation = wrapSelectionRemoval(deleteSymbolAfter, true);
  } else if (key === INSERT_TEXT_KEY) {
    inputOperation = wrapSelectionRemoval(insertText);
    parameters.push(event[INSERTED_TEXT]);
  } else {
    // insert character
    inputOperation = wrapSelectionRemoval(insertSymbol);
    parameters.push(key);
  }
  if (file && inputOperation) {
    const {
      operation,
      handled
    } = inputOperation(
      file,
      branch,
      currentLine,
      offset,
      ...parameters
    );
    if (operation) {
      file.registerOperation(operation);
    }
    return handled;
  }
  return false;
}

export function clearSelection () {
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
}

function removeCurrentSelection (file, branch) {
  const selection = getBranchCodeRangeFromSelection(document.getSelection());
  if (!selection) {
    return undefined;
  }
  const {
    start,
    end
  } = selection;
  clearSelection();
  if (!start || !end) {
    return undefined;
  }
  const {
    lineKey: startLineKey
  } = start;
  let {
    offset: startOffset
  } = start;
  const {
    lineKey: endLineKey
  } = end;
  let {
    offset: endOffset
  } = end;
  if (startLineKey === endLineKey && startOffset === endOffset) {
    return undefined;
  }
  const lines = file.getLines(branch, new Set([]));
  const allowedStates = new Set([LineStates.inserted, LineStates.original]);
  const ignoredStates = new Set(
    Object.values(LineStates)
      .filter(state => !allowedStates.has(state))
  );
  let _startLine = lines.find(line => line.key === startLineKey);
  while (_startLine && !allowedStates.has(_startLine.state[branch])) {
    _startLine = _startLine[branch];
    startOffset = 0;
  }
  let _endLine = lines.find(line => line.key === endLineKey);
  while (_endLine && !allowedStates.has(_endLine.state[branch])) {
    _endLine = _endLine.previous[branch];
    endOffset = Infinity;
  }
  if (!_startLine || !_endLine) {
    return undefined;
  }
  const startLine = _startLine;
  const endLine = _endLine;
  endOffset = Math.min(endOffset, getLineTextWithoutBreak(endLine, branch).length);
  const actions = [];
  const undoActions = [];
  const startLineOriginalText = startLine.text[branch];
  const endLineOriginalText = endLine.text[branch];
  const linesBetween = file
    .getLines(branch, ignoredStates, startLine, endLine)
    .filter(line => line !== startLine);
  const statesMap = new WeakMap();
  linesBetween.forEach(line => {
    statesMap[line] = line.state[branch];
  });
  actions.push(() => {
    startLine.text[branch] =
      `${startLineOriginalText.slice(0, startOffset)}${endLineOriginalText.slice(endOffset)}`;
    linesBetween.forEach(line => {
      line.state[branch] = LineStates.omit;
    });
  });
  undoActions.push(() => {
    startLine.text[branch] = startLineOriginalText;
    linesBetween.forEach(line => {
      line.state[branch] = statesMap[line];
    });
  });
  actions.push(() => file.preProcessLines(branch));
  undoActions.reverse();
  undoActions.push(() => file.preProcessLines(branch));
  const apply = () => {
    actions.forEach(f => f());
  };
  const revert = () => {
    undoActions.forEach(f => f());
  };
  apply();
  return {
    position: {
      line: startLine,
      offset: startOffset
    },
    apply,
    revert
  };
}

function wrapSelectionRemoval (operationFn, ignoreIfSelectionRemoved = false) {
  return function (file, branch, currentLine, offset, ...parameters) {
    const removeSelectionOperation = removeCurrentSelection(file, branch);
    if (!removeSelectionOperation) {
      return operationFn.call(this, file, branch, currentLine, offset, ...parameters);
    }
    const {
      apply: applyRemovalFn = () => {},
      revert: revertRemovalFn = () => {},
      position = {}
    } = removeSelectionOperation || {};
    const {
      line: lineAfterRemoval = currentLine,
      offset: offsetAfterRemoval = offset
    } = position;
    if (ignoreIfSelectionRemoved || typeof operationFn !== 'function') {
      return wrapOperation(
        {type: 'removeSelection', isFinite: true},
        file,
        branch,
        position,
        position,
        applyRemovalFn,
        revertRemovalFn
      );
    }
    const {
      operation,
      ...rest
    } = operationFn(file, branch, lineAfterRemoval, offsetAfterRemoval, ...parameters);
    if (operation) {
      return {
        operation: {
          ...operation,
          apply: (...args) => {
            applyRemovalFn();
            operation.apply && operation.apply(...args);
          },
          revert: (...args) => {
            operation.revert && operation.revert(...args);
            revertRemovalFn();
          },
          isFinite: true
        },
        ...rest
      };
    }
    return rest;
  };
}

function insertText (file, branch, currentLine, offset, text) {
  if (text && text.length) {
    const lines = text
      .split('\n');
    const originalText = currentLine.text[branch] || '';
    const before = originalText.slice(0, offset);
    const after = originalText.slice(offset);
    const processedLines = lines.map((line, index, array) => {
      let addonBefore = '';
      let addonAfter = '';
      if (index === 0) {
        addonBefore = before;
      }
      if (index === array.length - 1) {
        addonAfter = after;
      } else {
        addonAfter = '\n';
      }
      return `${addonBefore}${line}${addonAfter}`;
    });
    let temp = currentLine;
    let lastLine = currentLine;
    const subOperations = [];
    const undoSubOperations = [];
    for (let l = 0; l < processedLines.length; l++) {
      const line = temp;
      const backUp = line.text[branch];
      subOperations.push(() => {
        line.text[branch] = processedLines[l];
      });
      undoSubOperations.push(() => {
        line.text[branch] = backUp;
      });
      temp.text[branch] = processedLines[l];
      if (l < processedLines.length - 1) {
        temp = file.insertLine(temp, '', branch);
        if (!temp) {
          break;
        }
        const nextLine = temp;
        const state = nextLine.state;
        subOperations.push(() => {
          nextLine.state = {...state};
        });
        undoSubOperations.push(() => {
          nextLine.state[branch] = LineStates.omit;
        });
      }
      lastLine = temp;
    }
    const finalLine = lastLine;
    undoSubOperations.reverse();
    return wrapOperation(
      {type: 'insert', isFinite: true},
      file,
      branch,
      {
        line: finalLine,
        offset: (finalLine.text[branch] || '').length - after.length
      },
      {
        line: currentLine,
        offset
      },
      () => {
        subOperations.forEach(f => f());
      },
      () => {
        undoSubOperations.forEach(f => f());
      }
    );
  }
  return {
    handled: false
  };
}

function insertSymbol (file, branch, currentLine, offset, symbol) {
  const originalText = currentLine.text[branch] || '';
  const before = originalText.slice(0, offset);
  const after = originalText.slice(offset);
  currentLine.text[branch] = `${before}${symbol}${after}`;
  return wrapOperation(
    {type: 'input', isFinite: false},
    file,
    branch,
    {
      line: currentLine,
      offset: offset + 1
    },
    {
      line: currentLine,
      offset
    },
    () => {
      currentLine.text[branch] = `${before}${symbol}${after}`;
    },
    () => {
      currentLine.text[branch] = originalText;
    }
  );
}

function insertLineBreak (file, branch, currentLine, offset) {
  const originalText = currentLine.text[branch] || '';
  const before = originalText.slice(0, offset);
  const after = originalText.slice(offset);
  const newLine = file.insertLine(currentLine, after, branch);
  if (newLine) {
    const lineStates = {...newLine.state};
    return wrapOperation(
      {type: 'enter', isFinite: false},
      file,
      branch,
      {
        line: newLine,
        offset: 0
      },
      {
        line: currentLine,
        offset
      },
      () => {
        currentLine.text[branch] = `${before}\n`;
        newLine.state = {...lineStates};
      },
      () => {
        currentLine.text[branch] = originalText;
        Branches.forEach(b => {
          newLine.state[b] = LineStates.omit;
        });
      }
    );
  }
  return {
    line: currentLine.lineNumber[branch],
    offset
  };
}

function deleteSymbolBefore (file, branch, currentLine, offset) {
  const lineIndex = currentLine.lineNumber[branch];
  const lines = file.getLines(branch).slice(1);
  const prevLine = lines.find(l => l.lineNumber[branch] === lineIndex - 1);
  const originalText = currentLine.text[branch] || '';
  const before = originalText.slice(0, offset);
  const after = originalText.slice(offset);
  if (before.length > 0) {
    // just remove symbol before
    return wrapOperation(
      {type: 'backspace', isFinite: false},
      file,
      branch,
      {
        line: currentLine,
        offset: offset - 1
      },
      {
        line: currentLine,
        offset
      },
      () => {
        currentLine.text[branch] = `${before.slice(0, -1)}${after}`;
      },
      () => {
        currentLine.text[branch] = originalText;
      }
    );
  }
  if (prevLine) {
    // join this line and previous one into 1 (remove line break)
    const prevLineOriginalText = prevLine.text[branch];
    const currentLineState = currentLine.state[branch];
    const newOffset = getLineTextWithoutBreak(prevLine, branch).length;
    return wrapOperation(
      {type: 'backspace', isFinite: false},
      file,
      branch,
      {
        line: prevLine,
        offset: newOffset
      },
      {
        line: currentLine,
        offset
      },
      () => {
        currentLine.state[branch] = LineStates.omit;
        prevLine.text[branch] = `${getLineTextWithoutBreak(prevLine, branch)}${after}`;
      },
      () => {
        currentLine.state[branch] = currentLineState;
        prevLine.text[branch] = prevLineOriginalText;
      }
    );
  }
  return {
    handled: false
  };
}

function deleteSymbolAfter (file, branch, currentLine, offset) {
  if (!file) {
    return {
      line: currentLine.lineNumber[branch],
      offset
    };
  }
  const lineIndex = currentLine.lineNumber[branch];
  const lines = file.getLines(branch).slice(1);
  const nextLine = lines.find(l => l.lineNumber[branch] === lineIndex + 1);
  const originalText = currentLine.text[branch] || '';
  const before = originalText.slice(0, offset);
  const after = originalText.slice(offset);
  const isLineBreakSymbol = after === '\n';
  if (after.length > 1) {
    // just remove symbol after
    return wrapOperation(
      {type: 'delete', isFinite: false},
      file,
      branch,
      {
        line: currentLine,
        offset
      },
      {
        line: currentLine,
        offset
      },
      () => {
        currentLine.text[branch] = `${before}${after.slice(1)}`;
      },
      () => {
        currentLine.text[branch] = originalText;
      }
    );
  } else if (nextLine) {
    // join this line and next one into 1 (remove line break)
    const nextLineText = nextLine.text[branch];
    const nextLineState = nextLine.state[branch];
    return wrapOperation(
      {type: 'delete', isFinite: false},
      file,
      branch,
      {
        line: currentLine,
        offset
      },
      {
        line: currentLine,
        offset
      },
      () => {
        currentLine.text[branch] = `${before}${nextLineText}`;
        nextLine.state[branch] = LineStates.omit;
      },
      () => {
        currentLine.text[branch] = originalText;
        nextLine.state[branch] = nextLineState;
      }
    );
  } else if (isLineBreakSymbol || after.length === 1) {
    // no next line; we must remove line break symbol
    // OR
    // last symbol in the line, but it is not a line break.
    // just removing it
    return wrapOperation(
      {type: 'delete', isFinite: false},
      file,
      branch,
      {
        line: currentLine,
        offset
      },
      {
        line: currentLine,
        offset
      },
      () => {
        currentLine.text[branch] = before;
      },
      () => {
        currentLine.text[branch] = originalText;
      }
    );
  }
  return {
    handled: false
  };
}
