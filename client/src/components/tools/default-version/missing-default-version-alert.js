/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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
import {Alert, Button} from 'antd';
import {confirmDefaultVersion, updateDefaultVersion} from './update-default-version';

/**
 * A warning, that a tool's default version does not exist anymore (e.g. it was deleted from
 * the docker registry outside of the platform), with an action to unset it.
 * A tool image without a tag cannot be launched, until the default version is unset or replaced.
 */
class MissingDefaultVersionAlert extends React.Component {
  state = {
    pending: false
  };

  onUnset = async () => {
    const {tool, onChange} = this.props;
    if (!(await confirmDefaultVersion(tool.defaultVersion, true))) {
      return;
    }
    this.setState({pending: true});
    const request = await updateDefaultVersion(tool.id);
    this.setState({pending: false});
    if (!request.error && onChange) {
      await onChange(request.value);
    }
  };

  render () {
    const {
      disabled,
      style,
      tool,
      versions = []
    } = this.props;
    const {pending} = this.state;
    const {defaultVersion} = tool || {};
    if (!defaultVersion || versions.includes(defaultVersion)) {
      return null;
    }
    return (
      <Alert
        type="warning"
        showIcon
        style={style}
        message={(
          <span>
            The default version <b>{defaultVersion}</b> does not exist.
            {
              !disabled && (
                <Button
                  size="small"
                  loading={pending}
                  onClick={this.onUnset}
                  style={{marginLeft: 10, lineHeight: 1}}
                >
                  Unset
                </Button>
              )
            }
          </span>
        )}
      />
    );
  }
}

MissingDefaultVersionAlert.propTypes = {
  disabled: PropTypes.bool,
  style: PropTypes.object,
  tool: PropTypes.object,
  versions: PropTypes.arrayOf(PropTypes.string),
  onChange: PropTypes.func
};

export default MissingDefaultVersionAlert;
