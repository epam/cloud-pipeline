import React from 'react';
import PropTypes from 'prop-types';
import {
  inject,
  observer} from 'mobx-react';
import {Button,
  Dropdown
} from 'antd';
import {DownOutlined} from '@ant-design/icons';
import classNames from 'classnames';
import {addParameter, addSystemParameters} from '../utilities/parameter-utilities';
import {
  reservedParameters
} from '../utilities/parameters';
import SystemParametersBrowser from '../../dialogs/SystemParametersBrowser';
import {
  getSkippedParameters as getGPUScalingSkippedParameters
} from '../utilities/enable-gpu-scaling';

@inject('preferences')
@observer
class AddParameterButton extends React.Component {
  state = {
    systemParameterBrowserVisible: false
  };

  renderAddSystemParameterButton = () => {
    const {
      className,
      style,
      parameters,
      preferences,
      disabled,
      onChange
    } = this.props;
    const {systemParameterBrowserVisible: visible} = this.state;
    const onOpen = () => this.setState({systemParameterBrowserVisible: true});
    const onClose = () => this.setState({systemParameterBrowserVisible: false});
    const onSave = (systemParameters) => {
      const result = addSystemParameters(parameters, systemParameters);
      if (onChange) {
        onChange(result);
      }
      onClose();
    };
    const skipped = parameters.map((p) => p.name)
      .concat(reservedParameters)
      .concat(preferences.loaded ? getGPUScalingSkippedParameters(preferences) : []);
    return (
      <Button
        id="add-system-parameter-button"
        className={className}
        style={style}
        disabled={disabled}
        onClick={onOpen}
      >
        <span>Add system parameter</span>
        <SystemParametersBrowser
          visible={visible}
          onCancel={onClose}
          onSave={onSave}
          notToShow={skipped}
        />
      </Button>
    );
  };

  renderAddParameterButton = () => {
    const {
      className,
      style,
      parameters = [],
      onChange,
      disabled
    } = this.props;
    const hasOutput = parameters.some((p) => p.type.toLowerCase() === 'output');
    const onAddParameter = (type) => {
      const newParameters = addParameter(parameters, type);
      if (onChange) {
        onChange(newParameters);
      }
    };
    const onSelect = ({key}) => {
      onAddParameter(key);
    };

    const parameterTypeMenuItems = [
      {
        key: 'string',
        label: 'String parameter',
        id: 'add-string-parameter'
      }, {
        key: 'boolean',
        label: 'Boolean parameter',
        id: 'add-boolean-parameter'
      }, {
        key: 'path',
        label: 'Path parameter',
        id: 'add-path-parameter'
      }, {
        key: 'input',
        label: 'Input path parameter',
        id: 'add-input-parameter'
      }, {
        key: 'output',
        label: <span className={classNames({'cp-text-not-important': hasOutput})}>
          Output path parameter
        </span>,
        disabled: hasOutput,
        id: 'add-output-parameter'
      }, {
        key: 'common',
        label: 'Common path parameter',
        id: 'add-common-parameter'
      }, {
        key: 'metadata',
        label: 'Metadata parameter',
        id: 'add-metadata-parameter'
      }
    ];

    return (
      <Button.Group className={className} style={style}>
        <Button
          disabled={disabled}
          id="add-parameter-button"
          onClick={() => onAddParameter('string')}>
          Add parameter
        </Button>
        <Dropdown
          menu={{items: parameterTypeMenuItems, onClick: onSelect}}
          placement="bottomRight"
          trigger={disabled ? [] : ['hover']}
        >
          <Button
            disabled={disabled}
            id="add-parameter-dropdown-button"
            style={{padding: '0px 8px'}}
          >
            <DownOutlined />
          </Button>
        </Dropdown>
      </Button.Group>
    );
  };
  render () {
    if (this.props.system) {
      return this.renderAddSystemParameterButton();
    }
    return this.renderAddParameterButton();
  }
}

AddParameterButton.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  disabled: PropTypes.bool,
  system: PropTypes.bool,
  parameters: PropTypes.oneOfType([PropTypes.array, PropTypes.object]),
  onChange: PropTypes.func
};

export default AddParameterButton;
