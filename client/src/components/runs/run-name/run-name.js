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
import {Input} from 'antd';
import styles from './run-name.css';

const ALIAS_MAX_LENGTH = 25;

function getAliasFromProps (props) {
  const {run, alias} = props;
  if (alias !== undefined) {
    return alias;
  }
  const {
    tags = {}
  } = run || {};
  const {
    alias: aliasTag
  } = tags;
  return aliasTag;
}

class RunName extends React.PureComponent {
  state = {
    alias: undefined,
    editMode: false
  };

  componentDidMount () {
    this.updateFromProps();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    const prevAlias = getAliasFromProps(prevProps);
    const nextAlias = getAliasFromProps(this.props);
    if (prevAlias !== nextAlias) {
      this.updateFromProps();
    }
  }

  updateFromProps () {
    const alias = getAliasFromProps(this.props);
    this.setState({
      alias
    });
  }

  onChangeAlias = (event) => {
    const newAlias = (event.target.value || '');
    if (
      newAlias.length <= ALIAS_MAX_LENGTH &&
      /^[\da-zA-Z_-]*$/.test(newAlias)
    ) {
      this.setState({alias: newAlias});
    }
  };

  onEditMode = (event) => {
    const {disabled} = this.props;
    if (disabled) {
      return;
    }
    event.stopPropagation();
    event.preventDefault();
    this.setState({
      editMode: true
    });
  };

  commitChanges = () => {
    const {alias} = this.state;
    const {onChange} = this.props;
    this.setState({
      editMode: false,
      alias: alias && alias.length > 0 ? alias : undefined
    }, () => {
      const prevAlias = getAliasFromProps(this.props);
      if (onChange && (prevAlias || '') !== (alias || '')) {
        onChange(alias);
      }
    });
  }

  onFocus = (event) => {
    const input = event.target;
    if (input) {
      const {alias = ''} = this.state;
      const aliasLength = alias.length;
      input.setSelectionRange(aliasLength, aliasLength);
    }
  };

  render () {
    const {
      children,
      className,
      style,
      editable,
      ignoreOffset,
      disabled
    } = this.props;
    const {
      alias,
      editMode
    } = this.state;
    const editableProps = editable ? {onClick: this.onEditMode.bind(this)} : {};
    const aliasProps = (alias && !editMode) ? editableProps : {};
    const originalProps = (!alias && !editMode) ? editableProps : {};
    return (
      <span
        className={
          classNames(
            className,
            styles.runName,
            {
              [styles.editable]: editable,
              [styles.withAlias]: !!alias || editMode,
              [styles.ignoreOffset]: ignoreOffset
            }
          )
        }
        style={style}
      >
        {
          alias && !editMode && (
            <span
              className={
                classNames(
                  styles.alias,
                  'cp-run-name',
                  {
                    editable: editable && !editMode
                  }
                )
              }
              {...aliasProps}
            >
              {alias}
            </span>
          )
        }
        {
          editMode && (
            <Input
              autoFocus
              disabled={disabled}
              value={alias}
              onChange={this.onChangeAlias}
              onFocus={this.onFocus}
              onBlur={this.commitChanges}
              onPressEnter={this.commitChanges}
              className={styles.aliasInput}
            />
          )
        }
        <span
          className={
            classNames(
              styles.original,
              {
                'cp-run-name': !alias,
                editable: !alias && editable && !editMode
              }
            )
          }
          {...originalProps}
        >
          {children}
        </span>
      </span>
    );
  }
}

RunName.propTypes = {
  alias: PropTypes.string,
  run: PropTypes.object,
  children: PropTypes.node,
  className: PropTypes.string,
  style: PropTypes.object,
  editable: PropTypes.bool,
  disabled: PropTypes.bool,
  ignoreOffset: PropTypes.bool,
  onChange: PropTypes.func
};

export default RunName;
