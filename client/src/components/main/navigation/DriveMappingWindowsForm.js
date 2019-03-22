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
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import {Alert, Button, Row} from 'antd';

const DRIVE_MAPPING_URL_PREFERENCE = 'base.dav.auth.url';

@inject('preferences')
@observer
export default class DriveMappingWindowsForm extends React.Component {

  @computed
  get driveMappintAuthUrl () {
    return this.props.preferences.getPreferenceValue(DRIVE_MAPPING_URL_PREFERENCE);
  }

  get isIE () {
    const ua = window.navigator.userAgent;
    const msie = ua.indexOf('MSIE ');
    if (msie > 0) {
      // IE 10 or older => return version number
      return true;
    }
    const trident = ua.indexOf('Trident/');
    if (trident > 0) {
      // IE 11 => return version number
      return true;
    }
    const edge = ua.indexOf('Edge/');
    if (edge > 0) {
      // Edge (IE 12+) => return version number
      return true;
    }
    // other browser
    return false;
  }

  openWindow = () => {
    window.open(
      this.driveMappintAuthUrl,
      'Authenticate',
      'menubar=no,' +
      'toolbar=no,' +
      `left=${Math.round(screen.width / 4)},` +
      `top=${Math.round(screen.height / 4)},` +
      `width=${Math.round(screen.width / 2)},` +
      `height=${Math.round(screen.height / 2)}`
    );
  };

  render () {
    if (!this.isIE) {
      return (
        <Row>
          <Alert
            type="info"
            message={
              <div>
                <center><b>Web browser not supported</b></center>
                <center style={{marginTop: '10px'}}><b>Drive mapping</b> feature allows to mount a cloud data storage to your local workstation and manage files/folders as with any general hard drive</center>
                <center>Currently this is supported only for the <b>Windows</b> platform</center>
                <center style={{marginTop: '10px'}}>Please open this page using <b>Internet Explorer</b> web browser</center>
              </div>
            } />
        </Row>
      );
    }

    return (
      <Row>
        <Row>
          <Alert
            type="info"
            message={
              <div>
                <center><b>Authenticate to proceed</b></center>
                <center style={{marginTop: '10px'}}><b>Drive mapping</b> feature allows to mount a cloud data storage to your local workstation and manage files/folders as with any general hard drive</center>
                <center style={{marginTop: '10px'}}>Please click <b>Authenticate</b> button below to obtain a <b>Single Sign-On</b> token and be able to mount a cloud data storage</center>
              </div>
            } />
        </Row>
        <Row type="flex" justify="center" style={{marginTop: 10}}>
          <Button type="primary" onClick={this.openWindow}>
            Authenticate
          </Button>
        </Row>
      </Row>
    );
  }
}
