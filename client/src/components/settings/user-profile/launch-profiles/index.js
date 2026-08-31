/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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
import {observer} from 'mobx-react';
import {Button, Icon, message, Modal} from 'antd';
import classNames from 'classnames';
import {withCurrentUserAttributes} from '../../../../utils/current-user-attributes';
import MetadataUpdateKeys from '../../../../models/metadata/MetadataUpdateKeys';
import SectionsList from '../../sub-settings/sections-list';
import LaunchProfileForm from './launch-profile-form';
import styles from './launch-profile-form.css';

const LAUNCH_PROFILES_KEY = 'launch_profiles';
const NEW_PROFILE_ID = -1;

function parseProfiles (raw) {
  try {
    return JSON.parse(raw || '[]');
  } catch {
    return [];
  }
}

@withCurrentUserAttributes()
@observer
class LaunchProfilesSettings extends React.Component {
  state = {
    selectedId: null,
    profiles: [],
    pending: false,
    loading: true,
    formModified: false
  };

  _currentUserAttributesToken = {};

  componentDidMount () {
    (this.load)(true);
  }

  componentDidUpdate (prevProps, prevState) {
    if (this.state.selectedId === null) {
      this.autoSelectFirst();
    }
    if (prevState.formModified !== this.state.formModified) {
      const {onModified} = this.props;
      if (onModified) onModified(this.state.formModified);
    }
  }

  asyncSetState = async (state, callback) => new Promise((resolve) => {
    this.setState(state, () => {
      if (callback) {
        callback();
      }
      resolve();
    });
  })

  load = async (force = false) => {
    const {currentUserAttributes} = this.props;
    if (currentUserAttributes) {
      const token = this._currentUserAttributesToken = {};
      try {
        await this.asyncSetState({loading: true});
        await currentUserAttributes.refresh(force);
        if (token === this._currentUserAttributesToken) {
          const raw = currentUserAttributes.getAttributeValue(LAUNCH_PROFILES_KEY);
          const profiles = parseProfiles(raw);
          const {selectedId} = this.state;
          const profile = profiles.find(profile => profile.id === selectedId);
          this.setState({
            profiles,
            selectedId: profile ? profile.id : (profiles.length > 0 ? profiles[0].id : null)
          });
        }
      } catch {
        // noop
      } finally {
        if (token === this._currentUserAttributesToken) {
          await this.asyncSetState({loading: false});
        }
      }
    }
  };

  autoSelectFirst = () => {
    const {profiles = []} = this.state;
    if (profiles.length > 0) {
      this.setState({selectedId: profiles[0].id});
    }
  };

  componentWillUnmount () {
    const {onModified} = this.props;
    this._currentUserAttributesToken = {};
    if (onModified) onModified(false);
  }

  confirmLeave = () => {
    const {formModified} = this.state;
    return new Promise((resolve) => {
      if (formModified) {
        Modal.confirm({
          title: 'Changes will not be saved. Continue?',
          onOk () { resolve(true); },
          onCancel () { resolve(false); },
          okText: 'Yes',
          cancelText: 'No'
        });
      } else {
        resolve(true);
      }
    });
  };

  get userId () {
    const {currentUserAttributes} = this.props;
    const user = currentUserAttributes && currentUserAttributes.user;
    return user ? user.id : undefined;
  }

  get selectedProfile () {
    const {selectedId, profiles = []} = this.state;
    if (!selectedId || selectedId === NEW_PROFILE_ID) return null;
    return profiles.find(p => p.id === selectedId) || null;
  }

  saveProfiles = async (profiles) => {
    const userId = this.userId;
    if (!userId) {
      throw new Error('User info not loaded');
    }
    this.setState({profiles, formModified: false});
    const request = new MetadataUpdateKeys();
    await request.send({
      entity: {entityId: +userId, entityClass: 'PIPELINE_USER'},
      data: {
        [LAUNCH_PROFILES_KEY]: {
          value: JSON.stringify(profiles),
          type: 'string'
        }
      }
    });
    if (request.error) {
      throw new Error(request.error);
    }
    await this.load(true);
  };

  handleAdd = async () => {
    const confirmed = await this.confirmLeave();
    if (confirmed) {
      this.setState({selectedId: NEW_PROFILE_ID, formModified: false});
    }
  };

  handleSelect = async (id) => {
    const confirmed = await this.confirmLeave();
    if (confirmed) {
      this.setState({selectedId: id, formModified: false});
    }
  };

  handleSave = async (name, payload) => {
    this.setState({pending: true});
    const {profiles: originalProfiles = [], selectedId} = this.state;
    const profiles = originalProfiles.slice();
    let nextId = selectedId;
    try {
      if (selectedId === NEW_PROFILE_ID) {
        nextId = profiles.length > 0
          ? Math.max(...profiles.map(p => p.id)) + 1
          : 1;
        profiles.push({id: nextId, name, payload});
      } else {
        const idx = profiles.findIndex(p => p.id === selectedId);
        if (idx !== -1) {
          profiles[idx] = {...profiles[idx], name, payload};
        }
      }
      await this.saveProfiles(profiles);
      this.setState({selectedId: nextId, formModified: false});
    } catch (e) {
      message.error(e.message, 5);
    } finally {
      this.setState({pending: false});
    }
  };

  handleDelete = async () => {
    this.setState({pending: true});
    const {profiles: originalProfiles = [], selectedId} = this.state;
    try {
      if (selectedId === NEW_PROFILE_ID) {
        this.setState({selectedId: null, formModified: false, pending: false});
        return;
      }
      const profiles = originalProfiles.filter(p => p.id !== selectedId);
      await this.saveProfiles(profiles);
      const nextId = profiles.length > 0 ? profiles[0].id : null;
      this.setState({selectedId: nextId, formModified: false});
    } catch (e) {
      message.error(e.message, 5);
    } finally {
      this.setState({pending: false});
    }
  };

  render () {
    const {selectedId, pending, loading, profiles} = this.state;
    const sections = profiles.map(p => ({key: p.id, title: p.name, name: p.name}));
    const hasDetail = selectedId !== null;
    if (loading && profiles.length === 0) {
      return (
        <div className={styles.container} style={{height: '100%'}}>
          <div className={styles.noContentContainer}>
            <span className="cp-text-not-important">
              Loading launch profiles...
            </span>
          </div>
        </div>
      );
    }
    return (
      <div className={styles.container} style={{height: '100%'}}>
        <div className={classNames(styles.profileListContainer, 'cp-divider', 'right')}>
          <div className={styles.listProfiles}>
            <SectionsList
              activeSectionKey={selectedId}
              sections={sections}
              onSectionChange={this.handleSelect}
              disabled={pending || loading}
            />
          </div>
          <div className={styles.listHeader}>
            <Button
              size="small"
              type="primary"
              onClick={this.handleAdd}
              disabled={pending || loading}
              style={{marginLeft: 'auto'}}
            >
              <Icon type="plus" />
              {'Add profile'}
            </Button>
          </div>
        </div>
        <div className={styles.content}>
          {hasDetail ? (
            <LaunchProfileForm
              key={selectedId} // re-mount component
              profileId={selectedId}
              profile={this.selectedProfile}
              onSave={this.handleSave}
              onDelete={this.handleDelete}
              onModified={(modified) => this.setState({formModified: modified})}
              saving={pending || loading}
            />
          ) : (
            <div
              className={styles.noContentContainer}
            >
              <span className="cp-text-not-important" style={{padding: 16, display: 'block'}}>
                {'Select a profile or add a new one'}
              </span>
            </div>
          )}
        </div>
      </div>
    );
  }
}

export default LaunchProfilesSettings;
