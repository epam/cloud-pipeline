import React from 'react';
import PropTypes from 'prop-types';
import {Col, Input, Row} from 'antd';
import classNames from 'classnames';
import {
  ContextualPreferenceLoad,
  ContextualPreferenceUpdate,
} from '../../../../models/utils/ContextualPreference';
import CodeEditorFormItem from '../../../special/CodeEditorFormItem';

export async function fetchContextualSettingValue (setting, regionId) {
  try {
    const request = new ContextualPreferenceLoad('REGION', setting, regionId);
    await request.fetch();
    if (request.error) {
      throw new Error(request.error);
    }
    const {value} = request;
    if (value) {
      return value.value;
    }
    return undefined;
  } catch (error) {
    console.warn(`Error fetching preference "${setting}" for region ${regionId}: ${error.message}`);
  }
  return undefined;
}

export async function updateContextualSettingValue (setting, value, type, regionId) {
  try {
    const request = new ContextualPreferenceUpdate();
    await request.send({
      name: setting,
      value: value,
      type,
      resource: {
        level: 'REGION',
        resourceId: regionId
      }
    });
    if (request.error) {
      throw new Error(request.error);
    }
  } catch (error) {
    console.warn(`Error updating preference "${setting}" for region ${regionId}: ${error.message}`);
  }
}

const launchCommonMountsSetting = {
  setting: 'launch.common.mounts',
  title: 'Container host mounts',
  type: 'OBJECT'
};

export {launchCommonMountsSetting};

function isValidJson (aValue) {
  if (!aValue || aValue.trim().length === 0) {
    return true;
  }
  try {
    JSON.parse(aValue);
    return true;
  } catch {
    return false;
  }
}

class CloudRegionContextualSetting extends React.Component {
  token = {};
  state = {
    value: undefined,
    valid: true
  };

  componentDidMount () {
    this.rebuild();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (this.props.value !== prevProps.value || this.props.type !== prevProps.type) {
      this.rebuild();
    }
  }

  rebuild = () => {
    const {value, type} = this.props;
    if (this.state.value !== value) {
      let valid = true;
      if (/^object$/i.test(type)) {
        valid = isValidJson(value);
      }
      this.setState({
        value,
        valid
      }, this.report);
    }
  };

  report = () => {
    const {
      valid,
      value
    } = this.state;
    const {
      onChange,
      setting
    } = this.props;
    if (typeof onChange === 'function') {
      onChange(setting, value, valid);
    }
  };

  renderAsString = () => {
    const {
      value,
      valid
    } = this.state;
    const {
      disabled
    } = this.props;
    const onChange = (e) => {
      this.setState({
        value: e.target.value
      }, this.report);
    };
    return (
      <Input
        disabled={disabled}
        className={classNames({
          'error': !valid
        })}
        value={value}
        onChange={onChange}
        style={{width: '100%'}}
        size="small"
      />
    );
  };

  renderAsJson = () => {
    const {
      value,
      valid
    } = this.state;
    const {
      disabled
    } = this.props;
    const onChange = (newValue) => {
      this.setState({
        value: newValue,
        valid: isValidJson(newValue)
      }, this.report);
    };
    return (
      <CodeEditorFormItem
        disabled={disabled}
        value={value}
        onChange={onChange}
        editorLanguage="application/json"
        editorClassName={classNames({
          'has-error': !valid
        })}
      />
    );
  };

  renderValue = () => {
    const {
      type
    } = this.props;
    if (/^object$/i.test(type)) {
      return this.renderAsJson();
    }
    return this.renderAsString;
  };

  render () {
    const {setting, className, style, title, labelCol = {}, wrapperCol = {}} = this.props;
    return (
      <Row className={className} style={style}>
        <Col
          className="cp-settings-form-item-label"
          style={{textAlign: 'right', paddingRight: 6, paddingTop: 2}}
          {...labelCol}
        >
          {title || setting}
        </Col>
        <Col
          {...wrapperCol}
          style={{paddingLeft: 2}}
        >
          {this.renderValue()}
        </Col>
      </Row>
    );
  }
}

CloudRegionContextualSetting.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  disabled: PropTypes.bool,
  regionId: PropTypes.number,
  setting: PropTypes.string,
  value: PropTypes.string,
  onChange: PropTypes.func,
  labelCol: PropTypes.oneOfType(PropTypes.object, PropTypes.number),
  wrapperCol: PropTypes.oneOfType(PropTypes.object, PropTypes.number),
  title: PropTypes.node,
  type: PropTypes.oneOf(['OBJECT']),
  resetToken: PropTypes.object
};

export default CloudRegionContextualSetting;
