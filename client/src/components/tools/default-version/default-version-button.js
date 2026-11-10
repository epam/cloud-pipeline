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
import {Button, Icon, Tooltip} from 'antd';
import {confirmDefaultVersion, updateDefaultVersion} from './update-default-version';

const SET_DEFAULT = 'Set as default';
const UNSET_DEFAULT = 'Unset default';

/**
 * A button, which sets a tool's version as the default one, or unsets it, if it is that version,
 * once a user confirms it. It is shown for every version: the version, which is used,
 * while a default version is not set (the latest or the only one), is not the default one.
 */
class DefaultVersionButton extends React.Component {
  state = {
    pending: false
  };

  get isDefault () {
    const {tool, version} = this.props;
    return !!tool && !!version && tool.defaultVersion === version;
  }

  onClick = async (event) => {
    if (event) {
      event.preventDefault();
      event.stopPropagation();
    }
    const {tool, version, onChange} = this.props;
    const unset = this.isDefault;
    if (!(await confirmDefaultVersion(version, unset))) {
      return;
    }
    this.setState({pending: true});
    const request = await updateDefaultVersion(tool.id, unset ? undefined : version);
    this.setState({pending: false});
    if (!request.error && onChange) {
      await onChange(request.value);
    }
  };

  render () {
    const {
      disabled,
      tool,
      version
    } = this.props;
    const {pending} = this.state;
    if (!tool || !version) {
      return null;
    }
    const isDefault = this.isDefault;
    const title = isDefault ? UNSET_DEFAULT : SET_DEFAULT;
    return (
      <Tooltip title={title}>
        <Button
          aria-label={title}
          size="small"
          disabled={disabled}
          loading={pending}
          onClick={this.onClick}
          style={{lineHeight: 1}}
        >
          {!pending && <Icon type={isDefault ? 'check-circle' : 'check-circle-o'} />}
        </Button>
      </Tooltip>
    );
  }
}

DefaultVersionButton.propTypes = {
  disabled: PropTypes.bool,
  tool: PropTypes.object,
  version: PropTypes.string,
  onChange: PropTypes.func
};

export default DefaultVersionButton;
