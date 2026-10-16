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
import {Button, Icon, message, Tooltip} from 'antd';
import ToolDefaultVersionUpdate from '../../../models/tools/ToolDefaultVersionUpdate';
import ToolDefaultVersionDelete from '../../../models/tools/ToolDefaultVersionDelete';
import {
  getToolDefaultVersion,
  isCustomDefaultVersion
} from '../../../utils/tool-default-version';

const SET_DEFAULT = 'Set as default';
const REMOVE_DEFAULT = 'Remove default';

/**
 * A button, which sets a tool's version as the default one, or removes the default version,
 * if it is that version. It is not shown for a version, which is the default one implicitly
 * (the latest or the only one, while a default version is not set). If the default version
 * does not exist anymore, the button removes it from the version, which is used instead.
 */
class DefaultVersionButton extends React.Component {
  state = {
    pending: false
  };

  get isCustomDefault () {
    const {tool, version, versions} = this.props;
    return isCustomDefaultVersion(tool, versions) && tool.defaultVersion === version;
  }

  get isImplicitDefault () {
    const {tool, version, versions} = this.props;
    return !this.isCustomDefault && version === getToolDefaultVersion(tool, versions);
  }

  get missingDefaultVersion () {
    const {tool, versions} = this.props;
    const {defaultVersion} = tool || {};
    return defaultVersion && !(versions || []).includes(defaultVersion)
      ? defaultVersion
      : undefined;
  }

  get removesDefault () {
    return this.isCustomDefault || (this.isImplicitDefault && !!this.missingDefaultVersion);
  }

  onClick = async (event) => {
    if (event) {
      event.preventDefault();
      event.stopPropagation();
    }
    const {tool, version, onChange} = this.props;
    const remove = this.removesDefault;
    const hide = message.loading(
      remove ? 'Removing the default version...' : `Setting version '${version}' as default...`,
      0
    );
    this.setState({pending: true});
    const request = remove
      ? new ToolDefaultVersionDelete(tool.id)
      : new ToolDefaultVersionUpdate(tool.id, version);
    await request.send({});
    hide();
    this.setState({pending: false});
    if (request.error) {
      message.error(request.error, 5);
    } else if (onChange) {
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
    const remove = this.removesDefault;
    if (this.isImplicitDefault && !remove) {
      return null;
    }
    const title = remove ? REMOVE_DEFAULT : SET_DEFAULT;
    const missingDefaultVersion = this.missingDefaultVersion;
    const tooltip = remove && missingDefaultVersion
      ? `${REMOVE_DEFAULT}: version ${missingDefaultVersion} does not exist`
      : title;
    return (
      <Tooltip title={tooltip}>
        <Button
          aria-label={title}
          size="small"
          disabled={disabled}
          loading={pending}
          onClick={this.onClick}
          style={{lineHeight: 1}}
        >
          {!pending && <Icon type={remove ? 'star' : 'star-o'} />}
        </Button>
      </Tooltip>
    );
  }
}

DefaultVersionButton.propTypes = {
  disabled: PropTypes.bool,
  tool: PropTypes.object,
  version: PropTypes.string,
  versions: PropTypes.arrayOf(PropTypes.string),
  onChange: PropTypes.func
};

export default DefaultVersionButton;
