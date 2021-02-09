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
import {computed, observable} from 'mobx';
import classNames from 'classnames';
import {
  Alert,
  Button,
  Input,
  message,
  Modal,
  Select
} from 'antd';
import {
  CreateCloudCredentialsProfile,
  LoadCloudCredentialsProfiles,
  RemoveCloudCredentialsProfile,
  UpdateCloudCredentialsProfile
} from '../../../models/cloudCredentials';
import LoadingView from '../../special/LoadingView';
import CodeEditor from '../../special/CodeEditor';
import styles from './cloud-credentials-profile-form.css';

const APPEARANCE_TIMEOUT = 100;

function formatJson (value, formatted = true) {
  if (!value) {
    return value;
  }
  try {
    const o = JSON.parse(value);
    if (formatted) {
      return JSON.stringify(o, undefined, ' ');
    } else {
      return JSON.stringify(o);
    }
  } catch (_) {}
  return value;
}

class CloudCredentialsProfileForm extends React.Component {
  state = {
    assumedRole: undefined,
    cloudProvider: undefined,
    profileName: undefined,
    policy: undefined,
    initialAssumedRole: undefined,
    initialCloudProvider: undefined,
    initialProfileName: undefined,
    initialPolicy: undefined,
    pending: false
  };
  @observable profileRequest;

  @computed
  get providers () {
    const {cloudProviders} = this.props;
    if (cloudProviders.loaded) {
      return (cloudProviders.value || []).map(p => p);
    }
    return [];
  }

  get modified () {
    const {
      assumedRole,
      cloudProvider,
      profileName,
      policy,
      initialAssumedRole,
      initialCloudProvider,
      initialProfileName,
      initialPolicy
    } = this.state;
    return assumedRole !== initialAssumedRole ||
      cloudProvider !== initialCloudProvider ||
      profileName !== initialProfileName ||
      formatJson(policy, false) !== formatJson(initialPolicy, false);
  }

  @computed
  get otherProfiles () {
    const {
      isNew,
      cloudCredentialProfiles,
      credentialsIdentifier,
      provider
    } = this.props;
    if (cloudCredentialProfiles.loaded) {
      return (cloudCredentialProfiles.value || [])
        .filter(p => !provider || p.cloudProvider === provider)
        .filter(p => isNew || +(p.id) !== +credentialsIdentifier);
    }
    return [];
  }

  get profileNameError () {
    const {profileName} = this.state;
    if (!profileName || !profileName.length) {
      return 'Profile name is required';
    }
    if (this.otherProfiles.find(p => p.profileName === profileName)) {
      return 'Profile name must be unique';
    }
    return undefined;
  }

  get assumedRoleError () {
    const {assumedRole} = this.state;
    if (!assumedRole || !assumedRole.length) {
      return 'Role is required';
    }
    return undefined;
  }

  get providerError () {
    const {cloudProvider} = this.state;
    if (!cloudProvider || !cloudProvider.length) {
      return 'Provider is required';
    }
    return undefined;
  }

  get policyError () {
    const {policy} = this.state;
    try {
      if (policy && policy.length) {
        JSON.parse(policy);
        return undefined;
      } else {
        return undefined;
      }
    } catch (_) {
      return 'Wrong policy format';
    }
  }

  get valid () {
    return !this.profileNameError &&
      !this.assumedRoleError &&
      !this.providerError &&
      !this.policyError;
  }

  componentDidMount () {
    this.fetchCredentialsProfile();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.visible !== this.props.visible) {
      if (this.props.visible) {
        this.fetchCredentialsProfile();
      } else {
        this.editor = undefined;
      }
    }
  }

  fetchCredentialsProfile = () => {
    const {isNew, credentialsIdentifier, provider} = this.props;
    this.setState({
      assumedRole: undefined,
      cloudProvider: provider,
      profileName: undefined,
      policy: undefined,
      initialAssumedRole: undefined,
      initialCloudProvider: undefined,
      initialProfileName: undefined,
      initialPolicy: undefined,
      pending: false
    }, () => {
      if (this.editor) {
        this.editor.setValue('');
        this.editor.reset();
      }
    });
    if (!isNew && credentialsIdentifier) {
      this.profileRequest = new LoadCloudCredentialsProfiles({id: credentialsIdentifier});
      this.profileRequest
        .fetch()
        .then(() => {
          if (this.profileRequest.loaded) {
            this.setState({
              assumedRole: this.profileRequest.value.assumedRole,
              cloudProvider: this.profileRequest.value.cloudProvider,
              profileName: this.profileRequest.value.profileName,
              policy: this.profileRequest.value.policy,
              initialAssumedRole: this.profileRequest.value.assumedRole,
              initialCloudProvider: this.profileRequest.value.cloudProvider,
              initialProfileName: this.profileRequest.value.profileName,
              initialPolicy: formatJson(this.profileRequest.value.policy)
            }, () => {
              if (this.editor) {
                this.editor.setValue(this.state.initialPolicy);
                this.editor.reset();
              }
            });
          }
        })
        .catch(() => {});
    } else {
      this.profileRequest = undefined;
    }
  };

  renderContent = () => {
    const {
      isNew,
      provider
    } = this.props;
    if (!isNew && this.profileRequest && this.profileRequest.error) {
      return (
        <Alert type="error" message={this.profileRequest.error} />
      );
    }
    if (
      !isNew &&
      (
        !this.profileRequest ||
        (this.profileRequest.pending && !this.profileRequest.loaded)
      )
    ) {
      return (<LoadingView />);
    }
    const {
      assumedRole,
      cloudProvider,
      profileName,
      initialPolicy,
      pending
    } = this.state;
    const loading = pending || (!isNew && this.profileRequest && this.profileRequest.pending);
    const onChangeName = e => this.setState({profileName: e.target.value});
    const onChangeAssumedRole = e => this.setState({assumedRole: e.target.value});
    const onChangeProvider = e => this.setState({cloudProvider: e});
    const onChangePolicy = e => this.setState({policy: e});
    const initializeEditor = (editor) => {
      if (editor && !this.editor) {
        this.editor = editor;
        setTimeout(() => editor.reset(), APPEARANCE_TIMEOUT);
      } else if (this.editor) {
        this.editor._updateEditor();
      }
    };
    return (
      <div>
        <div
          className={
            classNames(
              styles.row,
              {
                [styles.error]: !!this.profileNameError
              }
            )
          }
        >
          <span className={styles.label}>
            Name:
          </span>
          <Input
            className={styles.content}
            value={profileName}
            onChange={onChangeName}
            disabled={loading}
          />
        </div>
        {
          this.profileNameError && (
            <div className={styles.errorDescription}>
              {this.profileNameError}
            </div>
          )
        }
        <div
          className={
            classNames(
              styles.row,
              {
                [styles.error]: !!this.providerError
              }
            )
          }
        >
          <span className={styles.label}>
            Provider:
          </span>
          <Select
            showSearch
            className={styles.content}
            value={cloudProvider}
            onChange={onChangeProvider}
            disabled={loading || !!provider}
          >
            {
              this.providers.map(p => (
                <Select.Option key={p} value={p}>{p}</Select.Option>
              ))
            }
          </Select>
        </div>
        {
          this.providerError && (
            <div className={styles.errorDescription}>
              {this.providerError}
            </div>
          )
        }
        <div
          className={
            classNames(
              styles.row,
              {
                [styles.error]: !!this.assumedRoleError
              }
            )
          }
        >
          <span className={styles.label}>
            Assumed role:
          </span>
          <Input
            className={styles.content}
            value={assumedRole}
            onChange={onChangeAssumedRole}
            disabled={loading}
          />
        </div>
        {
          this.assumedRoleError && (
            <div className={styles.errorDescription}>
              {this.assumedRoleError}
            </div>
          )
        }
        {
          this.props.visible && (
            <div
              className={
                classNames(
                  styles.row,
                  styles.policy,
                  {[styles.error]: !!this.policyError}
                )
              }
            >
              <span className={styles.label}>
                Policy:
              </span>
              <div
                className={styles.content}
                style={{
                  display: 'block',
                  position: 'relative',
                  minWidth: 200
                }}
              >
                <CodeEditor
                  readOnly={loading}
                  ref={initializeEditor}
                  className={styles.codeEditor}
                  language="application/json"
                  onChange={onChangePolicy}
                  lineWrapping
                  defaultCode={initialPolicy}
                  scrollbarStyle={null}
                />
              </div>
            </div>
          )
        }
        {
          this.policyError && (
            <div className={styles.errorDescription}>
              {this.policyError}
            </div>
          )
        }
      </div>
    );
  };

  onRemoveClicked = () => {
    const {onClose, cloudCredentialProfiles, credentialsIdentifier} = this.props;
    const doRemove = () => {
      return new Promise((resolve) => {
        const hide = message.loading('Removing profile...', 0);
        const request = new RemoveCloudCredentialsProfile(credentialsIdentifier);
        request
          .send()
          .then(() => {
            if (request.error) {
              hide();
              message.error(request.error, 5);
              resolve();
            } else {
              cloudCredentialProfiles
                .fetch()
                .then(() => {})
                .catch(() => {})
                .then(() => {
                  hide();
                  onClose && onClose();
                  resolve();
                });
            }
          })
          .catch(e => {
            hide();
            message.error(e.message, 5);
            resolve();
          });
      });
    };
    const onRemove = () => {
      this.setState({
        pending: true
      }, () => {
        doRemove()
          .then(() => {
            this.setState({
              pending: false
            });
          });
      });
      return Promise.resolve();
    };
    Modal.confirm({
      title: 'Are you sure you want to remove profile?',
      style: {
        wordWrap: 'break-word'
      },
      onOk () {
        return onRemove();
      },
      cancelText: 'No',
      okText: 'Yes'
    });
  };

  onSaveClicked = () => {
    this.setState({
      pending: true
    }, () => {
      const {
        isNew,
        credentialsIdentifier,
        cloudCredentialProfiles,
        onClose
      } = this.props;
      const hide = message.loading(isNew ? 'Creating profile...' : 'Updating profile...', 0);
      const request = isNew
        ? new CreateCloudCredentialsProfile()
        : new UpdateCloudCredentialsProfile(credentialsIdentifier);
      const {
        assumedRole,
        profileName,
        cloudProvider,
        policy
      } = this.state;
      request.send({
        assumedRole,
        profileName,
        cloudProvider,
        policy,
        id: isNew ? undefined : credentialsIdentifier
      })
        .then(() => {
          if (request.error) {
            hide();
            message.error(request.error, 5);
            this.setState({pending: false});
          } else {
            cloudCredentialProfiles
              .fetch()
              .then(() => {})
              .catch(() => {})
              .then(() => {
                hide();
                this.setState({pending: false});
                onClose && onClose();
              });
          }
        })
        .catch((e) => {
          hide();
          message.error(e.message, 5);
          this.setState({pending: false});
        });
    });
  };

  render () {
    const {
      isNew,
      visible,
      onClose
    } = this.props;
    let title;
    if (isNew) {
      title = 'Create profile';
    } else if (this.profileRequest && this.profileRequest.loaded) {
      title = `Edit "${this.profileRequest.value.profileName}" profile`;
    } else {
      title = 'Edit profile';
    }
    const {pending} = this.state;
    const loading = pending || (!isNew && this.profileRequest && this.profileRequest.pending);
    return (
      <Modal
        maskClosable={!loading}
        closable={!loading}
        visible={visible}
        width="80%"
        title={title}
        onCancel={onClose}
        footer={(
          <div className={styles.footer}>
            <div>
              {
                !isNew && (
                  <Button
                    className={styles.button}
                    disabled={loading}
                    type="danger"
                    onClick={this.onRemoveClicked}
                  >
                    Remove
                  </Button>
                )
              }
            </div>
            <div>
              <Button
                className={styles.button}
                disabled={loading}
                onClick={onClose}
              >
                Cancel
              </Button>
              <Button
                className={styles.button}
                type="primary"
                disabled={loading || !this.modified || !this.valid}
                onClick={this.onSaveClicked}
              >
                {isNew ? 'Create' : 'Update'}
              </Button>
            </div>
          </div>
        )}
      >
        {this.renderContent()}
      </Modal>
    );
  }
}

CloudCredentialsProfileForm.propTypes = {
  credentialsIdentifier: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  isNew: PropTypes.bool,
  onClose: PropTypes.func,
  provider: PropTypes.string,
  visible: PropTypes.bool
};

export default inject('cloudCredentialProfiles', 'cloudProviders')(
  observer(CloudCredentialsProfileForm)
);
