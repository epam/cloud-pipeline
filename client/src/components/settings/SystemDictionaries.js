/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import {computed} from 'mobx';
import {inject, observer} from 'mobx-react';
import {
  Alert,
  Button,
  Icon,
  Input,
  Modal,
  message
} from 'antd';
import SystemDictionaryForm from './forms/SystemDictionaryForm';
import roleModel from '../../utils/roleModel';
import SystemDictionariesUpdate from '../../models/systemDictionaries/SystemDictionariesUpdate';
import SystemDictionariesDelete from '../../models/systemDictionaries/SystemDictionariesDelete';
import LoadingView from '../special/LoadingView';
import SubSettings from './sub-settings';
import styles from './SystemDictionaries.css';

function nameSorter (a, b) {
  const aName = (a.key || '').toLowerCase();
  const bName = (b.key || '').toLowerCase();
  if (aName === bName) {
    return 0;
  }
  return aName < bName ? -1 : 1;
}

const NEW_DICTIONARY_KEY = 'new';

function getDictionaryKey (isNew, key) {
  return isNew ? NEW_DICTIONARY_KEY : `dictionary-${key}`;
}

@inject('systemDictionaries')
@roleModel.authenticationInfo
@observer
class SystemDictionaries extends React.Component {
  state = {
    newDictionary: false,
    modified: false,
    pending: false,
    changesCanBeSkipped: false,
    filter: undefined,
    currentDictionaryKey: undefined
  };

  componentDidMount () {
    const {route, router, systemDictionaries} = this.props;
    if (route && router) {
      router.setRouteLeaveHook(route, this.checkModifiedBeforeLeave);
    }
    systemDictionaries.fetch();
  };

  componentWillUnmount () {
    this.resetChangesStateTimeout && clearTimeout(this.resetChangesStateTimeout);
  }

  @computed
  get dictionaries () {
    const {systemDictionaries} = this.props;
    if (!systemDictionaries.loaded) {
      return [];
    }
    return systemDictionaries.value || [];
  }

  get sections () {
    const dataSource = [];
    const {newDictionary, filter} = this.state;
    if (newDictionary) {
      dataSource.push({isNew: true});
    }
    const lowerCasedFilter = (filter || '').toLowerCase();
    dataSource.push(
      ...this.dictionaries
        .filter((dict) => !filter ||
          (dict.values || [])
            .find(v => (v.value || '').toLowerCase().indexOf(lowerCasedFilter) >= 0)
        )
        .sort(nameSorter)
    );
    return dataSource
      .map(dictionary => ({
        key: getDictionaryKey(dictionary.isNew, dictionary.key),
        title: dictionary.isNew ? 'New dictionary' : dictionary.key,
        disabled: newDictionary && !dictionary.isNew,
        dictionary
      }));
  }

  onChangeSection = (section) => {
    this.setState({
      currentDictionaryKey: section
    });
  };

  checkModifiedBeforeLeave = (nextLocation) => {
    const {router} = this.props;
    const {changesCanBeSkipped, modified} = this.state;
    const resetChangesCanBeSkipped = () => {
      this.resetChangesStateTimeout = setTimeout(
        () => this.setState && this.setState({changesCanBeSkipped: false}),
        0
      );
    };
    const makeTransition = () => {
      this.setState({changesCanBeSkipped: true},
        () => {
          router.push(nextLocation);
          resetChangesCanBeSkipped();
        }
      );
    };
    if (modified && !changesCanBeSkipped) {
      this.confirmChangeDictionary()
        .then(confirmed => confirmed ? makeTransition() : undefined);
      return false;
    }
  };

  confirmChangeDictionary = () => {
    const {modified} = this.state;
    return new Promise((resolve) => {
      if (modified) {
        Modal.confirm({
          title: 'You have unsaved changes. Continue?',
          style: {
            wordWrap: 'break-word'
          },
          onOk () {
            resolve(true);
          },
          onCancel () {
            resolve(false);
          },
          okText: 'Yes',
          cancelText: 'No'
        });
      } else {
        resolve(true);
      }
    });
  };

  addNewDictionary = () => {
    this.setState({
      newDictionary: true,
      currentDictionaryKey: getDictionaryKey(true)
    });
  };

  onDictionaryChanged = (name, items, changed) => {
    this.setState({modified: changed});
  };

  onDictionarySave = (dictionary, name, items, previousName) => {
    const hide = message.loading('Saving dictionary...', 0);
    const {systemDictionaries} = this.props;
    this.setState({pending: true}, async () => {
      if (previousName) {
        const removeRequest = new SystemDictionariesDelete(previousName);
        await removeRequest.send();
        if (removeRequest.error) {
          hide();
          message.error(removeRequest.error, 5);
          this.setState({pending: false});
          return;
        }
      }
      const request = new SystemDictionariesUpdate();
      await request.send([{
        key: name,
        values: items
      }]);
      if (request.error) {
        hide();
        message.error(request.error, 5);
        this.setState({pending: false});
      } else {
        await systemDictionaries.fetch();
        hide();
        this.setState({
          pending: false,
          modified: false,
          newDictionary: false,
          currentDictionaryKey: getDictionaryKey(false, name)
        });
      }
    });
  };

  onDictionaryDelete = (dictionary) => {
    if (this.state.newDictionary || !dictionary || dictionary.isNew) {
      this.setState({
        newDictionary: false,
        currentDictionaryKey: undefined
      });
    } else {
      const hide = message.loading('Removing dictionary...', 0);
      const {systemDictionaries} = this.props;
      this.setState({pending: true}, async () => {
        const request = new SystemDictionariesDelete(dictionary.key);
        await request.send();
        if (request.error) {
          hide();
          message.error(request.error, 5);
          this.setState({pending: false});
        } else {
          await systemDictionaries.fetch();
          hide();
          this.setState({
            pending: false,
            modified: false,
            currentDictionaryKey: undefined
          });
        }
      });
    }
  };

  onFilter = (e) => {
    this.setState({filter: e.target.value});
  };

  render () {
    const {authenticatedUserInfo} = this.props;
    if (!authenticatedUserInfo.loaded && authenticatedUserInfo.pending) {
      return null;
    }
    if (!authenticatedUserInfo.value.admin) {
      return (
        <Alert type="error" message="Access is denied" />
      );
    }
    const {systemDictionaries} = this.props;
    if (!systemDictionaries.loaded && systemDictionaries.pending) {
      return <LoadingView />;
    }
    if (systemDictionaries.error) {
      return <Alert type="warning" message={systemDictionaries.error} />;
    }
    return (
      <div className={styles.container}>
        <div
          className={styles.actions}
        >
          <Input.Search
            style={{flex: 1}}
            value={this.state.filter}
            onChange={this.onFilter}
            placeholder="Search dictionary"
          />
          <Button
            className={styles.action}
            disabled={this.state.pending || this.state.newDictionary}
            onClick={this.addNewDictionary}
          >
            <Icon type="plus" />
            <span>Add dictionary</span>
          </Button>
        </div>
        <SubSettings
          sections={this.sections}
          activeSectionKey={this.state.currentDictionaryKey}
          onSectionChange={this.onChangeSection}
          canNavigate={this.confirmChangeDictionary}
          emptyDataPlaceholder={(
            <span
              className="cp-text-not-important"
              style={{flex: 1, textAlign: 'center'}}
            >
              Nothing found
            </span>
          )}
        >
          {
            ({section}) => (
              <SystemDictionaryForm
                filter={this.state.filter}
                disabled={this.state.pending}
                isNew={this.state.newDictionary}
                onDelete={() => this.onDictionaryDelete(section.dictionary)}
                onSave={
                  (name, values, previousName) =>
                    this.onDictionarySave(section.dictionary, name, values, previousName)
                }
                onChange={this.onDictionaryChanged}
                name={section.dictionary.key}
                items={(section.dictionary.values || []).map(o => o)}
                dictionaries={this.dictionaries.slice()}
              />
            )
          }
        </SubSettings>
      </div>
    );
  }
}

export default SystemDictionaries;
