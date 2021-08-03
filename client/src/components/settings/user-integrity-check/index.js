import React from 'react';
import PropTypes from 'prop-types';
import {
  Alert,
  AutoComplete,
  Modal,
  Input,
  Pagination
} from 'antd';
import {inject, observer} from 'mobx-react';
import checkUsersIntegrity, {loadUsersMetadata} from './check';
import LoadingView from '../../special/LoadingView';
import styles from './UserIntegrityCheck.css';

const PAGE_SIZE = 10;

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

  getSystemDictionary (key) {
    return this.dictionaries.find(dictionary => dictionary.key === key);
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
      const onChange = (opts) => {
        const {
          id,
          key,
          value
        } = opts;
        if (!metadata.hasOwnProperty(id)) {
          metadata[id] = {};
        }
        metadata[id][key] = value;
        this.setState({
          data: {...metadata}
        });
      };
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
                        mode="combobox"
                        size="large"
                        style={{width: '100%'}}
                        allowClear
                        autoFocus
                        backfill
                        onChange={(value) => onChange({id: user.id, value, key: column})}
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
                      onChange={e => onChange({id: user.id, value: e.target.value, key: column})}
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
      <div className={styles.scrollBox}>
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
                current={currentPage}
                pageSize={PAGE_SIZE}
                total={users.length}
                onChange={this.onPageChange}
                size="small"
              />
            )
          }
        </div>
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
