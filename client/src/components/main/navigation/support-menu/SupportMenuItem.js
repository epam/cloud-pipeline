/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import styles from './SupportMenu.css';

function replaceLineBreaks (text) {
  if (!text) {
    return text;
  }
  return text
    .replace(/\\n/g, '\n')
    .replace(/\\t/g, '\t');
}

function processLinks (html) {
  return (html || '').replace(/<a href/ig, '<a target="_blank" href');
}

function urlCheck (string) {
  let url;
  try {
    url = new URL(string);
  } catch (_) {
    return false;
  }
  return url.protocol === 'http:' || url.protocol === 'https:';
}

@inject('issuesRenderer')
@observer
class SupportMenuItem extends React.Component {
  static propTypes = {
    className: PropTypes.string,
    onVisibilityChanged: PropTypes.func,
    visible: PropTypes.bool,
    style: PropTypes.object,
    content: PropTypes.string,
    icon: PropTypes.string
  };

  renderIcon = () => {
    const {icon} = this.props;
    if (urlCheck(icon)) {
      return (
        <img
          src={icon}
          className={styles.externalIcon}
        />
      );
    }
    return (
      <Icon type={icon} />
    );
  };

  render () {
    const {
      className,
      onVisibilityChanged,
      issuesRenderer,
      visible,
      style,
      content
    } = this.props;
    if (!issuesRenderer) {
      return null;
    }
    const source = replaceLineBreaks(content);
    if (!source) {
      return null;
    }
    const html = processLinks(issuesRenderer.render(source));
    return (
      <Popover
        content={
          <div dangerouslySetInnerHTML={{__html: html}} />
        }
        placement="rightBottom"
        trigger="click"
        onVisibleChange={onVisibilityChanged}
        visible={visible}
      >
        <Button
          id="navigation-button-support"
          className={className}
          style={style}
        >
          {this.renderIcon()}
        </Button>
      </Popover>
    );
  }
}

export default SupportMenuItem;
