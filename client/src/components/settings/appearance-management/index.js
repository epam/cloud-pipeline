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
import {Alert, Button, Icon, message} from 'antd';
import classNames from 'classnames';
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import roleModel from '../../../utils/roleModel';
import LoadingView from '../../special/LoadingView';
import UIThemeEditForm from './ui-theme-edit-form';
import ThemeCard from './theme-card';
import themesAreEqual from './utilities/themes-are-equal';
import validateTheme from './utilities/theme-validation';
import {DefaultThemeIdentifier, generateIdentifier} from '../../../themes';
import styles from './appearance-management.css';

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
    pending: false
  };

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
    const onSelectTheme = (identifier) => {
      const theme = themes.find(o => o.identifier === identifier);
      this.setState({
        editableTheme: theme,
        themePayload: {...theme},
        modified: false,
        valid: true
      }, this.injectEditableThemeStyles);
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
              readOnly={theme.predefined}
              onSelect={() => onSelectTheme(theme.identifier)}
            />
          ))
        }
      </div>
    );
  };

  injectEditableThemeStyles = () => {
    const {
      themes: themesStore
    } = this.props;
    const {
      editableTheme,
      themePayload
    } = this.state;
    if (themesStore && themePayload && editableTheme) {
      const {identifier} = editableTheme;
      const previewClassName = themesStore.startTestingTheme({...themePayload, identifier});
      this.setState({
        editableThemePreviewClassName: previewClassName
      });
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
      }, this.injectEditableThemeStyles);
    }
  };

  onCreateTheme = () => {
    const newTheme = {
      isNew: true,
      name: 'New theme',
      extends: DefaultThemeIdentifier
    };
    this.setState({
      editableTheme: newTheme,
      themePayload: {...newTheme},
      modified: false,
      valid: validateTheme(newTheme, this.themes)
    }, this.injectEditableThemeStyles);
  };

  onSaveTheme = async () => {
    const {
      editableTheme,
      themePayload
    } = this.state;
    const {
      themes: themesStore
    } = this.props;
    if (editableTheme && themePayload) {
      const {
        isNew,
        identifier,
        predefined
      } = editableTheme;
      const createIdentifier = () => {
        let generated = generateIdentifier(themePayload.name);
        generated = (generated || '').concat(predefined ? '' : '-custom');
        const r = new RegExp(`^${generated}(|-[\d]+)$`, 'i');
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
        .concat(isNew ? [themeConfig] : [])
        .map(o => ({
          identifier: o.identifier,
          name: o.name,
          extends: o.extends,
          dark: o.dark,
          configuration: {...(o.properties || {})}
        }));
      const hide = message.loading(
        (
          <span>
            {isNew ? 'Creating' : 'Saving'} <b>{themePayload.name}</b> theme...
          </span>
        ),
        5
      );
      this.setState({
        pending: true
      }, () => {
        themesStore
          .saveThemes(themesPayload, true)
          .then(() => this.setState({
            editableTheme: undefined,
            themePayload: undefined,
            modified: false,
            valid: true
          }))
          .catch(e => message.error(e.message, 5))
          .then(() => {
            hide();
            this.ejectEditableThemeStyles();
            this.setState({pending: false});
          });
      });
    }
  };

  onCancelEdit = () => {
    this.setState({
      editableTheme: undefined,
      themePayload: undefined,
      modified: false,
      valid: true
    }, this.ejectEditableThemeStyles);
  };

  renderActions = () => {
    const {
      editableTheme,
      modified,
      valid
    } = this.state;
    if (editableTheme) {
      return (
        <div className={styles.actions}>
          <Button
            key="cancel"
            id="cancel-edit-theme-button"
            className={styles.action}
            onClick={this.onCancelEdit}
          >
            Cancel
          </Button>
          <Button
            key="primary"
            id="save-theme-button"
            disabled={!valid || !modified}
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
        >
          New theme
        </Button>
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
          !editableTheme && this.renderThemesSelector()
        }
        <UIThemeEditForm
          previewClassName={editableThemePreviewClassName}
          theme={editableTheme}
          onChange={this.onChangeTheme}
          themes={this.themes}
          readOnly={pending}
        />
      </div>
    );
  }
}

AppearanceManagement.propTypes = {
  router: PropTypes.object
};

export default AppearanceManagement;
