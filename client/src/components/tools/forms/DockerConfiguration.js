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
import {SERVER, API_PATH} from '../../../config';
import {observer, inject} from 'mobx-react';
import {computed} from 'mobx';
import {Modal, Row, Button, Collapse} from 'antd';
import styles from '../Tools.css';
import roleModel from '../../../utils/roleModel';
import UserToken from '../../../models/user/UserToken';
import hljs from 'highlight.js';
import 'highlight.js/styles/github.css';

const SECONDS_IN_MONTH = 60 * 60 * 24 * 30;

@inject(({authenticatedUserInfo}) => {
  return ({
    authenticatedUserInfo,
    accessKey: new UserToken(SECONDS_IN_MONTH)
  });
})
@observer
export default class DockerConfiguration extends React.Component {
  static propTypes = {
    visible: PropTypes.bool,
    onClose: PropTypes.func,
    registry: PropTypes.object,
    group: PropTypes.object
  };

  state = {
    activeTroubleShootingKeys: []
  };

  getApi () {
    const generateAPIURL = () => {
      const el = document.createElement('div');
      el.innerHTML= '<a href="'+(SERVER + API_PATH)+'"></a>';
      return el.firstChild.href;
    };
    let api = generateAPIURL();
    if (api.endsWith('/')) {
      api = api.substring(0, api.length - 1);
    }
    return api;
  }

  @computed
  get userName () {
    if (!this.props.authenticatedUserInfo.loaded) {
      return null;
    }
    return this.props.authenticatedUserInfo.value.userName;
  };

  loginIntoCloudRegistrySection = () => {
    if (this.props.registry && this.props.accessKey.loaded) {
      const {token} = this.props.accessKey.value;
      const loginIntoRegistryCode =
        `docker login ${this.props.registry.externalUrl || this.props.registry.path} -u ${this.userName} -p '${token}'`;
      return [
        <Row key="header" type="flex" align="middle" justify="space-between">
          <h2>Login into cloud registry</h2>
        </Row>,
        <Row key="body" type="flex" className={styles.mdPreview}>
          <pre style={{width: '100%', fontSize: 'smaller'}}>
            <code dangerouslySetInnerHTML={{__html: hljs.highlight('bash', loginIntoRegistryCode).value}} />
          </pre>
        </Row>
      ];
    }
    return null;
  };

  renderPushLocalDockerImageSection = () => {
    if (this.props.registry && this.props.group && roleModel.writeAllowed(this.props.group)) {
      const path = `${this.props.registry.externalUrl || this.props.registry.path}/${this.props.group.name}/$\{MY_LOCAL_DOCKER_IMAGE}`;
      const lines = [
        'MY_LOCAL_DOCKER_IMAGE=my_tool_name',
        `docker tag $MY_LOCAL_DOCKER_IMAGE ${path}`,
        `docker push ${path}`
      ];
      const code = lines.join('\r\n');
      return [
        <Row key="header" type="flex">
          <h2>Push a local docker image to the cloud registry</h2>
        </Row>,
        <Row key="body" type="flex" className={styles.mdPreview}>
          <pre style={{width: '100%', fontSize: 'smaller'}}>
            <code dangerouslySetInnerHTML={{__html: hljs.highlight('bash', code).value}} />
          </pre>
        </Row>
      ];
    }
    return null;
  };

  renderDockerLoginTroubleShooting = () => {
    if (this.props.registry && this.props.accessKey.loaded) {
      const {token} = this.props.accessKey.value;
      const code =
        `curl -k -s --header 'Authorization: Bearer ${token}' -o ca.crt ${this.getApi()}/dockerRegistry/${this.props.registry.id}/cert`;
      const placeIntoDockerDaemonDirectoryCode =
        `mkdir -p /etc/docker/certs.d/${this.props.registry.externalUrl || this.props.registry.path}\ncp ca.crt /etc/docker/certs.d/${this.props.registry.externalUrl || this.props.registry.path}/ca.crt`;
      return [
        <Row key="header 1" style={{marginTop: 0}}>
          <h3>Ask administrator to download registry certificate from URL</h3>
        </Row>,
        <Row key="body" type="flex" className={styles.mdPreview}>
          <pre style={{width: '100%', fontSize: 'smaller'}}>
            <code dangerouslySetInnerHTML={{__html: hljs.highlight('bash', code).value}} />
          </pre>
        </Row>,
        <Row key="header 2" type="flex">
          <h3>And place it into docker daemon directory</h3>
        </Row>,
        <Row key="body docker daemon directory" type="flex" className={styles.mdPreview}>
          <pre style={{width: '100%', fontSize: 'smaller'}}>
            <code dangerouslySetInnerHTML={{__html: hljs.highlight('bash', placeIntoDockerDaemonDirectoryCode).value}} />
          </pre>
        </Row>
      ];
    }
    return null;
  };

  renderDockerPushPullTroubleShooting = () => {
    if (this.props.registry) {
      const path = `/etc/docker/certs.d/${this.props.registry.externalUrl || this.props.registry.path}`;
      const code =
        `# Assume that ${path} contains ca.crt\n\n` +
        `# For ubuntu\ncd ${path}\ncp ca.crt registry-ca.crt\ncat /etc/ssl/certs/ca-certificates.crt registry-ca.crt >> ca.crt\n\n` +
        `# For centos/rhel\ncd ${path}\ncp ca.crt registry-ca.crt\ncat /etc/pki/tls/certs/ca-bundle.crt registry-ca.crt >> ca.crt`;
      return [
        <Row key="header" style={{marginTop: 0}}>
          <h3>Configure docker engine trust to system-wide CAs</h3>
        </Row>,
        <Row key="body" type="flex" className={styles.mdPreview}>
          <pre style={{width: '100%', fontSize: 'smaller'}}>
            <code dangerouslySetInnerHTML={{__html: hljs.highlight('bash', code).value}} />
          </pre>
        </Row>
      ];
    }
    return null;
  };

  renderTroubleshootingSection = () => {
    const troubleShootingScenarios = [];
    if (this.props.registry) {
      troubleShootingScenarios.push(
        {
          header: 'docker login: \'x509: certificate signed by unknown authority',
          body: this.renderDockerLoginTroubleShooting()
        }
      );
      troubleShootingScenarios.push(
        {
          header: 'docker push/pull: \'x509: certificate signed by unknown authority',
          body: this.renderDockerPushPullTroubleShooting()
        }
      );
    }
    if (troubleShootingScenarios.length > 0) {
      return [
        <Row key="header" type="flex" align="middle" justify="space-between">
          <h2>Troubleshooting</h2>
        </Row>,
        <Row key="body" type="flex">
          <Collapse
            onChange={(tabs) => this.setState({activeTroubleShootingKeys: tabs})}
            activeKey={this.state.activeTroubleShootingKeys}
            style={{width: '100%'}}
            bordered={false}>
            {
              troubleShootingScenarios.map((scenario, index) => {
                return (
                  <Collapse.Panel
                    className={styles.troubleshootingPanel}
                    id={`scenario_${index}`}
                    key={`scenario_${index}`}
                    header={<span className={styles.troubleshootingPanelHeader}>{scenario.header}</span>}>
                    {scenario.body}
                  </Collapse.Panel>
                );
              })
            }
          </Collapse>
        </Row>
      ];
    }
    return null;
  };

  render () {
    return (
      <Modal
        width="50%"
        closable={false}
        onCancel={this.props.onClose}
        title={null}
        footer={
          <Row type="flex" justify="end">
            <Button onClick={this.props.onClose}>OK</Button>
          </Row>
        }
        visible={this.props.visible}>
        {this.loginIntoCloudRegistrySection()}
        {this.renderPushLocalDockerImageSection()}
        {this.renderTroubleshootingSection()}
      </Modal>
    );
  }

  componentDidUpdate (prevProps) {
    if (prevProps.visible !== this.props.visible) {
      this.setState({
        activeTroubleShootingKeys: []
      });
    }
  }
}
