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
import PropTypes from 'prop-types';
import {observer} from 'mobx-react';
import CodeEditor from './CodeEditor';

@observer
export default class CodeEditorFormItem extends React.Component {

  static propTypes = {
    value: PropTypes.string,
    readOnly: PropTypes.bool,
    onChange: PropTypes.func,
    disabled: PropTypes.bool,
    editorClassName: PropTypes.string,
    editorLanguage: PropTypes.string,
    editorLineWrapping: PropTypes.bool
  };

  static defaultProps = {
    editorLineWrapping: true,
    readOnly: false
  };

  state = {
    value: null,
    visible: true
  };

  onChange = () => {
    this.props.onChange && this.props.onChange(this.state.value);
  };

  onValueChange = (value) => {
    this.setState({
      value: value
    }, this.onChange);
  };

  editor;

  initializeEditor = (editor) => {
    this.editor = editor;
  };

  render () {
    return (
      <CodeEditor
        readOnly={this.props.readOnly}
        ref={this.initializeEditor}
        className={this.props.editorClassName}
        language={this.props.editorLanguage || 'shell'}
        onChange={code => this.onValueChange(code)}
        lineWrapping={this.props.editorLineWrapping}
        defaultCode={this.props.value}
      />
    );
  }

  componentWillReceiveProps (nextProps) {
    if ('value' in nextProps) {
      const value = nextProps.value;
      if (value !== this.state.value) {
        this.setState({
          value: value
        });
        this.editor && this.editor.setValue(value);
      }
    }
  }

  componentDidMount () {
    this.setState({
      value: this.props.value
    });
  }

  reset = () => {
    if (this.editor) {
      this.editor.reset();
    }
  };
}
