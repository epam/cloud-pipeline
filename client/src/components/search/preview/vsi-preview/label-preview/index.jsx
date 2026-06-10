import React, {Component} from 'react';
import PropTypes from 'prop-types';
import {inject, observer} from 'mobx-react';
import {createObjectStorageWrapper} from '../../../../../utils/object-storage';
import {Alert} from 'antd';
import {LoadingOutlined} from '@ant-design/icons';

@inject('dataStorages')
@observer
class LabelPreview extends Component {
  state = {
    url: undefined,
    pending: false,
    error: undefined,
  };

  componentDidMount() {
    this.refreshUrl();
  }

  componentDidUpdate(prevProps) {
    if (
      this.props.storageId !== prevProps.storageId ||
      this.props.storagePath !== prevProps.storagePath
    ) {
      this.refreshUrl();
    }
  }

  componentWillUnmount() {
    this._token = {};
  }

  refreshUrl = () => {
    const {storageId, storagePath, dataStorages} = this.props;
    const token = (this._token = {});
    const commit = async (st) => {
      if (token === this._token) {
        return new Promise((resolve) => {
          this.setState(st, () => resolve());
        });
      }
      return Promise.resolve();
    };
    (async () => {
      if (storageId && storagePath) {
        try {
          await commit({pending: true});
          await dataStorages.fetchIfNeededOrWait();
          const storages = dataStorages.value || [];
          const os = await createObjectStorageWrapper(storages, Number(storageId));
          if (!os) {
            throw new Error(
              `Data storage #${storageId} not found ` +
                `for wsi label "${storagePath}". Using current storage`,
            );
          }
          const url = await os.generateFileUrl(storagePath);
          await commit({url});
        } catch (e) {
          console.error('Error fetching wsi label url:', e.message);
          await commit({error: e.message});
        } finally {
          await commit({pending: false});
        }
      } else {
        await commit({pending: false, url: undefined, error: undefined});
      }
    })();
  };

  render() {
    const {className, style} = this.props;
    const {url, pending, error} = this.state;
    return (
      <div className={className} style={style}>
        {url && <img src={url} style={{width: '100%'}} alt="Label" />}
        {!url && pending && (
          <div style={{display: 'flex', alignItems: 'center'}} className="cp-text-not-important">
            <LoadingOutlined style={{marginRight: 5}} />
            <span>Loading label...</span>
          </div>
        )}
        {!url && !pending && error && (
          <Alert title={<div>Error loading label: {error}</div>} type="error" />
        )}
      </div>
    );
  }
}

LabelPreview.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  storageId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  storagePath: PropTypes.string,
};

export default LabelPreview;
