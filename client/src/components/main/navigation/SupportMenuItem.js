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
import {inject, observer} from 'mobx-react';
import {Button, Icon, Popover} from 'antd';

function replaceLineBreaks (text) {
  if (!text) {
    return text;
  }
  return text
    .replace(/\\n/g, '\n')
    .replace(/\\t/g, '\t');
}

@inject('preferences', 'issuesRenderer')
@observer
class SupportMenuItem extends React.Component {
  static propTypes = {
    className: PropTypes.string,
    onVisibilityChanged: PropTypes.func,
    visible: PropTypes.bool,
    style: PropTypes.object
  };

  render () {
    const {
      className,
      onVisibilityChanged,
      issuesRenderer,
      visible,
      preferences,
      style
    } = this.props;
    if (!preferences || !preferences.loaded || !issuesRenderer) {
      return null;
    }
    const source = replaceLineBreaks(preferences.getPreferenceValue('ui.support.template'));
    if (!source) {
      return null;
    }
    return (
      <Popover
        content={
          <div dangerouslySetInnerHTML={{__html: issuesRenderer.render(source)}} />
        }
        placement="rightBottom"
        trigger="click"
        onVisibleChange={onVisibilityChanged}
        visible={visible}>
        <Button
          id="navigation-button-support"
          className={className}
          style={style}
        >
          <Icon type="customer-service" />
        </Button>
      </Popover>
    );
  }
}

export default SupportMenuItem;
