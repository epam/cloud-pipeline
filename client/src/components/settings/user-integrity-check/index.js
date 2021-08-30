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
import classNames from 'classnames';
import {
  Alert,
  Button,
  Checkbox,
  Icon,
  Input,
  message,
  Modal,
  Pagination,
  Select,
  Tooltip
} from 'antd';
import {computed} from 'mobx';
import {inject, observer} from 'mobx-react';
import updateUserMetadata from './update-user-metadata';
import addValueToSystemDictionary from './add-value-to-system-dictionary';
import checkUsersIntegrity, {loadUsersMetadata} from './check';
import getDictionaries from './dictionaries';
import LoadingView from '../../special/LoadingView';
import {
  build,
  buildPatterns,
  generatePatternSorter,
  getDefaultColumns,
  update
} from './links';
import styles from './user-integrity-check.css';
import UserName from '../../special/UserName';

const PAGE_SIZE = 10;

const FILTERS = {
  blocked: 'blocked',
  neverLoggedIn: 'never logged in'
};

@inject('systemDictionaries')
@observer
class UserIntegrityCheck extends React.Component {
  state = {
    currentPage: 1,
    pending: false,
    actionInProgress: false,
    defaultColumns: [],
    columns: [],
    data: {},
    initialData: {},
    error: undefined,
    visible: this.props.visible,
    paths: [],
    showFullMetadata: false,
    filters: []
  };

  get filteredUsers () {
    const {
      users = []
    } = this.props;
    const {
      filters = []
    } = this.state;
    return (users || [])
      .filter(user => {
        const blockedFilter = user.blocked || !filters.includes(FILTERS.blocked);
        const neverLoggedInFilter = !user.firstLoginDate ||
          !filters.includes(FILTERS.neverLoggedIn);
        return blockedFilter && neverLoggedInFilter;
      });
  }

  componentDidMount () {
    this.updateState();
    const {onInitialized} = this.props;
    onInitialized && onInitialized(this);
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      prevProps.users !== this.props.users ||
      (prevProps.visible !== this.props.visible && this.props.visible)
    ) {
      this.updateState();
    }
  }

  updateState = () => {
    this.setState({
      currentPage: 1
    }, this.fetchUsersAttributes);
  };

  @computed
  get dictionaries () {
    const {systemDictionaries} = this.props;
    return getDictionaries(systemDictionaries.loaded ? systemDictionaries.value : []);
  }

  get usersIds () {
    const {initialData} = this.state;
    return Object.keys(initialData);
  }

  get columns () {
    const {
      columns = [],
      defaultColumns = [],
      showFullMetadata
    } = this.state;
    if (showFullMetadata) {
      return columns;
    }
    return defaultColumns;
  }

  getSystemDictionary (key) {
    return this.dictionaries.find(dictionary => dictionary.key === key);
  }

  isNewValue = (dictionary, value) => {
    const {values = []} = dictionary;
    if (dictionary && value) {
      return !values
        .filter(v => !!v)
        .map(v => v.value)
        .includes(value);
    }
    return false;
  };

  isModifiedValue = (userId, key) => {
    const {data, initialData} = this.state;
    const userData = data[userId] || {};
    const initialUserData = initialData[userId] || {};
    const isEmpty = o => !o;
    return isEmpty(userData[key]) !== isEmpty(initialUserData[key]) ||
      (
        !isEmpty(userData[key]) &&
        !isEmpty(initialUserData[key]) &&
        userData[key] !== initialUserData[key]
      );
  };

  fetchUsersAttributes = () => {
    const {
      errors: integrityErrors = [],
      users = [],
      systemDictionaries: systemDictionariesRequest
    } = this.props;
    const defaultAttributesToDisplay = [...(new Set(integrityErrors.map(error => error.key)))];
    this.setState({
      pending: true,
      data: {},
      columns: [],
      defaultColumns: [],
      error: undefined,
      paths: [],
      showFullMetadata: false
    }, () => {
      systemDictionariesRequest.fetchIfNeededOrWait()
        .then(() => {
          loadUsersMetadata(users.map(user => user.id))
            .then(metadata => {
              const attributes = this.dictionaries.map(dictionary => dictionary.key);
              const usersMetadata = users
                .map(user => {
                  const userMetadata = (metadata || [])
                    .find(m => m.entity?.entityId === user.id);
                  const data = userMetadata ? userMetadata.data : {};
                  attributes.push(
                    ...(Object.keys(data || {}))
                  );
                  return {
                    [user.id]: Object.entries(data || {})
                      .map(([key, mValue]) => ({
                        [key]: mValue ? mValue.value : undefined
                      }))
                      .reduce((r, c) => ({...r, ...c}), {})
                  };
                })
                .reduce((r, c) => ({...r, ...c}), {});
              const attributesToDisplay = [...(new Set(attributes))]
                .sort((a, b) => {
                  const aIsDictionary = !!this.getSystemDictionary(a);
                  const bIsDictionary = !!this.getSystemDictionary(b);
                  if (aIsDictionary === bIsDictionary) {
                    return 0;
                  }
                  if (aIsDictionary) {
                    return -1;
                  }
                  return 1;
                });
              const patterns = buildPatterns(this.dictionaries);
              let defaultColumns = [
                ...new Set(
                  (defaultAttributesToDisplay || [])
                    .concat(getDefaultColumns(patterns, usersMetadata))
                )
              ];
              if (defaultColumns.length === 0) {
                defaultColumns = attributesToDisplay.slice();
              }
              defaultColumns.sort(generatePatternSorter(patterns));
              attributesToDisplay.sort(generatePatternSorter(patterns));
              return Promise.resolve({
                initialData: {...usersMetadata},
                columns: attributesToDisplay,
                defaultColumns,
                data: this.fixUsersMetadata({...usersMetadata}),
                paths: build(patterns, usersMetadata)
              });
            })
            .catch((e) => {
              return Promise.resolve({error: e.message});
            })
            .then((state) => this.setState({
              pending: false,
              ...state
            }));
        });
    });
  };

  fixUsersMetadata = (metadata) => {
    const userIds = Object.keys(metadata || {});
    for (let i = 0; i < userIds.length; i++) {
      const userId = userIds[i];
      metadata[userId] = {...metadata[userId]};
      const user = metadata[userId];
      const keys = Object.keys(user || {});
      const applyValue = (key, value, skipCheck = true) => {
        if (user[key] && !skipCheck) {
          return;
        }
        user[key] = value;
        const dictionary = this.getSystemDictionary(key);
        if (dictionary) {
          const dictionaryValue = (dictionary.values || []).find(v => v.value === value);
          if (dictionaryValue && dictionaryValue.links && dictionaryValue.links.length) {
            dictionaryValue.links.forEach(link => {
              applyValue(link.key, link.value, false);
            });
          }
        }
      };
      for (let k = 0; k < keys.length; k++) {
        const key = keys[k];
        if (user[key]) {
          applyValue(key, user[key]);
        }
      }
    }
    return {...metadata};
  };

  onPageChange = (page) => {
    this.setState({
      currentPage: page
    });
  };

  onFieldChange = (opts) => {
    const {data: metadata = {}, paths} = this.state;
    const {
      data: newData,
      paths: newPaths
    } = update(opts, metadata, this.dictionaries, paths);
    this.setState({
      data: newData,
      paths: newPaths
    });
  };

  getFieldParentLink = (user, key) => {
    const {data = {}} = this.state;
    const dictionary = this.getSystemDictionary(key);
    if (dictionary) {
      const value = data[user] ? data[user][key] : undefined;
      if (value) {
        const dictionaryValue = (dictionary.values || []).find(v => v.value === value);
        if (dictionaryValue) {
          const {linksFrom = []} = dictionary;
          for (let l = 0; l < linksFrom.length; l++) {
            const link = linksFrom[l];
            const linkDictionary = this.getSystemDictionary(link);
            const linkValue = data[user] ? data[user][link] : undefined;
            if (
              linkDictionary &&
              (linkDictionary.values || [])
                .find(v => v.value === linkValue &&
                  v.links &&
                  v.links.find(l => l.key === key && l.value === value)
                )
            ) {
              return {
                from: link,
                fromValue: linkValue,
                to: key,
                toValue: value
              };
            }
          }
        }
      }
    }
    return undefined;
  };

  getNewDictionaryValues = () => {
    const {data} = this.state;
    const users = Object.keys(data);
    return users.reduce((dataToUpdate, user) => {
      Object.entries(data[user])
        .forEach(([key, value]) => {
          const dictionary = this.getSystemDictionary(key);
          if (
            dictionary &&
            this.isNewValue(dictionary, value) &&
            !(dataToUpdate[key] || []).includes(value)
          ) {
            dataToUpdate[key] = [...(dataToUpdate[key] || []), value];
          }
        });
      return dataToUpdate;
    }, {});
  };

  saveNewValuesToDictionaries = () => {
    const {data} = this.state;
    const {systemDictionaries} = this.props;
    const newDictionaryValues = this.getNewDictionaryValues();
    return new Promise((resolve, reject) => {
      const promises = Object
        .entries(newDictionaryValues)
        .map(([dictionary, newValues]) => addValueToSystemDictionary(
          this.getSystemDictionary(dictionary),
          newValues
        ));
      Promise.all(promises)
        .then(() => {
          return systemDictionaries.fetch();
        })
        .then(() => {
          const modifiedDictionaries = Object.keys(newDictionaryValues);
          const update = [];
          // creating "links to"
          this.dictionaries
            .filter(dictionary => dictionary.linksTo
              .find(link => modifiedDictionaries.includes(link))
            )
            .forEach(dictionary => {
              const modifiedDictionary = {
                id: dictionary.id,
                key: dictionary.key,
                values: (dictionary.values || []).map(v => ({links: [], ...v}))
              };
              const {
                values = []
              } = modifiedDictionary;
              const {
                key,
                linksTo = []
              } = dictionary;
              for (let v = 0; v < values.length; v++) {
                const {value, links = []} = values[v];
                const usersData = Object.values(data)
                  .filter(ud => ud[key] === value);
                const missingLinks = linksTo.filter(to => !links.find(l => l.key === to));
                for (let l = 0; l < missingLinks.length; l++) {
                  const linkKey = missingLinks[l];
                  const linkDictionary = this.getSystemDictionary(linkKey);
                  const userData = usersData.find(u => u[linkKey]);
                  const linkDictionaryValue = (linkDictionary.values || [])
                    .find(v => userData && v.value === userData[linkKey]);
                  if (linkDictionaryValue) {
                    // creating link "key:value" -> "linkKey:userData[linkKey]"
                    links.push({
                      attributeId: linkDictionary.id,
                      id: linkDictionaryValue.id,
                      key: linkKey,
                      value: linkDictionaryValue.value
                    });
                    // eslint-disable-next-line
                    console.info(`Creating new link ${key}:${value} -> ${linkKey}:${linkDictionaryValue.value}`);
                    if (update.indexOf(modifiedDictionary) === -1) {
                      update.push(modifiedDictionary);
                    }
                  }
                }
              }
            });
          return Promise.all(update.map(dictionary => addValueToSystemDictionary(dictionary)));
        })
        .then(() => systemDictionaries.fetch())
        .then(() => resolve())
        .catch(reject);
    });
  }

  onSave = () => {
    return new Promise((resolve) => {
      const {onClose} = this.props;
      const {data, initialData} = this.state;
      const usersWithChanges = this.usersIds.filter(id => {
        const metadataKeys = Object.keys({...data[id], ...initialData[id]});
        return metadataKeys.some(key => {
          const initial = initialData[id] || {};
          const updated = data[id] || {};
          return updated[key] !== initial[key];
        });
      });
      this.setState({actionInProgress: true}, () => {
        let hide = message.loading(`Updating dictionaries...`, 0);
        this.saveNewValuesToDictionaries()
          .then(() => {
            hide();
            hide = message.loading(
              `Updating user${usersWithChanges.length > 1 ? 's' : ''} metadata...`,
              0
            );
            return Promise.all(usersWithChanges.map(userId => {
              const user = data[userId] || {};
              const userPayload = Object.entries(user)
                .reduce((acc, [key, value]) => {
                  if (value) {
                    acc[key] = {value};
                  }
                  return acc;
                }, {});
              return updateUserMetadata(userId, userPayload);
            }));
          })
          .catch(e => {
            message.error(e.message, 5);
            return Promise.resolve(false);
          })
          .then((success) => {
            hide();
            this.setState({actionInProgress: false}, () => {
              success && onClose && onClose();
              resolve(success);
            });
          });
      });
    });
  };

  renderTableHead = () => {
    return (
      <tr>
        <th>USERNAME</th>
        {
          this.columns.map((attr) => (
            <th key={attr}>
              {attr}
            </th>)
          )}
      </tr>

    );
  };

  renderTableContent = () => {
    const {
      errors = []
    } = this.props;
    const {
      currentPage,
      data: metadata = {},
      actionInProgress
    } = this.state;
    const data = this.filteredUsers.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE);
    if (data.length > 0) {
      return data.map((user) => {
        const userMetadata = metadata[user.id] || {};
        const userErrors = errors.filter(error => error.user.id === user.id);
        const {
          blocked
        } = user;
        return (
          <tr key={user.userName}>
            <th key="userName">
              <div
                className={
                  classNames(
                    styles.userNameCellContainer,
                    {
                      [styles.blocked]: blocked
                    }
                  )
                }
              >
                <span className={styles.userNameCell}>
                  {
                    blocked && (<Icon type="lock" />)
                  }
                  <UserName userName={user.userName} />
                </span>
                {
                  userErrors.length > 0 && (
                    <Tooltip
                      overlay={(
                        <ul style={{listStyle: 'disc', paddingLeft: 20}}>
                          {
                            userErrors.map((error, index) => (
                              <li key={`${error.user.userName}-error-${index}`}>
                                {error.error}
                              </li>
                            ))
                          }
                        </ul>
                      )}
                    >
                      <Icon
                        type="exclamation-circle-o"
                        className={styles.issues}
                      />
                    </Tooltip>
                  )
                }
              </div>
            </th>
            {
              this.columns.map(column => {
                const dictionary = this.getSystemDictionary(column);
                if (dictionary) {
                  const fieldParentLink = this.getFieldParentLink(user.id, column);
                  return (
                    <td key={column}>
                      <div className={styles.cell}>
                        <Select
                          className={
                            classNames({
                              [styles.modifiedValue]:
                                this.isModifiedValue(user.id, column),
                              [styles.newValue]:
                                this.isNewValue(dictionary, userMetadata[column])
                            })
                          }
                          mode="combobox"
                          size="large"
                          style={{flex: 1}}
                          allowClear
                          disabled={actionInProgress || !!fieldParentLink}
                          onChange={(value) => this.onFieldChange({
                            id: user.id,
                            value,
                            key: column
                          })}
                          filterOption={
                            (input, option) =>
                              option.props.value.toLowerCase().indexOf(input.toLowerCase()) >= 0
                          }
                          value={
                            userMetadata.hasOwnProperty(column)
                              ? userMetadata[column]
                              : undefined
                          }
                          dropdownMatchSelectWidth={false}
                        >
                          {
                            this.isNewValue(dictionary, userMetadata[column]) && (
                              <Select.Option
                                key={userMetadata[column]}
                                value={userMetadata[column]}
                              >
                                {userMetadata[column]} <i>(new value)</i>
                              </Select.Option>
                            )
                          }
                          {
                            (dictionary.values || []).map((value) => (
                              <Select.Option
                                key={value.id}
                                value={value.value || ''}
                              >
                                {value.value || (<i>Empty value</i>)}
                              </Select.Option>
                            ))
                          }
                        </Select>
                        {
                          fieldParentLink && (
                            <Tooltip
                              overlay={(
                                <div>
                                  <div>This is linked value</div>
                                  <div className={styles.linkDescription}>
                                    <span>{fieldParentLink.from}:{fieldParentLink.fromValue}</span>
                                    <Icon
                                      type="caret-right"
                                    />
                                    <span>{fieldParentLink.to}:{fieldParentLink.toValue}</span>
                                  </div>
                                </div>
                              )}
                              getPopupContainer={node => node.parentNode}
                            >
                              <Icon
                                className={styles.cellInfo}
                                type="info-circle"
                              />
                            </Tooltip>
                          )
                        }
                      </div>
                    </td>
                  );
                }
                return (
                  <td key={column}>
                    <Input
                      disabled={actionInProgress}
                      className={
                        classNames(
                          {
                            [styles.modifiedValue]:
                              this.isModifiedValue(user.id, column)
                          },
                          styles.input
                        )
                      }
                      size="large"
                      style={{width: '100%'}}
                      value={
                        userMetadata.hasOwnProperty(column)
                          ? userMetadata[column]
                          : undefined
                      }
                      onChange={e => this.onFieldChange({
                        id: user.id,
                        value: e.target.value,
                        key: column
                      })}
                    />
                  </td>
                );
              })
            }
          </tr>
        );
      });
    }
    return null;
  };

  renderContent = () => {
    const {
      error,
      pending
    } = this.state;
    if (error) {
      return (
        <Alert type="error" message={error} />
      );
    }
    if (pending) {
      return (<LoadingView />);
    }
    const blur = (e) => {
      e.target.focus();
    };
    const {
      filters
    } = this.state;
    const onChangeFilters = (newFilters) => {
      this.setState({
        filters: (newFilters || []).slice(),
        currentPage: 1
      });
    };
    return [
      <div
        key="filter"
        className={styles.filters}
      >
        <span className={styles.label}>
          Filter users:
        </span>
        <Select
          style={{flex: 1}}
          value={filters}
          onChange={onChangeFilters}
          mode="multiple"
          getPopupContainer={node => node.parentNode}
          dropdownStyle={{zIndex: 1054}}
        >
          <Select.Option key={FILTERS.blocked} value={FILTERS.blocked}>
            Blocked users
          </Select.Option>
          <Select.Option key={FILTERS.neverLoggedIn} value={FILTERS.neverLoggedIn}>
            Never logged in users
          </Select.Option>
        </Select>
      </div>,
      <div
        key="table"
        tabIndex={0}
        className={styles.tableContainer}
        onScroll={blur}
      >
        <table className={styles.table}>
          <thead>
            {this.renderTableHead()}
          </thead>
          <tbody>
            {this.renderTableContent()}
          </tbody>
        </table>
      </div>
    ];
  };

  render () {
    const {
      visible,
      onClose,
      users,
      mode
    } = this.props;
    const {
      defaultColumns,
      columns,
      showFullMetadata,
      currentPage
    } = this.state;
    const pagesCount = Math.ceil(this.filteredUsers.length / PAGE_SIZE);
    const {actionInProgress} = this.state;
    if (/^inline$/i.test(mode)) {
      if (users.length === 0) {
        return (
          <Alert
            type="success"
            message="No issues found"
            showIcon
          />
        );
      }
      return (
        <div style={{width: '100%', position: 'relative'}}>
          {this.renderContent()}
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              marginTop: 5
            }}
          >
            <div>
              {
                defaultColumns.length !== columns.length && (
                  <Checkbox
                    checked={showFullMetadata}
                    onChange={e => this.setState({showFullMetadata: e.target.checked})}
                  >
                    Show all attributes
                  </Checkbox>
                )
              }
            </div>
            <div>
              {
                pagesCount > 1 && (
                  <Pagination
                    current={currentPage}
                    pageSize={PAGE_SIZE}
                    total={this.filteredUsers.length}
                    onChange={this.onPageChange}
                    size="small"
                  />
                )
              }
            </div>
          </div>
        </div>
      );
    }
    if (users.length === 0) {
      return (
        <Modal
          title="Integrity check results"
          footer={false}
          onCancel={onClose}
          visible={visible}
        >
          <Alert
            type="success"
            message="No issues found"
            showIcon
          />
        </Modal>
      );
    }
    return (
      <Modal
        className={styles.modal}
        title="Integrity check results"
        visible={visible}
        onCancel={onClose}
        bodyStyle={{padding: '10px'}}
        width={'90vw'}
        footer={(
          <div className={styles.footer}>
            <Button
              onClick={onClose}
            >
              CANCEL
            </Button>
            <Button
              type="primary"
              disabled={actionInProgress}
              onClick={this.onSave}
            >
              SAVE
            </Button>
          </div>
        )}
      >
        {this.renderContent()}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            marginTop: 5
          }}
        >
          <div>
            {
              defaultColumns.length !== columns.length && (
                <Checkbox
                  checked={showFullMetadata}
                  onChange={e => this.setState({showFullMetadata: e.target.checked})}
                >
                  Show all attributes
                </Checkbox>
              )
            }
          </div>
          <div>
            {
              pagesCount > 1 && (
                <Pagination
                  current={currentPage}
                  pageSize={PAGE_SIZE}
                  total={this.filteredUsers.length}
                  onChange={this.onPageChange}
                  size="small"
                />
              )
            }
          </div>
        </div>
      </Modal>
    );
  };
}

UserIntegrityCheck.propTypes = {
  visible: PropTypes.bool,
  errors: PropTypes.array,
  users: PropTypes.array,
  onClose: PropTypes.func,
  mode: PropTypes.oneOf(['inline', 'modal']),
  onInitialized: PropTypes.func
};

UserIntegrityCheck.defaultProps = {
  mode: 'modal'
};

UserIntegrityCheck.check = checkUsersIntegrity;

export default UserIntegrityCheck;
