/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import {
  Alert,
  Button,
  Icon,
  Table
} from 'antd';
import CloudCredentialsProfileForm from './cloud-credentials-profile-form';
import LoadingView from '../../special/LoadingView';
import styles from './cloud-credentials-form.css';

class CloudCredentialsForm extends React.Component {
  state = {
    currentCredentials: undefined,
    createNew: false
  };

  get readOnly () {
    const {provider} = this.props;
    return !/^aws$/i.test(provider);
  }

  @computed
  get allCredentialProfiles () {
    if (this.props.cloudCredentialProfiles.loaded) {
      return (this.props.cloudCredentialProfiles.value || []).map(c => c);
    }
    return [];
  }

  get credentialProfiles () {
    const {provider} = this.props;
    if (provider) {
      return this.allCredentialProfiles.filter(c => c.cloudProvider === provider);
    }
    return this.allCredentialProfiles;
  }

  get columns () {
    return [
      {
        dataIndex: 'profileName',
        title: 'Name'
      },
      {
        dataIndex: 'assumedRole',
        title: 'Role'
      },
      {
        className: styles.actionsCell,
        render: (credentials) => {
          if (this.readOnly) {
            return null;
          }
          return (
            <div className={styles.actions}>
              <Button
                size="small"
                onClick={e => e.stopPropagation() && this.onEditCredentialsClicked(credentials)}
              >
                <Icon type="edit" />
              </Button>
            </div>
          );
        }
      }
    ];
  }

  onEditCredentialsClicked = (credentials) => {
    if (this.readOnly) {
      return;
    }
    if (credentials) {
      this.setState({
        currentCredentials: credentials.id,
        createNew: false
      });
    } else {
      this.setState({
        currentCredentials: undefined,
        createNew: false
      });
    }
  };

  onCreateNewClicked = () => {
    this.setState({
      currentCredentials: undefined,
      createNew: true
    });
  };

  onCloseCredentialsModal = () => {
    this.setState({
      currentCredentials: undefined,
      createNew: false
    });
  };

  render () {
    const {className, provider} = this.props;
    let content;
    if (this.props.cloudCredentialProfiles.pending && !this.props.cloudCredentialProfiles.loaded) {
      content = (<LoadingView />);
    } else if (this.props.cloudCredentialProfiles.error) {
      content = (<Alert type="error" message={this.props.cloudCredentialProfiles.error} />);
    } else {
      const {
        currentCredentials,
        createNew
      } = this.state;
      content = (
        <div>
          {
            !this.readOnly && (
              <div className={styles.header} style={{justifyContent: 'flex-end'}}>
                <Button
                  size="small"
                  onClick={this.onCreateNewClicked}
                >
                  <Icon type="plus" />
                  <span>Create profile</span>
                </Button>
              </div>
            )
          }
          <Table
            columns={this.columns}
            dataSource={this.credentialProfiles}
            size="small"
            rowKey="id"
            rowClassName={() => styles.credentialsRow}
            onRowClick={o => this.onEditCredentialsClicked(o)}
          />
          <CloudCredentialsProfileForm
            visible={createNew || !!currentCredentials}
            credentialsIdentifier={currentCredentials}
            isNew={createNew}
            provider={provider}
            onClose={this.onCloseCredentialsModal}
          />
        </div>
      );
    }
    return (
      <div
        className={className}
      >
        {content}
      </div>
    );
  }
}

CloudCredentialsForm.propTypes = {
  className: PropTypes.string,
  provider: PropTypes.string
};

export default inject('cloudCredentialProfiles')(observer(CloudCredentialsForm));
