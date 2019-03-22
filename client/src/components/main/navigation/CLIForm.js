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
import {API_PATH, SERVER} from '../../../config';
import {
  Alert,
  Button,
  DatePicker,
  Icon,
  message,
  Row,
  Select,
  Table
} from 'antd';
import styles from './SettingsForm.css';
import UserToken from '../../../models/user/UserToken';
import PipelineGitCredentials from '../../../models/pipelines/PipelineGitCredentials';
import Notifications from '../../../models/notifications/Notifications';
import moment from 'moment';
import hljs from 'highlight.js';
import 'highlight.js/styles/github.css';
import LoadingView from '../../special/LoadingView';
import DriveMappingWindowsForm from './DriveMappingWindowsForm';
import {getOS} from '../../../utils/OSDetection';
import roleModel from '../../../utils/roleModel';

const CLI_KEY = 'cli';
const GIT_CLI_KEY = 'git cli';
const DRIVE_KEY = 'drive';

const DRIVE_MAPPING_URL_PREFERENCE = 'base.dav.auth.url';
const DRIVE_MAPPING_KEY = 'ui.pipe.drive.mapping';

@inject('authenticatedUserInfo', 'dataStorages', 'preferences')
@inject(({authenticatedUserInfo, dataStorages, preferences}) => ({
  authenticatedUserInfo,
  preferences,
  dataStorages,
  notifications: new Notifications(),
  pipelineGitCredentials: new PipelineGitCredentials()
}))
@observer
export default class CLIForm extends React.Component {

  state = {
    cli: {
      validTill: moment().add(1, 'M'),
      accessKey: null
    },
    driveMapping: {
      accessKey: null
    },
    activeTab: CLI_KEY
  };

  @computed
  get driveMappintAuthUrl () {
    return this.props.preferences.getPreferenceValue(DRIVE_MAPPING_URL_PREFERENCE);
  }

  @computed
  get pipeDriveMapping () {
    return this.props.preferences.getPreferenceValue(DRIVE_MAPPING_KEY) || JSON.stringify({});
  }

  @computed
  get hasWritableNFSStorages () {
    if (this.props.dataStorages.loaded) {
      return (this.props.dataStorages.value || [])
        .filter(s => s.type.toLowerCase() === 'nfs' && roleModel.writeAllowed(s))
        .length > 0;
    }
    return false;
  }

  renderPipeCLIContent = () => {
    const getSettingsValue = (key) => {
      if (this.props.preferences.loaded &&
        this.props.preferences.getPreferenceValue(key)) {
        return this.props.preferences.getPreferenceValue(key);
      }
      return '';
    };
    const onValidTillChanged = (date) => {
      if (date < moment()) {
        message.info('\'Valid till\' date should not be in past');
        return;
      }
      const cli = this.state.cli;
      cli.validTill = date;
      cli.accessKey = null;
      this.setState({cli});
    };
    const generateAccessKey = async () => {
      const validTill = moment(this.state.cli.validTill.format('YYYY-MM-DD'))
        .add(1, 'days').subtract(1, 'seconds');
      const now = moment(moment().format('YYYY-MM-DD'));
      const request = new UserToken(validTill.diff(now, 'seconds'));
      const hide = message.loading('Generating...');
      await request.fetch();
      hide();
      if (request.error) {
        message.error(request.error);
      } else {
        const cli = this.state.cli;
        cli.accessKey = request.value.token;
        this.setState({cli});
      }
    };
    const generateCliConfigureCommand = () => {
      if (this.state.cli.accessKey) {
        const generateAPIAbsoluteUrl = () => {
          const el = document.createElement('div');
          el.innerHTML= '<a href="'+(SERVER + API_PATH)+'"></a>';
          return el.firstChild.href;
        };
        return `pipe configure --auth-token ${this.state.cli.accessKey} --api ${generateAPIAbsoluteUrl()}`;
      }
      return null;
    };

    const generatePipInstallUrl = () => {
      const el = document.createElement('div');
      let url = (SERVER || '') + '/PipelineCLI.tar.gz';
      if (SERVER && SERVER.endsWith('/')) {
        url = SERVER + 'PipelineCLI.tar.gz';
      }
      el.innerHTML = '<a href="' + url + '"></a>';
      const extractHostname = (url) => {
        let hostname;
        if (url.indexOf('://') > -1) {
          hostname = url.split('/')[2];
        } else {
          hostname = url.split('/')[0];
        }
        hostname = hostname.split(':')[0];
        hostname = hostname.split('?')[0];
        return hostname;
      };
      return `pip install --trusted-host ${extractHostname(el.firstChild.href)} ${el.firstChild.href}`;
    };

    let
      cliConfigureCommand,
      pipInstallCommand,
      operationSystems,
      operationSystem;

    if (this.props.preferences.pending && !this.props.preferences.loaded) {
      cliConfigureCommand = <Icon type="loading" />;
      pipInstallCommand = <Icon type="loading" />;
    } else {
      let pipInstallCommandTemplate = this.props.preferences.replacePlaceholders(
        getSettingsValue('ui.pipe.cli.install.template') ||
        generatePipInstallUrl() ||
        ''
      );
      try {
        const pipInstallCommandTemplateJson = JSON.parse(pipInstallCommandTemplate);
        operationSystems = [];
        for (let os in pipInstallCommandTemplateJson) {
          if (pipInstallCommandTemplateJson.hasOwnProperty(os)) {
            operationSystems.push(os);
          }
        }
        if (this.state.operationSystems &&
          operationSystems.indexOf(this.state.operationSystems) >= 0) {
          operationSystem = this.state.operationSystems;
        } else {
          const detectedSystem = getOS();
          const index = operationSystems.map(o => o.toLowerCase()).indexOf(detectedSystem.toLowerCase());
          if (index >= 0) {
            operationSystem = operationSystems[index];
          } else {
            operationSystem = operationSystems[0];
          }
        }
        if (operationSystem) {
          pipInstallCommandTemplate = pipInstallCommandTemplateJson[operationSystem];
        }
      } catch (__) {}
      pipInstallCommand = (
        <code
          id="pip-install-url-input"
          dangerouslySetInnerHTML={{
            __html: hljs.highlight('bash', pipInstallCommandTemplate).value
          }} />
      );

      let cliConfigureCommandTemplate = getSettingsValue('ui.pipe.cli.configure.template') ||
        generateCliConfigureCommand() || '';

      try {
        const cliConfigureCommandTemplateJson = JSON.parse(cliConfigureCommandTemplate);
        if (operationSystem) {
          cliConfigureCommandTemplate = cliConfigureCommandTemplateJson[operationSystem] || '';
        }
      } catch (__) {}

      cliConfigureCommandTemplate = this.props.preferences.replacePlaceholders(
        cliConfigureCommandTemplate
          .replace(new RegExp('{user.jwt.token}', 'g'), this.state.cli.accessKey || '')
      );
      cliConfigureCommand = (
        <code
          id="cli-configure-command-text-area"
          dangerouslySetInnerHTML={{
            __html: hljs.highlight('bash', cliConfigureCommandTemplate).value
          }} />
      );
    }

    return (
      <div>
        <Row>
          <Alert
            type="info"
            message={
              <Row type="flex" align="middle">
                <Icon
                  type="exclamation-circle-o"
                  style={{
                    fontSize: 'large',
                    marginRight: 10,
                    color: '#2796dd'
                  }} /> Python 2 or Python 3 environment is required to run CLI
              </Row>
            } />
        </Row>
        {
          operationSystems &&
          <Row type="flex" align="middle" style={{marginTop: 5}}>
            <b style={{marginRight: 10}}>Operation system: </b>
            <Select
              style={{width: 200}}
              onSelect={e => this.setState({operationSystems: e})}
              value={operationSystem}>
              {
                operationSystems.map(system => {
                  return (
                    <Select.Option key={system}>
                      {system}
                    </Select.Option>
                  );
                })
              }
            </Select>
          </Row>
        }
        <Row type="flex" className={styles.mdPreview}>
          <pre style={{width: '100%', fontSize: 'smaller'}}>
            {pipInstallCommand}
          </pre>
        </Row>
        <div style={{backgroundColor: '#ddd', height: 1, width: '100%'}} />
        <Row style={{fontSize: 'large', marginBottom: 10}}>Access keys:</Row>
        <Row>
          <b>Valid till: </b>
          <DatePicker
            className="valid-till-date-picker"
            allowClear={false}
            onChange={onValidTillChanged}
            value={this.state.cli.validTill} />
          <Button
            id="generate-access-key-button"
            onClick={generateAccessKey}
            style={{marginLeft: 10}}
            type="primary">Generate access key</Button>
        </Row>
        {
          this.state.cli.accessKey &&
          <Row style={{marginTop: 10}}>
            <b>Access key: </b>
            <Row
              type="flex"
              className={styles.mdPreview}>
              <pre style={{width: '100%', fontSize: 'smaller'}}>
                <code
                  id="access-key-text-area"
                  dangerouslySetInnerHTML={{__html: hljs.highlight('bash', this.state.cli.accessKey).value}} />
              </pre>
            </Row>
          </Row>
        }
        {
          this.state.cli.accessKey &&
          <Row style={{marginTop: 10}}>
            <b>CLI configure command: </b>
            <Row
              type="flex"
              className={styles.mdPreview}>
              <pre style={{width: '100%', fontSize: 'smaller'}}>
                {cliConfigureCommand}
              </pre>
            </Row>
          </Row>
        }
      </div>
    );
  };

  renderGitCLIContent = () => {
    if (this.props.pipelineGitCredentials.pending && this.props.pipelineGitCredentials.loaded) {
      return <LoadingView />;
    }
    if (this.props.pipelineGitCredentials.error) {
      return <Alert type="error" message={this.props.pipelineGitCredentials.error} />;
    }
    const {email, userName, url, token} = this.props.pipelineGitCredentials.value;
    const getSettingsValue = (key) => {
      if (this.props.preferences.loaded &&
        this.props.preferences.getPreferenceValue(key)) {
        return this.props.preferences.getPreferenceValue(key);
      }
      return '';
    };
    const defaultCode = '' +
      '# Configure your git client to use correct username and password\n' +
      'git config --global user.name "{userName}"\n' +
      'git config --global user.email "{email}"\n' +
      '\n' +
      '# Disable TLS check, as a Certification Authority may not be trusted by your workstation\n' +
      'git config --global http.sslVerify "false"\n' +
      '\n' +
      '# git access token for futher reuse\n' +
      'git config credential.helper store\n' +
      '\n' +
      '# \n' +
      'git clone {url}/<GROUP_NAME>/<REPO_NAME>.git';
    let code = getSettingsValue('ui.git.cli.configure.template') || defaultCode;
    code = code.replace(/\\n/g, '\n');
    code = code.replace(/\\r/g, '\r');
    code = code.replace(/\{userName\}/g, userName);
    code = code.replace(/\{email\}/g, email);
    code = code.replace(/\{url\}/g, url);
    code = code.replace(/\{token\}/g, token);
    return (
      <Row type="flex" className={styles.mdPreview}>
        <pre style={{width: '100%', fontSize: 'smaller'}}>
          <code
            id="git-cli-configure-command"
            dangerouslySetInnerHTML={{__html: hljs.highlight('bash', code).value}} />
        </pre>
      </Row>
    );
  };

  renderDrive = () => {
    if (!this.hasWritableNFSStorages) {
      return (
        <Row>
          <Alert
            type="info"
            message={
              <div>
                <center><b>No supported data storage available</b></center>
                <center style={{marginTop: '10px'}}><b>Drive mapping</b> feature allows to mount a cloud data storage to your local workstation and manage files/folders as with any general hard drive</center>
                <center>Currently this is supported only for the <b>NFS data storages</b>, other types are NOT supported</center>
                <center style={{marginTop: '10px'}}>Please make sure that you have <b>READ/WRITE</b> access to any <b>NFS data storage</b> or ask support team to assist with a creation of such storage</center>
              </div>
            } />
        </Row>
      );
    }

    let content;
    const driveMapping = JSON.parse(this.pipeDriveMapping);
    const operationSystems = ['Windows', ...Object.keys(driveMapping)];
    let defaultOS = 'Windows';
    if (operationSystems.map(o => o.toLowerCase()).indexOf(getOS().toLowerCase()) >= 0) {
      defaultOS = getOS();
    }
    const operationSystem = this.state.driveMappingOS || defaultOS;

    if (driveMapping[operationSystem]) {
      let code = this.props.preferences.replacePlaceholders(driveMapping[operationSystem]);
      if (code.indexOf('{user.jwt.token}') >= 0) {
        code = code.replace(new RegExp('{user.jwt.token}', 'g'), this.state.driveMapping.accessKey || '');
        if (!this.state.driveMapping.accessKey) {
          (async () => {
            const duration = this.props.preferences.getPreferenceValue('launch.jwt.token.expiration') || 60 * 60 * 24; // 1 day
            const request = new UserToken(duration);
            await request.fetch();
            if (!request.error) {
              const driveMapping = this.state.driveMapping;
              driveMapping.accessKey = request.value.token;
              this.setState({driveMapping});
            }
          })();
        } else {
          code = code.replace(new RegExp('{user.jwt.token}', 'g'), this.state.driveMapping.accessKey || '');
        }
      }
      if (code) {
        content = (
          <Row type="flex" className={styles.mdPreview}>
            <pre style={{width: '100%', fontSize: 'smaller'}}>
              <code
                id="cli-configure-command-text-area"
                dangerouslySetInnerHTML={{
                  __html: hljs.highlight(
                    'bash',
                    this.props.preferences.replacePlaceholders(code)
                  ).value
                }} />
            </pre>
          </Row>
        );
      } else {
        content = (
          <Row type="flex" className={styles.mdPreview}>
            <Icon type="loading" />
          </Row>
        );
      }
    } else {
      content = <DriveMappingWindowsForm />;
    }

    return [
      <Row type="flex" align="middle" style={{marginBottom: 10}} key="drive-mapping-os-select">
        <b style={{marginRight: 10}}>Operation system: </b>
        <Select
          style={{width: 200}}
          onSelect={e => this.setState({driveMappingOS: e})}
          value={operationSystem}>
          {
            operationSystems.map(system => {
              return (
                <Select.Option key={system}>
                  {system}
                </Select.Option>
              );
            })
          }
        </Select>
      </Row>,
      <Row key="drive-mapping-content">
        {content}
      </Row>
    ];
  };

  selectTab = ({key}) => {
    if (key !== this.state.activeTab) {
      this.setState({
        activeTab: key
      });
    }
  };

  columns = [
    {
      dataIndex: 'title'
    }
  ];

  getTabs = () => {
    const tabs = [];
    tabs.push({
      key: CLI_KEY,
      title: 'Pipe CLI'
    });
    tabs.push({
      key: GIT_CLI_KEY,
      title: 'Git CLI'
    });
    if (this.driveMappintAuthUrl) {
      tabs.push({
        key: DRIVE_KEY,
        title: 'Drive mapping'
      });
    }
    return tabs;
  };

  renderContent = () => {
    switch (this.state.activeTab) {
      case CLI_KEY: return this.renderPipeCLIContent();
      case GIT_CLI_KEY: return this.renderGitCLIContent();
      case DRIVE_KEY: return this.renderDrive();
    }
  };

  render () {
    return (
      <div style={{display: 'flex', flexDirection: 'row', height: '100%'}}>
        <div style={{width: 200, height: '100%', borderRight: '1px solid #eee'}}>
          <Table
            columns={this.columns}
            dataSource={this.getTabs()}
            showHeader={false}
            bordered={false}
            size="medium"
            rowClassName={row => row.key === this.state.activeTab ? styles.tabSelected : styles.tab}
            onRowClick={this.selectTab}
            pagination={false} />
        </div>
        <div style={{flex: 1, height: '100%', overflow: 'auto', paddingLeft: 10}}>
          {this.renderContent()}
        </div>
      </div>
    );
  }
}

