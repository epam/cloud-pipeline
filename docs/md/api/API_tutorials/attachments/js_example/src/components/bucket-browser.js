import React from 'react';
import PropTypes from 'prop-types';
import Alert from 'antd/es/alert';
import Breadcrumb from 'antd/es/breadcrumb';
import Button from 'antd/es/button';
import Checkbox from 'antd/es/checkbox';
import Table from 'antd/es/table';
import Modal from 'antd/es/modal';
import 'antd/es/alert/style/css';
import 'antd/es/breadcrumb/style/css';
import 'antd/es/button/style/css';
import 'antd/es/checkbox/style/css';
import 'antd/es/table/style/css';
import 'antd/es/modal/style/css';
import './bucket-browser.css';

import * as storageApi from '../api/storage';

class BucketBrowser extends React.Component {
  static propTypes = {
    onChange: PropTypes.func,
    onClose: PropTypes.func,
    storageId: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
    value: PropTypes.string,
    visible: PropTypes.bool
  };

  state = {
    folders: [],
    path: null,
    pending: false,
    value: null
  };

  componentDidMount() {
    this.operationWrapper(this.updateState)(this.props);
  }

  componentDidUpdate(prevProps, prevState, snapshot) {
    if (prevProps.visible !== this.props.visible) {
      this.operationWrapper(this.updateState)(this.props);
    }
  }

  get breadcrumbs() {
    const {path} = this.state;
    const items = (path || '').split('/').filter(Boolean);
    return [null, ...items].map((item, index, array) => ({
      name: item || 'Root',
      path: array.slice(0, index + 1).filter(Boolean).join('/'),
      canNavigate: index < array.length - 1
    }));
  }

  get folders() {
    const {folders, path} = this.state;
    if (!!path) {
      return [
        {
          path: (path || '').split('/').slice(0, -1).join('/'),
          name: '...',
          goToParent: true
        },
        ...folders
      ];
    }
    return folders;
  }

  operationWrapper = (fn) => (...opts) => {
    this.setState({pending: true}, async () => {
      await fn(...opts);
      this.setState({pending: false});
    });
  };

  fetchFolders = () => {
    return new Promise(async (resolve) => {
      const {path} = this.state;
      const {storageId} = this.props;
      const payload = await storageApi.list(storageId, path);
      if (payload.error) {
        this.setState({error: payload.error, folders: []}, resolve);
      } else {
        const {results} = payload;
        const folders = (results || []).filter(i => /^folder$/i.test(i.type));
        this.setState({error: null, folders}, resolve);
      }
    });
  };

  updateState = (props) => {
    const {value} = props;
    const path = (value || '').split('/').filter(Boolean).slice(0, -1).join('/');
    return new Promise((resolve) => {
      this.setState({value, path}, async () => {
        await this.fetchFolders();
        resolve();
      });
    });
  };

  isFolderSelected = (folder) => {
    if (folder.goToParent) {
      return false;
    }
    const {value} = this.state;
    const valueCorrected = (value || '').split('/').filter(Boolean).join('/');
    const regExp = new RegExp(`^${valueCorrected}$`, 'i');
    return regExp.test(folder.path);
  };

  selectFolder = (folder) => {
    this.setState({value: folder ? folder.path : null});
  };

  navigateToFolder = (folder) => {
    return new Promise(resolve => {
      this.setState({path: folder.path}, async () => {
        await this.fetchFolders();
        resolve();
      })
    });
  };

  getCheckBoxColumn = () => {
    return {
      key: 'checkbox',
      render: (folder) => {
        if (folder.goToParent) {
          return null;
        }
        return (
          <Checkbox
            checked={this.isFolderSelected(folder)}
            onChange={e => e.target.checked ? this.selectFolder(folder) : this.selectFolder(null)}
          />
        );
      },
      width: 30
    }
  };

  getPathColumn = () => {
    return {
      className: 'folder-name-cell',
      dataIndex: 'name',
      key: 'path',
      onCell: (folder) => ({
        onClick: () => this.operationWrapper(this.navigateToFolder)(folder)
      })
    };
  };

  onOk = () => {
    const {onChange} = this.props;
    const {value} = this.state;
    onChange(value);
  };

  render () {
    const {
      onClose,
      visible
    } = this.props;
    const {
      error,
      pending
    } = this.state;
    return (
      <Modal
        width="50vw"
        visible={visible}
        title="Browse"
        closable
        onCancel={onClose}
        footer={(
          <div style={{display: 'flex', flexDirection: 'row', justifyContent: 'space-between'}}>
            <Button
              disabled={pending}
              onClick={onClose}
            >
              Cancel
            </Button>
            <Button
              disabled={pending}
              onClick={this.onOk}
            >
              OK
            </Button>
          </div>
        )}
      >
        <div style={{marginBottom: 5}}>
          <Breadcrumb>
            {
              this.breadcrumbs.map(item => (
                <Breadcrumb.Item
                  key={item.path}
                  href={item.canNavigate ? "" : undefined}
                  onClick={item.canNavigate ? () => this.operationWrapper(this.navigateToFolder)(item) : undefined}
                >
                  {item.name}
                </Breadcrumb.Item>
              ))
            }
          </Breadcrumb>
        </div>
        {
          error && <Alert type="error" message="error" />
        }
        {
          !error &&
          <Table
            columns={[
              this.getCheckBoxColumn(),
              this.getPathColumn()
            ]}
            dataSource={this.folders}
            loading={pending}
            rowKey={folder => folder.path}
            size="small"
            pagination={false}
            showHeader={false}
          />
        }
      </Modal>
    );
  }
}

export default BucketBrowser;
