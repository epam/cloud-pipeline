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
import codeEditorStyles from './CodeEditor.css';

import CodeMirror from 'codemirror';

import 'codemirror/lib/codemirror.css';
import 'codemirror/mode/javascript/javascript';
import 'codemirror/mode/python/python';
import 'codemirror/mode/xml/xml';
import 'codemirror/mode/jsx/jsx';
import 'codemirror/mode/r/r';
import 'codemirror/mode/shell/shell';
import 'codemirror/mode/htmlembedded/htmlembedded';
import 'codemirror/mode/htmlmixed/htmlmixed';
import 'codemirror/addon/mode/simple.js';
import 'codemirror/addon/lint/lint.js';
import 'codemirror/addon/dialog/dialog.css';
import 'codemirror/addon/dialog/dialog';
import 'codemirror/addon/display/fullscreen.css';
import 'codemirror/addon/display/fullscreen';
import 'codemirror/addon/display/placeholder';
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
export default class CodeEditor extends React.Component {

  static propTypes = {
    language: PropTypes.string,
    fileExtension: PropTypes.string,
    fileName: PropTypes.string,
    code: PropTypes.string,
    readOnly: PropTypes.bool,
    onChange: PropTypes.func,
    className: PropTypes.string,
    defaultCode: PropTypes.string,
    lineWrapping: PropTypes.bool,
    supportsFullScreen: PropTypes.bool,
    lineNumbers: PropTypes.bool,
    placeholder: PropTypes.string,
    scrollbarStyle: PropTypes.oneOf([null, 'native']),
    // 'delayedUpdate': if set to true,
    // updates CodeMirror control after a small delay
    // (after Modal container appearance animation finished).
    delayedUpdate: PropTypes.bool,
    style: PropTypes.object
  };

  static defaultProps = {
    lineNumbers: true,
    scrollbarStyle: 'native'
  };

  codeMirrorInstance;

  clear () {
    if (this.codeMirrorInstance) {
      this.codeMirrorInstance.off('change', this._onCodeChange);
      this.codeMirrorInstance.setValue('');
      this.codeMirrorInstance.on('change', this._onCodeChange);
    }
  }

  _onCodeChange = (editor) => {
    const {line, ch} = editor.getCursor();
    if (this.props.onChange) {
      this.props.onChange(editor.getValue());
    }
    editor.setCursor(line, ch);
  };

  _getFileLanguage = () => {
    if (this.props.language) {
      return this.props.language;
    }
    let fileExtension = this.props.fileExtension;
    if (!fileExtension) {
      if (this.props.fileName) {
        const parts = this.props.fileName.split('.');
        fileExtension = parts[parts.length - 1];
      }
    }
    if (!fileExtension) {
      return 'python';
    }
    switch (fileExtension.toLowerCase()) {
      case 'sh': return 'shell';
      case 'xml': return 'xml';
      case 'json':
      case 'js':
        return 'javascript';
      case 'r': return 'r';
      case 'wdl': return 'wdl';
      case 'txt': return 'text';
      default: return 'python';
    }
  };

  _initializeCodeMirror = (textArea) => {
    if (!textArea) {
      this.codeMirrorInstance = null;
      return;
    }
    const defaultExtraKeys = {
      'Ctrl-Space': 'autocomplete',
      'Tab': function (cm) {
        const spaces = new Array(cm.getOption('indentUnit') + 1).join(' ');
        cm.replaceSelection(spaces);
      }
    };
    const extraKeys = this.props.supportsFullScreen ? {
      'F11': function (cm) {
        cm.setOption('fullScreen', !cm.getOption('fullScreen'));
      },
      'Esc': function (cm) {
        if (cm.getOption('fullScreen')) cm.setOption('fullScreen', false);
      },
      ...defaultExtraKeys
    } : defaultExtraKeys;
    CodeMirror.commands.autocomplete = function (cm) {
      cm.showHint({hint: CodeMirror.hint.anyword});
      cm.showHint({hint: CodeMirror.hint.javascript});
    };
    this.codeMirrorInstance = CodeMirror.fromTextArea(textArea, {
      mode: this._getFileLanguage(),
      autoCloseBrackets: true,
      autoCloseTags: true,
      showHint: true,
      readOnly: this.props.readOnly,
      search: true,
      indentUnit: 4,
      tabSize: 4,
      lineWrapping: this.props.lineWrapping,
      spellcheck: true,
      lineNumbers: this.props.lineNumbers,
      extraKeys: extraKeys,
      placeholder: this.props.placeholder,
      scrollbarStyle: this.props.scrollbarStyle
    });
    this.codeMirrorInstance.setValue(this.props.defaultCode || '');
    this._updateEditor();
    if (this.props.delayedUpdate) {
      setTimeout(() => this._updateEditor(), 500);
    }
  };

  _updateEditor = (code = undefined) => {
    if (!this.codeMirrorInstance) {
      return;
    }
    this.codeMirrorInstance.setSize('100%', '100%');
    this.codeMirrorInstance.display.wrapper.style.backgroundColor = this.props.readOnly
      ? '#f0f0f0' : 'white';
    this.codeMirrorInstance.setOption('mode', this._getFileLanguage());
    this.codeMirrorInstance.setOption('readOnly', this.props.readOnly);
    this.codeMirrorInstance.off('change', this._onCodeChange);
    if (code) {
      this.codeMirrorInstance.setValue(code || '');
    } else if (this.props.code) {
      this.codeMirrorInstance.setValue(this.props.code || '');
    }
    this.codeMirrorInstance.on('change', this._onCodeChange);
    process.nextTick(() => {
      if (this.codeMirrorInstance) {
        this.codeMirrorInstance.refresh();
      }
    });
  };

  render () {
    return (
      <div
        className={this.props.className || codeEditorStyles.container}
        style={this.props.style}>
        <div
          className={
            this.props.readOnly
              ? codeEditorStyles.readOnlyEditor
              : codeEditorStyles.editor
          }>
          <input type="textarea" ref={this._initializeCodeMirror} />
        </div>
      </div>
    );
  }

  reset = () => {
    this._updateEditor(this.props.defaultCode || this.props.code);
  };

  setValue = (value) => {
    if (this.codeMirrorInstance) {
      this.codeMirrorInstance.setValue(value || '');
    }
  };

  componentDidUpdate (props) {
    if (props.defaultCode !== this.props.defaultCode && !this.props.code) {
      this._updateEditor(this.props.defaultCode);
    } else {
      this._updateEditor();
    }
  }

  componentWillMount () {
    CodeMirror.defineSimpleMode('wdl', {
      start: [
        {regex: /"(?:[^\\]|\\.)*?(?:"|$)/, token: 'string'},
        {regex: /'(?:[^\\]|\\.)*?(?:'|$)/, token: 'string'},
        {
          regex: /(workflow|task)(\s+)([A-Za-z$][\w$]*)/,
          token: ['keyword', null, 'variable-2']
        },
        {
          regex: /(call)(\s+)([A-Za-z$][\w$]*)(\s+)(as)(\s+)([A-Za-z$][\w$]*)/,
          token: ['keyword', null, 'variable-1', null, 'keyword', null, 'variable-2']
        },
        {
          regex: /(call)(\s+)([A-Za-z$][\w$]*)/,
          token: ['keyword', null, 'variable-2']
        },
        {
          regex: /(?:task|call|workflow|if|scatter|while|input|output|as)\b/,
          token: 'keyword'
        },
        {
          regex: /(command)(\s+)({)/,
          token: ['keyword', null, null],
          push: 'command_usual'
        },
        {
          regex: /(command)(\s+)(<<<)/,
          token: ['keyword', null, null],
          mode: {spec: 'text/x-sh', end: /(?:>>>)/}
        },
        {regex: /(?:Boolean|Int|Float|File|String)/, token: 'atom'},
        {
          regex: /0x[a-f\d]+|[-+]?(?:\.\d+|\d+\.?\d*)(?:e[-+]?\d+)?/i,
          token: 'number'
        },
        {regex: /\/\/.*/, token: 'comment'},
        {regex: /\#.*/, token: 'comment'},
        {regex: /\/(?:[^\\]|\\.)*?\//, token: 'variable-3'},
        {regex: /\/\*/, token: 'comment', next: 'comment'},
        {regex: /[-+\/*=<>!]+/, token: 'operator'},
        {regex: /[\{\[\(\:]/, indent: true},
        {regex: /[\}\]\)]/, dedent: true},
        {regex: /[A-Za-z$][\w$]*/, token: 'variable'},
      ],

      command_usual: [
        {regex: /\/\/.*/, token: 'comment'},
        {regex: /\#.*/, token: 'comment'},
        {regex: /(?:{)/, token: null, push: 'command_usual'},
        {regex: /(?:})/, token: null, pop: true}
      ],

      comment: [
        {regex: /.*?\*\//, token: 'comment', next: 'start'},
        {regex: /.*/, token: 'comment'}
      ],

      meta: {
        dontIndentStates: ['comment'],
        lineComment: '#'
      }
    });
  }
}
