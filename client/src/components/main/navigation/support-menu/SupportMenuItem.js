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
import {Button, Icon, Popover, Tooltip} from 'antd';
import Markdown from '../../../special/markdown';
import {HcsImageAnalysisJobsModal} from '../../../special/hcs-image/hcs-image-analysis-jobs';
import styles from './SupportMenu.css';

const actions = {
  hcs: 'hcs'
};

const allActions = new Set(Object.values(actions));

const Hint = ({children, hint}) => {
  if (!hint) {
    return children;
  }
  return (
    <Tooltip
      trigger={['hover']}
      title={hint}
      placement="right"
    >
      {children}
    </Tooltip>
  );
};

function replaceLineBreaks (text) {
  if (!text) {
    return text;
  }
  return text
    .replace(/\\n/g, '\n')
    .replace(/\\t/g, '\t');
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

@inject('uiNavigation')
@observer
class SupportMenuItem extends React.Component {
  static propTypes = {
    className: PropTypes.string,
    onVisibilityChanged: PropTypes.func,
    visible: PropTypes.bool,
    style: PropTypes.object,
    content: PropTypes.string,
    icon: PropTypes.string,
    url: PropTypes.string,
    action: PropTypes.string,
    target: PropTypes.string,
    hint: PropTypes.string,
    entryName: PropTypes.string
  };

  state = {
    hcsJobsModalVisible: false
  };

  openHCSJobs = () => {
    this.setState({
      hcsJobsModalVisible: true
    });
  };

  closeHCSJobs = () => {
    this.setState({
      hcsJobsModalVisible: false
    });
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

  openUrl = () => {
    const {
      url,
      target = '_blank'
    } = this.props;
    if (url) {
      window.open(url, target);
    }
  };

  doAction = () => {
    const {
      action
    } = this.props;
    switch (action) {
      case actions.hcs:
        this.openHCSJobs();
        break;
    }
  };

  render () {
    const {
      className,
      onVisibilityChanged,
      visible,
      style,
      content,
      url,
      action,
      hint,
      entryName
    } = this.props;
    const id = `navigation-button-support-${(entryName || 'default').replace(/[\s.,;]/g, '-')}`;
    if (url) {
      return (
        <Hint
          hint={hint}
        >
          <Button
            id={id}
            className={className}
            style={style}
            onClick={this.openUrl}
          >
            {this.renderIcon()}
          </Button>
        </Hint>
      );
    }
    if (action && !allActions.has(action)) {
      return null;
    }
    if (action) {
      return (
        <Hint
          hint={hint}
        >
          <Button
            id={id}
            className={className}
            style={style}
            onClick={this.doAction}
          >
            {this.renderIcon()}
            <HcsImageAnalysisJobsModal
              visible={this.state.hcsJobsModalVisible}
              onClose={this.closeHCSJobs}
            />
          </Button>
        </Hint>
      );
    }
    if (!content) {
      return null;
    }
    const source = replaceLineBreaks(content);
    if (!source) {
      return null;
    }
    return (
      <Popover
        content={
          <Markdown
            md={source}
            target="_blank"
            useCloudPipelineLinks
          />
        }
        placement="rightBottom"
        trigger="click"
        onVisibleChange={onVisibilityChanged}
        visible={visible}
      >
        <Button
          id={id}
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
