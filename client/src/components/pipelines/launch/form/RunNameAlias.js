/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import {
  Input
} from 'antd';
import styles from './RunNameAlias.css';

const ALIAS_MAX_LENGTH = 25;

class RunNameAlias extends React.Component {
  inputRef;
  containerRef;

  state={
    expanded: false,
    inputValue: undefined
  }

  componentWillUnmount () {
    if (this.containerRef) {
      this.containerRef.removeEventListener('keydown', this.onKeyDown);
    }
  }

  get isEditable () {
    const {onChange} = this.props;
    return !!onChange;
  }

  initializeContainer = (container) => {
    this.containerRef = container;
    if (this.containerRef) {
      this.containerRef.addEventListener('keydown', this.onKeyDown);
    }
  };

  initializeInput = (input) => {
    this.inputRef = input;
    if (this.inputRef) {
      this.updateInputValue();
      this.inputRef.focus();
    }
  };

  updateInputValue = () => {
    const {alias} = this.props;
    const {inputValue} = this.state;
    if (alias !== inputValue) {
      this.setState({inputValue: alias});
    }
  };

  onInputChange = (event = {}) => {
    const {value = ''} = event.target || {};
    const validityPattern = /^$|^[\da-zA-Z_-]+$/;
    if (
      value.length <= ALIAS_MAX_LENGTH &&
      validityPattern.test(value)
    ) {
      this.setState({inputValue: value});
    }
  };

  onExpandInput = () => {
    const {onChange, disabled} = this.props;
    if (!disabled && onChange) {
      this.setState({expanded: true});
    }
  };

  onHideInput = () => {
    this.setState({expanded: false});
  };

  onKeyDown = (event = {}) => {
    event.stopPropagation();
    if (event.key === 'Enter') {
      event.preventDefault();
      this.changeAlias();
    }
    if (event.key === 'Escape') {
      this.onHideInput();
    }
  };

  changeAlias = () => {
    const {onChange} = this.props;
    const {inputValue} = this.state;
    onChange && onChange(inputValue);
    this.onHideInput();
  };

  renderRunNamePlaceholder = () => {
    const {
      name,
      version,
      idPrefix,
      namePlaceholderStyle,
      versionDelimiter
    } = this.props;
    const {expanded} = this.state;
    if (expanded) {
      return null;
    }
    return (
      <div
        className={styles.namePlaceholder}
        style={namePlaceholderStyle}
      >
        (
        <span
          id={idPrefix ? `${idPrefix}-pipeline-name` : null}
        >
          {name}
        </span>
        {version ? (
          <span
            id={idPrefix ? `${idPrefix}-pipeline-version` : null}
          >
            {`${versionDelimiter || ':'}${version}`}
          </span>
        ) : null}
        )
      </div>
    );
  };

  renderVersion = () => {
    const {
      version,
      textBeforeVersion,
      textAfterVersion,
      versionDelimiter
    } = this.props;
    if (!version) {
      return null;
    }
    return (
      <span
        style={{marginLeft: versionDelimiter ? '0px' : '5px'}}
      >
        {textBeforeVersion ? (
          <span>
            {textBeforeVersion}
          </span>
        ) : null}
        {versionDelimiter || ''}
        {version}
        {textAfterVersion ? (
          <span>
            {textAfterVersion}
          </span>
        ) : null}
      </span>
    );
  };

  render () {
    const {
      name,
      alias,
      idPrefix,
      textBeforeContent,
      textAfterContent,
      containerStyle,
      disabled
    } = this.props;
    const {expanded, inputValue} = this.state;
    return (
      <div
        className={styles.container}
        style={containerStyle}
        ref={this.initializeContainer}
      >
        {textBeforeContent ? (
          <span>
            {textBeforeContent}
          </span>
        ) : null}
        {expanded ? (
          <Input
            onChange={this.onInputChange}
            style={{width: '200px', marginLeft: '5px'}}
            onBlur={this.changeAlias}
            ref={this.initializeInput}
            value={inputValue}
            disabled={disabled}
          />
        ) : (
          <span
            onClick={this.onExpandInput}
            id={!alias && idPrefix ? `${idPrefix}-pipeline-name` : null}
            className={styles.clickableName}
            style={{textDecoration: this.isEditable ? 'underline' : 'initial'}}
          >
            {alias || name}
          </span>
        )}
        {alias
          ? this.renderRunNamePlaceholder()
          : this.renderVersion()
        }
        {textAfterContent ? (
          <span>
            {textAfterContent}
          </span>
        ) : null}
      </div>
    );
  }
}

RunNameAlias.propTypes = {
  name: PropTypes.oneOfType([
    PropTypes.string,
    PropTypes.number
  ]),
  onChange: PropTypes.func,
  alias: PropTypes.string,
  version: PropTypes.string,
  idPrefix: PropTypes.string,
  textBeforeContent: PropTypes.string,
  textAfterContent: PropTypes.string,
  textBeforeVersion: PropTypes.string,
  textAfterVersion: PropTypes.string,
  versionDelimiter: PropTypes.string,
  containerStyle: PropTypes.object,
  namePlaceholderStyle: PropTypes.object,
  disabled: PropTypes.bool
};

export default RunNameAlias;
