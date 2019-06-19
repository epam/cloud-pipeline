/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import React from 'react';
import {observer} from 'mobx-react';
import {Button, Checkbox, LocaleProvider, Modal, Row} from 'antd';
import enUS from 'antd/lib/locale-provider/en_US';
import {
  Browsers,
  getBrowser,
  isCompatible,
  browserDisclaimerClosedPermanently,
  closeBrowserDisclaimerPermanently
} from '../../utils/browserCompatibility';

@observer
export class App extends React.Component {

  state = {
    browserModalVisible: false,
    closePermanently: false
  };

  getBrowsers = () => {
    const result = [];
    for (let browserKey in Browsers) {
      if (Browsers.hasOwnProperty(browserKey)) {
        result.push(Browsers[browserKey]);
      }
    }
    return result;
  };

  static browserWarningClosed = false;

  onClose = () => {
    App.browserWarningClosed = true;
    if (this.state.closePermanently) {
      closeBrowserDisclaimerPermanently();
    }
    this.setState({
      browserModalVisible: false
    });
  };

  onChangeClosePermanently = (e) => {
    this.setState({
      closePermanently: e.target.checked
    });
  };

  render () {
    const info = getBrowser();
    return (
      <LocaleProvider locale={enUS}>
        <div id="root-container" style={{height: '100%'}}>
          {this.props.children}
          <Modal
            onCancel={this.onClose}
            closable={false}
            footer={
              <Row type="flex" justify="end">
                <Button type="primary" onClick={this.onClose}>
                  OK
                </Button>
              </Row>
            }
            visible={
              App.browserWarningClosed
                ? false
                : !browserDisclaimerClosedPermanently() && this.state.browserModalVisible
            }>
            <Row style={{marginTop: 5}}>
              <Row>
                Current browser version (<b>{info.name}</b> version <b>{info.version}</b>) is not officially supported.
              </Row>
              <Row>
                Despite this, application can still work fine, but we strongly recommend to use the following browsers:
              </Row>
            </Row>
            <Row style={{marginTop: 5}}>
              <ul style={{
                listStylePosition: 'inside',
                listStyleType: 'disc'
              }}>
                {
                  this.getBrowsers().map(b => {
                    return (
                      <li>
                        <b>{b.name} >= {b.version}</b>
                      </li>
                    );
                  })
                }
              </ul>
            </Row>
            <Row style={{marginTop: 5}}>
              <Checkbox
                checked={this.state.closePermanently}
                onChange={this.onChangeClosePermanently}>
                Do not show again
              </Checkbox>
            </Row>
          </Modal>
        </div>
      </LocaleProvider>
    );
  }

  componentDidMount () {
    if (!isCompatible()) {
      this.setState({
        browserModalVisible: true
      });
    }
  }
}

export function NoStorage () {
  return (
    <div>
      You must specify storage id in URL.
    </div>
  );
}
