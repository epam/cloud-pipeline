import React from 'react';
import PropTypes from 'prop-types';
import Menu, {MenuItem} from 'rc-menu';
import {inject, observer} from 'mobx-react';
import {Button, Icon} from 'antd';
import Dropdown from 'rc-dropdown';
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

    const parameterTypeMenu = (
      <Menu selectedKeys={[]} onClick={onSelect} style={{cursor: 'pointer'}}>
        <MenuItem id="add-string-parameter" key="string">String parameter</MenuItem>
        <MenuItem id="add-boolean-parameter" key="boolean">Boolean parameter</MenuItem>
        <MenuItem id="add-path-parameter" key="path">Path parameter</MenuItem>
        <MenuItem id="add-input-parameter" key="input">Input path parameter</MenuItem>
        <MenuItem
          id="add-output-parameter"
          key="output"
          disabled={hasOutput}
        >
          <span
            className={classNames({'cp-text-not-important': hasOutput})}
          >
            Output path parameter
          </span>
        </MenuItem>
        <MenuItem id="add-common-parameter" key="common">Common path parameter</MenuItem>
        <MenuItem id="add-metadata-parameter" key="metadata">Metadata parameter</MenuItem>
      </Menu>
    );

    return (
      <Button.Group className={className} style={style}>
        <Button
          disabled={disabled}
          id="add-parameter-button"
          onClick={() => onAddParameter('string')}>
          Add parameter
        </Button>
        <Dropdown
          overlay={parameterTypeMenu}
          placement="bottomRight"
          trigger={disabled ? [] : ['hover']}
        >
          <Button
            disabled={disabled}
            id="add-parameter-dropdown-button"
            style={{padding: '0px 8px'}}
          >
            <Icon type="down" />
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
