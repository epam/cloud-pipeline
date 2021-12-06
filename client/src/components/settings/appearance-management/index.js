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
import {
  Alert,
  Button,
  Checkbox,
  Icon,
  Input,
  message,
  Modal,
  Radio
} from 'antd';
import classNames from 'classnames';
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import roleModel from '../../../utils/roleModel';
import LoadingView from '../../special/LoadingView';
import UIThemeEditForm from './ui-theme-edit-form';
import ThemeCard from './theme-card';
import themesAreEqual from './utilities/themes-are-equal';
import validateTheme from './utilities/theme-validation';
import {
  DefaultThemeIdentifier,
  generateIdentifier, ThemesPreferenceModes,
  ThemesPreferenceName
} from '../../../themes';
import styles from './appearance-management.css';

const INJECT_TESTING_THEME_DELAY_MS = 500;

@roleModel.authenticationInfo
@inject('themes', 'preferences')
@observer
class AppearanceManagement extends React.Component {
  state = {
    editableTheme: undefined,
    editableThemePreviewClassName: undefined,
    modified: false,
    themePayload: undefined,
    valid: true,
    pending: false,
    liveUpdate: false,
    mode: undefined
  };

  componentWillUnmount () {
    if (this.injectEditableThemeStylesDelayed) {
      clearTimeout(this.injectEditableThemeStylesDelayed);
    }
  }

  @computed
  get themes () {
    const {
      themes: themesStore
    } = this.props;
    return (themesStore.themes || [])
      .sort((a, b) => Number(b.predefined) - Number(a.predefined));
  }

  renderThemesSelector = () => {
    const themes = this.themes;
    const {
      pending
    } = this.state;
    const onSelectTheme = (identifier) => {
      const theme = themes.find(o => o.identifier === identifier);
      this.setState({
        editableTheme: theme,
        themePayload: {...theme},
        modified: false,
        valid: true,
        url: undefined,
        mode: undefined
      }, () => this.injectEditableThemeStyles(false));
    };
    return (
      <div
        className={classNames(styles.themes, styles.section)}
      >
        {
          themes.map((theme) => (
            <ThemeCard
              key={theme.identifier}
              identifier={theme.identifier}
              name={theme.name}
              tag={theme.predefined ? 'PREDEFINED' : undefined}
              readOnly={theme.predefined || pending}
              onSelect={() => onSelectTheme(theme.identifier)}
            />
          ))
        }
      </div>
    );
  };

  injectEditableThemeStyles = (delayed = true) => {
    const {
      themes: themesStore
    } = this.props;
    const {
      editableTheme,
      themePayload,
      liveUpdate
    } = this.state;
    if (themesStore && themePayload && editableTheme) {
      const {identifier} = editableTheme;
      if (this.injectEditableThemeStylesDelayed) {
        clearTimeout(this.injectEditableThemeStylesDelayed);
      }
      const inject = () => {
        themesStore
          .startTestingTheme({...themePayload, identifier}, liveUpdate)
          .then(previewClassName => this.setState({
            editableThemePreviewClassName: previewClassName
          }));
      };
      if (delayed) {
        this.injectEditableThemeStylesDelayed = setTimeout(
          () => inject(),
          INJECT_TESTING_THEME_DELAY_MS
        );
      } else {
        inject();
      }
    }
  };

  ejectEditableThemeStyles = () => {
    const {
      themes: themesStore
    } = this.props;
    if (themesStore) {
      themesStore.stopTestingTheme();
      this.setState({
        editableThemePreviewClassName: undefined
      });
    }
  };

  onChangeTheme = (payload, valid) => {
    const {
      editableTheme
    } = this.state;
    if (editableTheme) {
      this.setState({
        themePayload: payload,
        modified: !themesAreEqual(payload, editableTheme),
        valid
      }, () => this.injectEditableThemeStyles());
    }
  };

  onCreateTheme = () => {
    const newTheme = {
      isNew: true,
      extends: DefaultThemeIdentifier
    };
    this.setState({
      editableTheme: {
        ...newTheme,
        name: 'New theme'
      },
      themePayload: {
        ...newTheme
      },
      modified: true,
      valid: validateTheme(newTheme, this.themes),
      url: undefined,
      mode: undefined
    }, () => this.injectEditableThemeStyles(false));
  };

  onRemove = () => {
    const {
      editableTheme
    } = this.state;
    if (editableTheme && !editableTheme.isNew) {
      const {
        identifier,
        name
      } = editableTheme;
      const themesPayload = this.themes
        .filter(o => !o.predefined && o.identifier !== identifier);
      const hide = message.loading(
        (
          <span>
            Removing <b>{name}</b> theme...
          </span>
        ),
        5
      );
      this.save(themesPayload)
        .then(hide);
    }
  };

  saveCurrentThemes = (themes, mode, url) => {
    return new Promise((resolve, reject) => {
      const {themes: themesStore} = this.props;
      const themesPayload = (themes || [])
        .filter(o => !o.predefined)
        .map(o => ({
          identifier: o.identifier,
          name: o.name,
          extends: o.extends,
          dark: o.dark,
          configuration: {...(o.properties || {})}
        }));
      themesStore
        .saveThemes(
          themesPayload,
          {
            throwError: true,
            mode,
            url: mode === ThemesPreferenceModes.url ? url : undefined
          })
        .then(resolve)
        .catch(reject);
    });
  };

  onSaveTheme = async () => {
    const {
      editableTheme,
      themePayload
    } = this.state;
    if (editableTheme && themePayload) {
      const {
        isNew,
        identifier,
        predefined
      } = editableTheme;
      const createIdentifier = () => {
        let generated = generateIdentifier(themePayload.name);
        generated = (generated || '').concat(predefined ? '' : '-custom');
        const r = new RegExp(`^${generated}(|-[\\d]+)$`, 'i');
        const existing = this.themes.filter(o => r.test(o.identifier)).length;
        if (existing > 0) {
          generated = generated.concat(`-${existing + 1}`);
        }
        return generated;
      };
      const themeConfig = {
        identifier: identifier || createIdentifier(),
        ...themePayload
      };
      const themesPayload = this.themes
        .filter(o => !o.predefined)
        .map(o => !isNew && o.identifier === identifier ? themeConfig : o)
        .concat(isNew ? [themeConfig] : []);
      const hide = message.loading(
        (
          <span>
            {isNew ? 'Creating' : 'Saving'} <b>{themePayload.name}</b> theme...
          </span>
        ),
        5
      );
      this.save(themesPayload)
        .then(hide);
    }
  };

  save = async (themes = []) => {
    const {themes: themesStore} = this.props;
    return new Promise((resolve) => {
      this.setState({
        pending: true
      }, () => {
        themesStore
          .saveThemes(
            themes
              .filter(o => !o.predefined)
              .map(o => ({
                identifier: o.identifier,
                name: o.name,
                extends: o.extends,
                dark: o.dark,
                configuration: {...(o.properties || {})}
              })),
            {throwError: true}
          )
          .then(() => {
            if (themesStore.mode === ThemesPreferenceModes.url) {
              Modal.info({
                title: 'Themes configuration file was downloaded',
                content: (
                  <div>
                    You should copy configuration file to
                    the <code>{themesStore.themesURL}</code>.
                  </div>
                )
              });
            }
            this.setState({
              editableTheme: undefined,
              themePayload: undefined,
              modified: false,
              valid: true
            });
          })
          .catch(e => message.error(e.message, 5))
          .then(() => {
            this.ejectEditableThemeStyles();
            this.setState({pending: false});
            resolve();
          });
      });
    });
  };

  onCancelEdit = () => {
    this.setState({
      editableTheme: undefined,
      themePayload: undefined,
      modified: false,
      valid: true
    }, this.ejectEditableThemeStyles);
  };

  toggleLiveUpdate = (e) => {
    this.setState({
      liveUpdate: e.target.checked
    }, () => this.injectEditableThemeStyles(false));
  };

  renderActions = () => {
    const {
      editableTheme,
      modified,
      valid,
      liveUpdate,
      pending
    } = this.state;
    if (editableTheme) {
      return (
        <div className={styles.actions}>
          <Checkbox
            checked={liveUpdate}
            onChange={this.toggleLiveUpdate}
            disabled={pending}
          >
            Live preview
          </Checkbox>
          <Button
            key="cancel"
            id="cancel-edit-theme-button"
            className={styles.action}
            onClick={this.onCancelEdit}
            disabled={pending}
          >
            Cancel
          </Button>
          <Button
            key="primary"
            id="save-theme-button"
            disabled={!valid || !modified || pending}
            className={styles.action}
            onClick={this.onSaveTheme}
            type="primary"
          >
            {
              editableTheme.isNew ? 'Create' : 'Save'
            }
          </Button>
        </div>
      );
    }
    return (
      <div className={styles.actions}>
        <Button
          key="primary"
          id="create-theme-button"
          className={styles.action}
          onClick={this.onCreateTheme}
          type="primary"
          disabled={pending}
        >
          New theme
        </Button>
      </div>
    );
  };

  renderPreferenceDisclaimer = () => {
    const {
      themes: themesStore,
      preferences
    } = this.props;
    const {
      mode,
      url,
      pending
    } = this.state;
    const currentMode = mode || themesStore.mode;
    const cloudPipelineAppName = preferences.deploymentName || 'Cloud Pipeline';
    const onChangeMode = (newMode) => {
      if (newMode === ThemesPreferenceModes.payload) {
        this.setState({mode: newMode, url: undefined});
      } else {
        this.setState({mode: newMode});
      }
    };
    const onChangeUrl = (e) => this.setState({
      url: e.target.value
    });
    const onCancel = () => this.setState({url: undefined, mode: undefined});
    const onSave = () => {
      this.setState({
        pending: true
      }, () => {
        const hide = message.loading('Saving...', 0);
        this.saveCurrentThemes(this.themes, mode, url)
          .then(() => this.setState({url: undefined, mode: undefined}))
          .catch(e => message.error(e.message, 5))
          .then(() => {
            hide();
            this.setState({
              pending: false
            });
          });
      });
    };
    const downloadFile = () => this.saveCurrentThemes(this.themes, ThemesPreferenceModes.url);
    return (
      <div
        className={
          classNames(
            styles.section,
            'cp-divider',
            'bottom'
          )
        }
      >
        <div className={styles.section}>
          <b>{cloudPipelineAppName}</b> User Interface themes are configured
          using <code>{ThemesPreferenceName}</code> preference. This preference may
          contain an <b>URL</b> to the configuration file (recommended)
          or <b>serialized</b> themes (JSON object).
        </div>
        <div className={styles.section}>
          <div className={styles.section}>
            <Radio
              disabled={pending}
              checked={currentMode === ThemesPreferenceModes.url}
              onChange={() => onChangeMode(ThemesPreferenceModes.url)}
            >
              Specify <b>URL</b> to the themes configuration file
            </Radio>
            {
              currentMode === ThemesPreferenceModes.url && (
                <div style={{paddingLeft: 22}}>
                  <div>
                    You should <a onClick={downloadFile}>download</a> configuration
                    file and save it <b>manually</b> by the given URL.
                  </div>
                  <div>
                    <Input
                      disabled={pending}
                      value={url === undefined ? themesStore.themesURL : url}
                      placeholder="URL to the configuration file"
                      onChange={onChangeUrl}
                    />
                  </div>
                </div>
              )
            }
          </div>
          <div className={styles.section}>
            <Radio
              disabled={pending}
              checked={currentMode === ThemesPreferenceModes.payload}
              onChange={() => onChangeMode(ThemesPreferenceModes.payload)}
            >
              Save themes as serialized object
            </Radio>
            <div style={{paddingLeft: 22}}>
              This will increase overall preferences size due to
              all images are stored as base64-encoded data.
            </div>
          </div>
        </div>
        {
          ((currentMode !== themesStore.mode) || url !== undefined) && (
            <div
              className={
                classNames(
                  styles.section,
                  styles.actions
                )
              }
              style={{justifyContent: 'flex-end'}}
            >
              <Button
                disabled={pending}
                className={styles.action}
                onClick={onCancel}
              >
                CANCEL
              </Button>
              <Button
                disabled={(currentMode === ThemesPreferenceModes.url && !url) || pending}
                className={styles.action}
                type="primary"
                onClick={onSave}
              >
                SAVE
              </Button>
            </div>
          )
        }
      </div>
    );
  };

  render () {
    const {
      router,
      preferences,
      authenticatedUserInfo
    } = this.props;
    const cloudPipelineAppName = preferences.deploymentName || 'Cloud Pipeline';
    const administrator = !!authenticatedUserInfo &&
      !!authenticatedUserInfo.loaded &&
      !!authenticatedUserInfo.value &&
      authenticatedUserInfo.value.admin;
    const {
      editableTheme,
      pending,
      editableThemePreviewClassName
    } = this.state;
    const goBack = () => {
      if (editableTheme) {
        this.onCancelEdit();
      } else {
        router && router.push('/settings/profile/appearance');
      }
    };
    if (
      authenticatedUserInfo &&
      authenticatedUserInfo.pending &&
      !authenticatedUserInfo.loaded
    ) {
      return (
        <div className={styles.appearanceManagement}>
          <LoadingView />
        </div>
      );
    }
    if (
      authenticatedUserInfo &&
      authenticatedUserInfo.loaded &&
      !administrator
    ) {
      return (
        <div className={styles.appearanceManagement}>
          <Alert message="Access denied" type="error" />
        </div>
      );
    }
    return (
      <div className={styles.appearanceManagement}>
        <div
          className={
            classNames(
              styles.header,
              'cp-divider',
              'bottom'
            )
          }
        >
          <div className={styles.title}>
            <Button
              className={styles.back}
              size="small"
              onClick={goBack}
            >
              <Icon type="left" />
            </Button>
            <h2 className="cp-title">
              <span>{cloudPipelineAppName} UI Themes management</span>
              {
                editableTheme ? (<span>: {editableTheme.name}</span>) : undefined
              }
            </h2>
          </div>
          {this.renderActions()}
        </div>
        {
          !editableTheme && this.renderPreferenceDisclaimer()
        }
        {
          !editableTheme && this.renderThemesSelector()
        }
        <UIThemeEditForm
          previewClassName={editableThemePreviewClassName}
          theme={editableTheme}
          onChange={this.onChangeTheme}
          themes={this.themes}
          readOnly={pending}
          removable={editableTheme && !editableTheme.isNew}
          onRemove={this.onRemove}
        />
      </div>
    );
  }
}

AppearanceManagement.propTypes = {
  router: PropTypes.object
};

export default AppearanceManagement;
