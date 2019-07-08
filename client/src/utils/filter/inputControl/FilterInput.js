/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import React from 'react';
import {observer} from 'mobx-react';
import PropTypes from 'prop-types';
import codeEditorStyles from './FilterInput.css';

import CodeMirror from 'codemirror';

import 'codemirror/lib/codemirror.css';
import 'codemirror/mode/javascript/javascript';
import 'codemirror/mode/python/python';
import 'codemirror/mode/xml/xml';
import 'codemirror/mode/jsx/jsx';
import 'codemirror/mode/r/r';
import 'codemirror/mode/shell/shell';
import 'codemirror/addon/mode/simple.js';
import 'codemirror/addon/lint/lint.js';
import 'codemirror/addon/dialog/dialog.css';
import 'codemirror/addon/dialog/dialog';
import 'codemirror/addon/display/fullscreen.css';
import 'codemirror/addon/display/fullscreen';
import 'codemirror/addon/search/searchcursor';
import 'codemirror/addon/search/search';
import 'codemirror/addon/search/match-highlighter';
import 'codemirror/addon/search/jump-to-line';
import 'codemirror/addon/search/matchesonscrollbar.css';
import 'codemirror/addon/search/matchesonscrollbar';
import 'codemirror/addon/scroll/annotatescrollbar';
import 'codemirror/addon/hint/anyword-hint';
import 'codemirror/addon/hint/javascript-hint';
import 'codemirror/addon/hint/show-hint.css';
import 'codemirror/addon/hint/show-hint';
import 'codemirror/addon/edit/matchtags';
import 'codemirror/addon/edit/matchbrackets';
import 'codemirror/addon/edit/closebrackets';
import 'codemirror/addon/edit/closetag';
import 'codemirror/addon/edit/continuelist';
import 'codemirror/addon/edit/trailingspace';

@observer
export default class FilterInput extends React.Component {

  static propTypes = {
    className: PropTypes.string,
    defaultValue: PropTypes.string,
    onEnter: PropTypes.func,
    onEdit: PropTypes.func,
    keywords: PropTypes.array,
    isError: PropTypes.bool,
    onDownKeyPressed: PropTypes.func,
    onUpKeyPressed: PropTypes.func
  };

  codeMirrorInstance;

  clear () {
    if (this.codeMirrorInstance) {
      this.codeMirrorInstance.off('change', this._onCodeChange);
      this.codeMirrorInstance.setValue('');
      this.codeMirrorInstance.on('change', this._onCodeChange);
    }
  }

  _onCodeChange = (editor, change) => {
    if (this.props.onEdit && change.from) {
      this.props.onEdit(editor.getValue(), change.from.ch + (change.from.sticky === 'after' ? -1 : 0));
    }
  };

  _onEnter = () => {
    if (this.props.onEnter) {
      this.props.onEnter(this.codeMirrorInstance.getValue());
    }
  };

  _onBlur = () => {
    this._onEnter();
  };

  _keyHandled = (instance, name, event) => {
    if (name && name.toLowerCase() === 'enter') {
      this._onEnter();
    }
    if (name && name.toLowerCase() === 'down') {
      return false;
    }
    if (name && name.toLowerCase() === 'up') {
      return false;
    }
  };

  _beforeChange = (instance, change) => {
    const newText = change.text.join('').replace(/\n/g, ''); // remove ALL \n !
    if (change.update) {
      change.update(change.from, change.to, [newText]);
    }
    return true;
  };

  _onDownKeyPressed = () => {
    if (this.props.onDownKeyPressed) {
      this.props.onDownKeyPressed();
    }
  };

  _onUpKeyPressed = () => {
    if (this.props.onUpKeyPressed) {
      this.props.onUpKeyPressed();
    }
  };

  _initializeCodeMirror = (textArea) => {
    if (!textArea) {
      this.codeMirrorInstance = null;
      return;
    }
    const defaultExtraKeys = {
      'Down': this._onDownKeyPressed,
      'Up': this._onUpKeyPressed
    };
    this.codeMirrorInstance = CodeMirror.fromTextArea(textArea, {
      mode: 'filter',
      lineNumbers: false,
      autoCloseBrackets: false,
      autoCloseTags: false,
      showHint: true,
      readOnly: false,
      search: false,
      indentUnit: 4,
      tabSize: 4,
      lineWrapping: true,
      spellcheck: true,
      extraKeys: defaultExtraKeys
    });
    this.updateEditor(this.props.defaultValue, (this.props.defaultValue || '').length);
  };

  updateEditor = (code = undefined, position = undefined) => {
    if (!this.codeMirrorInstance) {
      return;
    }
    this.codeMirrorInstance.setSize('100%', '100%');
    this.codeMirrorInstance.display.wrapper.style.backgroundColor = this.props.isError ? '#fff9f9' : 'white';
    this.codeMirrorInstance.display.wrapper.style.borderRadius = '4px';
    this.codeMirrorInstance.display.wrapper.style.border = this.props.isError ? '1px solid #f00' : '1px solid #ddd';
    this.codeMirrorInstance.off('change', this._onCodeChange);
    this.codeMirrorInstance.off('keyHandled', this._keyHandled);
    this.codeMirrorInstance.off('blur', this._onBlur);
    this.codeMirrorInstance.off('beforeChange', this._beforeChange);
    if (code) {
      this.codeMirrorInstance.setValue(code || '');
      this.codeMirrorInstance.focus();
      if (position !== undefined) {
        this.codeMirrorInstance.setCursor({line: 0, ch: position});
      }
    }
    this.codeMirrorInstance.on('change', this._onCodeChange);
    this.codeMirrorInstance.on('keyHandled', this._keyHandled);
    this.codeMirrorInstance.on('blur', this._onBlur);
    this.codeMirrorInstance.on('beforeChange', this._beforeChange);
  };

  render () {
    return (
      <div className={this.props.className || codeEditorStyles.container}>
        <div
          className={codeEditorStyles.editor}>
          <input type="textarea" ref={this._initializeCodeMirror} />
        </div>
      </div>
    );
  }

  componentDidUpdate () {
    this.updateEditor();
  }

  componentWillMount () {
    let keywordsRule = {};
    if (this.props.keywords && this.props.keywords.length > 0) {
      const keywordsRegex = (this.props.keywords || []).map(keyword => {
        if (keyword.regex) {
          return keyword.fieldName;
        } else {
          return keyword.fieldName.split('.').join('\\.');
        }
      });
      const keywordsRegexp = new RegExp(`(?:${keywordsRegex.join('|')})\\b`, 'i');
      keywordsRule = {
        regex: keywordsRegexp,
        token: ['keyword']
      };
    }
    CodeMirror.defineSimpleMode('filter', {
      start: [
        keywordsRule,
        {
          regex: /\s+(?:or|and)\s+/i,
          token: ['variable-2']
        },
        {
          regex: /(\s)*(<|<=|=|!=|>=|>)(\s)*('(?:[^\\]|\\.)*?(?:'|$)|"(?:[^\\]|\\.)*?(?:"|$)|[^\s'"()[\]{}/\\]+)/,
          token: [null, null, null, 'comment']
        }
      ]
    });
  }
}
