import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import FetchPlugins from '../../../models/plugins/fetch-plugins';
import styles from './plugins.css';
import ConfigurePlugin from './configure-plugin';
import {Alert, Button} from 'antd';
import LoadingView from '../../special/LoadingView';

class ConfigurePluginsControl extends React.PureComponent {
  static newPluginId = 0;

  state = {
    pending: false,
    error: undefined,
    plugins: []
  };

  _pluginsLoadToken = {};

  componentDidMount () {
    const {configurations} = this.props;
    if (configurations?.length) {
      this.setConfigurationsFromProps();
    }
    this.loadPlugins();
  }

  componentDidUpdate (prevProps) {
    if (prevProps.updateTrigger !== this.props.updateTrigger) {
      this.loadPlugins();
    }
  }

  componentWillUnmount () {
    this.abortLoadPlugins();
  }

  abortLoadPlugins = () => {
    this._pluginsLoadToken = {};
  };

  loadPlugins = () => {
    this.abortLoadPlugins();
    const token = this._pluginsLoadToken = {};
    const commit = async (st, cb) => {
      if (token === this._pluginsLoadToken) {
        return new Promise((resolve) => {
          this.setState(st, () => {
            if (cb) {
              cb();
            }
            resolve();
          });
        });
      }
    };
    (async () => {
      try {
        await commit({pending: true, error: undefined});
        const req = new FetchPlugins();
        await req.fetch();
        if (req.error) {
          throw new Error(req.error);
        }
        const plugins = (req.value || []);
        await commit({pending: false, plugins});
      } catch (error) {
        await commit({pending: false, error: error.message});
      }
    })();
  };

  onChangePlugin = (plugin) => {
    const {
      plugins = [],
      onPluginsChange
    } = this.props;
    if (onPluginsChange) {
      const newPlugins = plugins.slice();
      const idx = newPlugins.findIndex((o) => o.id === plugin.id);
      if (idx >= 0) {
        newPlugins.splice(idx, 1, plugin);
      } else {
        newPlugins.push(plugin);
      }
      onPluginsChange(newPlugins);
    }
  };

  onRemovePlugin = (plugin) => {
    const {
      plugins = [],
      onPluginsChange
    } = this.props;
    if (onPluginsChange) {
      const newPlugins = plugins.slice();
      const idx = newPlugins.findIndex((o) => o.id === plugin.id);
      if (idx >= 0) {
        newPlugins.splice(idx, 1);
        onPluginsChange(newPlugins);
      }
    }
  };

  onAddPlugin = () => {
    const {
      pipelineId,
      pipelineVersion,
      toolId,
      toolVersion
    } = this.props;
    ConfigurePluginsControl.newPluginId -= 1;
    const id = ConfigurePluginsControl.newPluginId;
    const {plugins: available = []} = this.state;
    const anyPlugin = available[0];
    if (anyPlugin && (pipelineId || toolId)) {
      this.onChangePlugin({
        id,
        pipelineId,
        toolId,
        version: pipelineId ? pipelineVersion : toolId ? toolVersion : undefined,
        plugin: anyPlugin,
        sids: []
      });
    }
  };

  render () {
    const {
      className,
      style,
      disabled,
      plugins
    } = this.props;
    const {
      pending,
      error,
      plugins: availablePlugins
    } = this.state;
    return (
      <div className={classNames(className, styles.configurePluginsContainer)} style={style}>
        {
          !pending && error && (
            <div className={styles.error}>
              <Alert title={error} type="error" />
            </div>
          )
        }
        <div className={styles.actions}>
          <Button
            disabled={disabled || pending}
            size="small"
            onClick={this.onAddPlugin}
          >
            Assign plugin
          </Button>
        </div>
        {
          (pending && plugins.length === 0) && (
            <div className={styles.list}>
              <LoadingView />
            </div>
          )
        }
        {
          !(pending && plugins.length === 0) && (
            <div className={styles.list}>
              {
                plugins.map((plugin) => (
                  <ConfigurePlugin
                    className="cp-even-odd-element"
                    disabled={disabled || pending}
                    style={{padding: '5px 2px'}}
                    key={`${plugin.id}`}
                    plugin={plugin}
                    onChange={this.onChangePlugin}
                    onRemove={this.onRemovePlugin}
                    availablePlugins={availablePlugins}
                  />
                ))
              }
            </div>
          )
        }
      </div>
    );
  }
}

ConfigurePluginsControl.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  disabled: PropTypes.bool,
  plugins: PropTypes.oneOfType([PropTypes.array, PropTypes.object]),
  onPluginsChange: PropTypes.func,
  updateTrigger: PropTypes.any,
  pipelineId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  pipelineVersion: PropTypes.string,
  toolId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  toolVersion: PropTypes.string
};

export default ConfigurePluginsControl;
