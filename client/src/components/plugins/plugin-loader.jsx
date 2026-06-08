import React from 'react';
import PropTypes from 'prop-types';
import {loadPlugin, UI_PLUGIN_TYPE_LAUNCH_FORM} from './utilities';
import {API_PATH, SERVER} from '../../config';
import BucketBrowser from '../pipelines/launch/dialogs/BucketBrowser';
import LoadingView from '../special/LoadingView';
import {Alert} from 'antd';

class PluginLoader extends React.PureComponent {
  state = {
    render: undefined,
    plugin: undefined,
    error: undefined,
    pending: false,
    selectStorageItemsRequest: undefined
  };
  _pluginLoadToken = {};
  _pluginContainer = undefined;

  componentDidMount () {
    this.attachPlugin();
  }
  componentDidUpdate (prevProps, prevState, snapshot) {
    const {
      plugin: currentPlugin = {},
      pluginOptions: currentPluginOptions
    } = this.props;
    const {
      plugin: prevPlugin = {},
      pluginOptions: prevPluginOptions
    } = prevProps;
    const {
      path: currentPluginUri
    } = currentPlugin;
    const {
      path: prevPluginUri
    } = prevPlugin;
    if (currentPluginUri !== prevPluginUri) {
      this.attachPlugin();
    } else if (currentPluginOptions !== prevPluginOptions) {
      this.renderPlugin();
    }
  }
  componentWillUnmount () {
    this.detachPlugin();
    this._pluginContainer = undefined;
  }

  attachPlugin () {
    this.detachPlugin();
    const token = this._pluginLoadToken = {};
    const commitState = (state, cb) => {
      if (token === this._pluginLoadToken) {
        this.setState(state, cb);
      }
    };
    const {plugin = {}, type = UI_PLUGIN_TYPE_LAUNCH_FORM} = this.props;
    const {path} = plugin;
    if (path) {
      commitState({pending: true, error: undefined, render: undefined});
      (async () => {
        try {
          const render = await loadPlugin(plugin, type);
          commitState({render}, () => this.renderPlugin());
        } catch (e) {
          commitState({error: e.message, render: undefined});
        } finally {
          commitState({pending: false});
        }
      })();
    } else {
      commitState({pending: false, error: undefined, render: undefined});
    }
  }
  detachPlugin () {
    this._pluginLoadToken = {};
    const {plugin} = this.state;
    if (plugin) {
      plugin.destroy();
      this.setState({plugin: undefined});
    }
  }

  selectStorageItems = (opts) => {
    const {
      selection = [],
      bucketTypes,
      storageSelectionAvailable = false,
      onlyFolders = false,
      allowUpload = false,
      checkWritePermissions = false,
      multiple = true
    } = opts ?? {};
    return new Promise((resolve, reject) => {
      const onSelect = (path) => {
        if (path) {
          const items = path && path.length > 0
            ? path.split(',').map((o) => o.trim()).filter((o) => o.length > 0)
            : [];
          resolve(items);
          this.setState({selectStorageItemsRequest: undefined});
        }
      };
      const onCancel = () => {
        reject(new Error('cancelled'));
        this.setState({selectStorageItemsRequest: undefined});
      };
      this.setState({
        selectStorageItemsRequest: {
          selection: selection.join(','),
          multiple,
          bucketTypes,
          storageSelectionAvailable,
          onlyFolders,
          allowUpload,
          checkWritePermissions,
          onSelect,
          onCancel
        }
      });
    });
  };

  launch = async (payload) => {
    const {onLaunch} = this.props;
    if (typeof onLaunch === 'function') {
      await onLaunch(payload);
    }
  };

  onLaunched = (runId) => {
    const {onLaunched} = this.props;
    if (typeof onLaunched === 'function') {
      onLaunched(runId);
    }
  };

  initializeContainer = (container = this._pluginContainer) => {
    this._pluginContainer = container;
    this.renderPlugin();
  };

  renderPlugin = () => {
    const {render} = this.state;
    let {plugin} = this.state;
    if (this._pluginContainer && render) {
      const {pluginOptions} = this.props;
      if (plugin) {
        plugin.setOptions(pluginOptions);
      } else {
        plugin = render({
          container: this._pluginContainer,
          api: {
            url: SERVER + API_PATH,
            selectStorageItems: this.selectStorageItems,
            launch: this.launch,
            onLaunched: this.onLaunched
          },
          options: pluginOptions
        });
        this.setState({plugin});
      }
    }
  };

  render () {
    const {
      selectStorageItemsRequest,
      render,
      pending,
      error
    } = this.state;
    const {
      plugin
    } = this.props;
    const loadingView = plugin && plugin.name
      ? (
        <LoadingView>
          <div
            className="cp-text-not-important"
            style={{whiteSpace: 'pre', fontSize: 'larger'}}
          >
            <span>Loading <b>{plugin.name}</b> plugin...</span>
          </div>
        </LoadingView>
      )
      : <LoadingView />;
    return (
      <div style={{width: '100%', height: '100%', overflow: 'auto'}}>
        {
          render && (
            <div
              ref={this.initializeContainer}
              style={{width: '100%', height: '100%', overflow: 'auto'}}
            />
          )
        }
        {
          !render && pending && loadingView
        }
        {
          !render && !pending && error && (
            <Alert title={error} type="error" />
          )
        }
        <BucketBrowser
          multiple={selectStorageItemsRequest?.multiple}
          onSelect={selectStorageItemsRequest ? selectStorageItemsRequest.onSelect : () => {}}
          onCancel={selectStorageItemsRequest ? selectStorageItemsRequest.onCancel : () => {}}
          visible={Boolean(selectStorageItemsRequest)}
          uploadFilesAllowed={selectStorageItemsRequest?.allowUpload}
          path={selectStorageItemsRequest?.selection}
          showOnlyFolder={selectStorageItemsRequest?.onlyFolders}
          allowBucketSelection={selectStorageItemsRequest?.storageSelectionAvailable}
          checkWritePermissions={selectStorageItemsRequest?.checkWritePermissions}
          bucketTypes={selectStorageItemsRequest?.bucketTypes}
        />
      </div>
    );
  }
}

PluginLoader.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  plugin: PropTypes.object,
  type: PropTypes.string,
  pluginOptions: PropTypes.object,
  onLaunch: PropTypes.func,
  onLaunched: PropTypes.func
};

export default PluginLoader;
