import React from 'react';
import classNames from 'classnames';
import PropTypes from 'prop-types';
import {Modal} from 'antd';
import LaunchFormSchemeParameterTable from './scheme-parameter-table';
import mainStyles from '../launch-form-parameter-input.css';
import styles from './scheme-parameter-input.css';
import ParameterValueRepresentation from '../../representation';

class LaunchFormSchemeParameterInput extends React.Component {
  state = {
    modalVisible: false
  };

  onOpenModal = () => this.setState({modalVisible: true});
  onCloseModal = () => this.setState({modalVisible: false});

  onChange = (newValue) => {
    const {onChange} = this.props;
    if (onChange) {
      onChange(newValue);
    }
    this.onCloseModal();
  }

  render () {
    const {
      className,
      style,
      disabled,
      onChange,
      ...rest
    } = this.props;
    const {parameter} = rest;
    const {
      name,
      config = {},
      value,
      valid
    } = parameter || {};
    const {
      prettyName = name,
      scheme = {}
    } = config || {};
    const {
      properties = []
    } = scheme;
    const propsCount = (properties || []).length;
    const modalWidth = Math.min(95, Math.max(50, propsCount * 15));
    const {modalVisible} = this.state;
    return (
      <div
        className={classNames(className, mainStyles.launchParameterInput)}
        style={style}
      >
        <div
          onClick={this.onOpenModal}
          className={
            classNames(
              styles.schemeParameterInput,
              {
                disabled,
                'cp-limit-mounts-input': valid,
                'cp-error border': !valid
              }
            )
          }
          style={{padding: '0 5px'}}
        >
          <ParameterValueRepresentation
            value={value}
            missingLabel={(<i className="cp-text-not-important">Empty value</i>)}
          />
        </div>
        <Modal
          title={(<b>{prettyName}</b>)}
          visible={modalVisible}
          footer={false}
          width={`${modalWidth}%`}
          closable={false}
          bodyStyle={{
            margin: 0,
            padding: '2px 10px 10px'
          }}
        >
          <LaunchFormSchemeParameterTable
            {...rest}
            disabled={disabled}
            onChange={this.onChange}
            onCancel={this.onCloseModal}
          />
        </Modal>
      </div>
    );
  }
}

LaunchFormSchemeParameterInput.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  parameter: PropTypes.object,
  value: PropTypes.any,
  required: PropTypes.bool,
  onChange: PropTypes.func,
  disabled: PropTypes.bool,
  currentProjectId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  currentProjectMetadata: PropTypes.object,
  currentMetadataEntity: PropTypes.oneOfType([PropTypes.array, PropTypes.object]),
  rootEntityId: PropTypes.string,
  metadataAutoComplete: PropTypes.bool
};

export default LaunchFormSchemeParameterInput;
