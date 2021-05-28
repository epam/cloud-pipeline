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
import {Alert} from 'antd';
import {inject, observer} from 'mobx-react';
import TextFileConflict from './text-file-conflict';
import BinaryFileConflict from './binary-file-conflict';

class Conflict extends React.PureComponent {
  state = {
    error: undefined,
    conflictedFile: undefined
  };

  componentDidMount () {
    this.updateFromProps();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.file !== this.props.file) {
      this.updateFromProps();
    }
  }

  updateFromProps = () => {
    const {
      conflictsSession,
      file
    } = this.props;
    if (conflictsSession && file) {
      const conflictsSessionFile = conflictsSession.getFile(file);
      if (conflictsSessionFile) {
        conflictsSessionFile
          .getInfo()
          .then((conflictedFile) => {
            this.setState({
              conflictedFile,
              error: undefined
            });
          })
          .catch(e => {
            this.setState({
              conflictedFile: undefined,
              error: e.message
            });
          });
      }
    } else {
      this.setState({
        conflictedFile: undefined,
        error: undefined
      });
    }
  };

  render () {
    const {
      conflictedFile,
      error
    } = this.state;
    const {
      disabled,
      onInitialized
    } = this.props;
    if (error) {
      return (
        <Alert type="error" message={error} />
      );
    }
    if (!conflictedFile) {
      return null;
    }
    const {
      binary = false
    } = conflictedFile;
    if (!binary) {
      return (
        <TextFileConflict
          disabled={disabled}
          conflictedFile={conflictedFile}
          onInitialized={onInitialized}
        />
      );
    }
    return (
      <BinaryFileConflict
        disabled={disabled}
        conflictedFile={conflictedFile}
        onInitialized={onInitialized}
      />
    );
  }
}

Conflict.propTypes = {
  disabled: PropTypes.bool,
  file: PropTypes.string,
  onInitialized: PropTypes.func
};

export default inject('conflictsSession')(observer(Conflict));
