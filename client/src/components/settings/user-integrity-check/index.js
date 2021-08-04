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
  AutoComplete,
  Modal,
  Input,
  Pagination
} from 'antd';
import {inject, observer} from 'mobx-react';
import checkUsersIntegrity, {loadUsersMetadata, extractLinks} from './check';
import LoadingView from '../../special/LoadingView';
import styles from './UserIntegrityCheck.css';

const PAGE_SIZE = 10;

const getLinkedValues = (key, value, dictionariesValues) => {
  if (key && value && dictionariesValues?.length) {
    let linkedValues = {};
    const links = extractLinks(key, value, dictionariesValues);
    if (links && links.length) {
      linkedValues = (links || [])
        .reduce((acc, link) => ({
          ...acc,
          ...{[link.key]: link.value}
        }), {});
    }
    return linkedValues;
  }
  return {};
};

@inject('systemDictionaries')
@observer
class UserIntegrityCheck extends React.Component {
  state = {
    tableContent: [],
    currentPage: 1,
    pagesCount: 0,
    pending: false,
    columns: [],
    data: {},
    error: undefined,
    visible: this.props.visible
  };

  componentDidMount () {
    this.updateState();
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
    const {users = []} = this.props;
    const pagesCount = Math.ceil(users.length / PAGE_SIZE);
    this.setState({
      currentPage: 1,
      pagesCount
    }, this.fetchUsersAttributes);
  };

  get dictionaries () {
    const {systemDictionaries} = this.props;
    if (systemDictionaries.loaded) {
      return (systemDictionaries.value || []).slice();
    }
    return [];
  }

  get dictionariesValues () {
    return this.dictionaries
      .reduce((acc, current) => ([...acc, ...current.values]), []);
  }

  getSystemDictionary (key) {
    return this.dictionaries.find(dictionary => dictionary.key === key);
  }
  isNewValue = (userId, dictionary) => {
    const {data} = this.state;
    if (dictionary && data[userId] && data[userId][key]) {
      const {key, values} = dictionary;
      return !values
        .filter(v => !!v)
        .map(v => v.value)
        .includes(data[userId][key]);
    }
    return false;
  }

  isNewValue = (userId, dictionary) => {
    const {data} = this.state;
    const {key, values} = dictionary;
    if (dictionary && data[userId] && data[userId][key]) {
      return !values
        .filter(v => !!v)
        .map(v => v.value)
        .includes(data[userId][key]);
    }
    return false;
  }

  fetchUsersAttributes = () => {
    const {
      users = [],
      systemDictionaries: systemDictionariesRequest
    } = this.props;
    this.setState({
      pending: true,
      data: {},
      columns: [],
      error: undefined
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
              return Promise.resolve({
                data: usersMetadata,
                columns: attributesToDisplay
              });
            })
            .catch((e) => {
              return Promise.resolve({error: e.message});
            })
            .then((state) => this.setState({pending: false, ...state}));
        });
    });
  };

  onPageChange = (page) => {
    this.setState({
      currentPage: page
    });
  }

  onFieldChange = (opts) => {
    const {
      id,
      key,
      value
    } = opts;
    const {data: metadata = {}} = this.state;
    const currentUser = metadata[id] || {};
    const linkedValues = getLinkedValues(key, value, this.dictionariesValues);
    const newUserData = {
      ...currentUser,
      ...linkedValues,
      [key]: value
    };
    this.setState({
      data: {
        ...metadata,
        [id]: newUserData
      }
    });
  };

  renderTableHead = () => {
    const {columns = []} = this.state;
    return (
      <tr>
        <th>USERNAME</th>
        {
          columns.map((attr) => (
            <th key={attr}>
              {attr}
            </th>)
          )}
      </tr>

    );
  }

  renderTableContent = () => {
    const {
      users = []
    } = this.props;
    const {
      currentPage,
      columns = [],
      data: metadata = {}
    } = this.state;
    const data = users.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE);
    if (data.length > 0) {
      return data.map((user) => {
        const userMetadata = metadata[user.id] || {};
        return (
          <tr key={user.userName}>
            <th key="userName">{user.userName}</th>
            {
              columns.map(column => {
                const dictionary = this.getSystemDictionary(column);
                if (dictionary) {
                  return (
                    <td key={column}>
                      <AutoComplete
                        className={classNames({
                          [styles.newDictionaryValue]: this.isNewValue(user.id, dictionary)
                        })}
                        mode="combobox"
                        size="large"
                        style={{width: '100%'}}
                        allowClear
                        autoFocus
                        backfill
                        onChange={(value) => this.onFieldChange({
                          id: user.id,
                          value,
                          key: column
                        })}
                        filterOption={
                          (input, option) =>
                            option.props.children.toLowerCase().indexOf(input.toLowerCase()) >= 0
                        }
                        value={
                          userMetadata.hasOwnProperty(column)
                            ? userMetadata[column]
                            : undefined
                        }
                      >
                        {
                          (dictionary.values || []).map((value) => (
                            <AutoComplete.Option
                              key={value.id}
                              value={value.value}
                            >
                              {value.value}
                            </AutoComplete.Option>))
                        }
                      </AutoComplete>
                    </td>
                  );
                }
                return (
                  <td key={column}>
                    <Input
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
      pagesCount,
      currentPage,
      error,
      pending
    } = this.state;
    const {
      users = []
    } = this.props;
    if (error) {
      return (
        <Alert type="error" message={error} />
      );
    }
    if (pending) {
      return (<LoadingView />);
    }
    return (
      <div className={styles.tableContainer}>
        <table className={styles.table}>
          <thead>
            {this.renderTableHead()}
          </thead>
          <tbody>
            {this.renderTableContent()}
          </tbody>
        </table>
        {
          pagesCount > 1 && (
            <Pagination
              className={styles.pagination}
              current={currentPage}
              pageSize={PAGE_SIZE}
              total={users.length}
              onChange={this.onPageChange}
              size="small"
            />
          )
        }
      </div>
    );
  };

  render () {
    const {
      visible,
      onClose
    } = this.props;
    return (
      <Modal
        className={styles.modal}
        title="Fix users issues"
        visible={visible}
        footer={null}
        onCancel={onClose}
        bodyStyle={{padding: '10px', overflowX: 'scroll'}}
        width={'90vw'}
      >
        {this.renderContent()}
      </Modal>
    );
  }
}

UserIntegrityCheck.propTypes = {
  visible: PropTypes.bool,
  users: PropTypes.array,
  onClose: PropTypes.func
};

UserIntegrityCheck.check = checkUsersIntegrity;

export default UserIntegrityCheck;
