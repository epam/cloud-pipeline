import React from 'react';
import PropTypes from 'prop-types';
import {
  fetchAvailablePlugins,
  pluginAssignmentsArraysAreEqual,
  updatePluginsAssignments,
} from '../utilities';
import {Alert, Modal, message, Button} from 'antd';
import {SettingOutlined} from '@ant-design/icons';
import ConfigurePluginsControl from './configure-plugins-control';
import classNames from 'classnames';

class ConfigurePlugins extends React.PureComponent {
  state = {
    plugins: [],
    initial: [],
    pending: false,
    error: undefined,
    visible: false,
    updateTrigger: {},
  };

  _token = {};

  get modified() {
    const {plugins, initial} = this.state;
    return !pluginAssignmentsArraysAreEqual(initial, plugins);
  }

  componentDidMount() {
    this.loadPlugins();
  }

  componentDidUpdate(prevProps, prevState, snapshot) {
    const {mode = 'default'} = this.props;
    if (
      prevProps.pipelineId !== this.props.pipelineId ||
      prevProps.pipelineVersion !== this.props.pipelineVersion ||
      prevProps.toolId !== this.props.toolId ||
      prevProps.toolVersion !== this.props.toolVersion ||
      (mode.toLowerCase() === 'modal' &&
        prevProps.visible !== this.props.visible &&
        this.props.visible) ||
      (mode.toLowerCase() !== 'modal' &&
        prevState.visible !== this.state.visible &&
        this.state.visible)
    ) {
      this.loadPlugins();
    }
  }

  componentWillUnmount() {
    this.abort();
  }

  abort = () => {
    this._token = {};
  };

  loadPlugins = () => {
    const token = (this._token = {});
    const commit = async (st, cb) => {
      if (token === this._token) {
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
        const {pipelineId, pipelineVersion, toolId, toolVersion} = this.props;
        const plugins = await fetchAvailablePlugins({
          pipelineId,
          pipelineVersion,
          toolId,
          toolVersion,
        });
        await commit({pending: false, plugins, initial: plugins.slice()});
      } catch (error) {
        await commit({pending: false, error: error.message});
      }
    })();
  };

  onChange = (newPlugins) => {
    this.setState({plugins: newPlugins});
  };

  onSubmit = () => {
    const {plugins, initial} = this.state;
    (async () => {
      const hide = message.loading('Updating plugins...', -1);
      try {
        await updatePluginsAssignments(plugins, initial);
        this.loadPlugins();
        this.onClose();
      } catch (error) {
        message.error(
          <div>
            <b style={{marginRight: 5}}>Error updating plugins:</b>
            <span>{error.message}</span>
          </div>,
          5,
        );
      } finally {
        hide();
      }
    })();
  };

  onOpen = () => {
    this.setState({visible: true, updateTrigger: {}});
  };

  onClose = () => {
    const {onClose} = this.props;
    const {initial = []} = this.state;
    this.setState({plugins: initial.slice(), visible: false});
    if (onClose) {
      onClose();
    }
  };

  render() {
    const {
      className,
      style,
      disabled: propsDisabled,
      visible,
      pipelineId,
      pipelineVersion,
      toolId,
      toolVersion,
      mode = 'default',
    } = this.props;
    const disabled =
      propsDisabled ||
      (pipelineId && !pipelineVersion) ||
      (toolId && !toolVersion) ||
      (!pipelineId && !toolId);
    const {
      pending,
      error,
      plugins,
      visible: stateVisible,
      updateTrigger: stateUpdateTrigger,
    } = this.state;
    const {modified} = this;
    const modalVisible = mode.toLowerCase() === 'modal' ? visible : stateVisible;
    const controlTrigger = mode.toLowerCase() === 'modal' ? visible : stateUpdateTrigger;
    const generalError = (() => {
      if (pipelineId && !pipelineVersion) {
        return 'Pipeline version is not specified';
      }
      if (toolId && !toolVersion) {
        return 'Tool version is not specified';
      }
      if (!pipelineId && !toolId) {
        return 'Pipeline or tool is not specified';
      }
      return undefined;
    })();
    const content = (
      <div>
        {error && <Alert title={error} type="error" />}
        {generalError && <Alert title={generalError} type="error" />}
        <ConfigurePluginsControl
          disabled={pending || disabled}
          plugins={plugins}
          onPluginsChange={this.onChange}
          pipelineId={pipelineId}
          pipelineVersion={pipelineVersion}
          toolId={toolId}
          toolVersion={toolVersion}
          updateTrigger={controlTrigger}
        />
      </div>
    );
    const footer = (
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'flex-end',
        }}
      >
        {mode.toLowerCase() === 'default' && (
          <Button onClick={this.onClose} style={{marginRight: 5}} disabled={!modified}>
            Revert
          </Button>
        )}
        {mode.toLowerCase() !== 'default' && (
          <Button onClick={this.onClose} style={{marginRight: 5}}>
            Cancel
          </Button>
        )}
        <Button disabled={disabled || pending || !modified} onClick={this.onSubmit} type="primary">
          Save
        </Button>
      </div>
    );
    const modal = (
      <Modal
        title="Configure UI plugins"
        open={modalVisible}
        onCancel={this.onClose}
        footer={footer}
        width="620px"
      >
        {content}
      </Modal>
    );
    if (mode.toLowerCase() === 'button') {
      const linkText = plugins.length
        ? `${plugins.length} plugin${plugins.length > 1 ? 's' : ''} applied`
        : 'Configure plugins';
      return (
        <div
          className={className}
          style={Object.assign(
            {
              width: 'fit-content',
            },
            style,
          )}
        >
          {propsDisabled ? (
            <span>
              <SettingOutlined />
              <span style={{margin: '0 5px'}}>{linkText}</span>
            </span>
          ) : (
            <a
              onClick={this.onOpen}
              className={classNames('cp-text', 'underline')}
              style={{textDecoration: 'underline'}}
            >
              <SettingOutlined />
              <span style={{margin: '0 5px'}}>{linkText}</span>
            </a>
          )}
          {modal}
        </div>
      );
    }
    if (mode.toLowerCase() === 'modal') {
      return modal;
    }
    return (
      <div className={className} style={style}>
        {content}
        {footer}
      </div>
    );
  }
}

ConfigurePlugins.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  disabled: PropTypes.bool,
  visible: PropTypes.bool,
  onClose: PropTypes.func,
  pipelineId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  pipelineVersion: PropTypes.string,
  toolId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  toolVersion: PropTypes.string,
  mode: PropTypes.oneOf(['default', 'button', 'modal']),
};

ConfigurePlugins.defaultProps = {
  mode: 'button',
};

export default ConfigurePlugins;
