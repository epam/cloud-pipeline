/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import {Alert, Button, Icon, Input, Popover, Row} from 'antd';
import ServerlessAPIURL from '../../../models/configuration/ServerlessAPIURL';

const CLOSE_POPOVER_DELAY_MS = 200;

@observer
class ServerlessAPIButton extends React.Component {
  static propTypes = {
    url: PropTypes.string,
    overlayClassName: PropTypes.string,
    style: PropTypes.object
  };

  state = {
    visible: false,
    url: null,
    error: null
  };

  componentDidMount () {
    this.updateServerlessAPIUrl();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      prevProps.configurationId !== this.props.configurationId ||
      prevProps.configurationName !== this.props.configurationName
    ) {
      this.updateServerlessAPIUrl();
    }
  }

  updateServerlessAPIUrl = () => {
    const {configurationId, configurationName} = this.props;
    const request = new ServerlessAPIURL(configurationId, configurationName);
    request.fetch()
      .then(() => {
        if (request.loaded) {
          this.setState({
            url: request.value,
            error: null
          });
        } else {
          this.setState({
            url: null,
            error: request.error
          });
        }
      })
      .catch(e => this.setState({url: undefined, error: e.message}));
  };

  getServerlessAPIPopoverContent = () => {
    const {url, error} = this.state;
    if (error) {
      return (
        <Row className={this.props.overlayClassName}>
          <Alert type="error" message={error} />
        </Row>
      );
    }
    if (!url) {
      return (
        <Row className={this.props.overlayClassName}>
          <Icon type="loading" />
        </Row>
      );
    }
    return (
      <Row className={this.props.overlayClassName}>
        <Input
          readOnly
          value={url} />
      </Row>
    );
  };

  onDropdownVisibilityChanged = (visibility) => {
    if (!visibility && this.closePopoverTimeout) {
      clearTimeout(this.closePopoverTimeout);
    }
    if (visibility) {
      this.setState({
        visible: visibility
      }, () => {
        this.updateServerlessAPIUrl();
      });
    } else {
      this.closePopoverTimeout = setTimeout(() => {
        if (this.state.preventPopoverFromClosing) {
          this.setState({
            preventPopoverFromClosing: false
          });
        } else {
          this.setState({
            preventPopoverFromClosing: false,
            visible: visibility
          });
        }
      }, CLOSE_POPOVER_DELAY_MS);
    }
  };
  render () {
    return (
      <Popover
        overlayClassName="serverless-api-popover"
        title="Serverless API"
        content={this.getServerlessAPIPopoverContent()}
        visible={this.state.visible}
        onVisibleChange={this.onDropdownVisibilityChanged}
        trigger={['click']}
        placement="bottomLeft"
      >
        <Button
          id="serverless-api-button"
          size="small"
          style={Object.assign({lineHeight: 1}, this.props.style || {})}>
          SERVERLESS API
        </Button>
      </Popover>
    );
  }
}

export default ServerlessAPIButton;
