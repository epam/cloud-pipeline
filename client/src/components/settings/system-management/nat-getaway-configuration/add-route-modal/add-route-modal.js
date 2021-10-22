import React from 'react';
import {Modal, Input, Button, Form} from 'antd';

import {validate} from '../helpers';
import styles from './add-route-modal.css';

const FormItem = Form.Item;
export default class AddRouteForm extends React.Component {
    state={
      formData: {
        ports: {'port': undefined},
        ip: undefined,
        serverName: undefined
      },
      formErrors: {}
    }

    get ports () {
      return Object.entries(this.state.formData.ports);
    }

    get formIsInvalid () {
      const {formData} = this.state;
      const ports = formData.ports || {};
      const flattenFormData = {
        ip: formData.ip,
        serverName: formData.serverName,
        ...ports};
      return !!Object.entries(this.state.formErrors)
        .map(([key, status]) => (status && status.error) || false)
        .filter(error => error)
        .length || Object.values(flattenFormData).includes(undefined);
    }
    handleChange = (event) => {
      const {name, value} = event.target;
      const {formData, formErrors} = this.state;

      if (!name.startsWith('port')) {
        this.setState({
          formData: {
            ...formData,
            [name]: value
          },
          formErrors: {
            ...formErrors,
            [name]: validate(name, value)
          }
        });
      } else {
        const ports = {...formData.ports};
        ports[name] = value;
        this.setState({
          formData: {
            ...formData,
            ports: ports
          },
          formErrors: {
            ...formErrors,
            [name]: validate(name, value)
          }
        });
      }
    }
    addPortInput = () => {
      this.setState({
        formData: {
          ...this.state.formData,
          ports: {
            ...this.state.formData.ports,
            [`port${this.ports.length - 1}`]: undefined
          }
        }
      });
    }

    getValidationStatus = (key) => {
      return this.state.formErrors[key] && this.state.formErrors[key].error ? 'error' : 'success';
    }
    getValidationMessage = (key) => {
      return (this.state.formErrors[key] && this.state.formErrors[key].message) || '';
    }

    onSubmit = async () => {
      if (!this.formIsInvalid) {
        this.props.onAdd(this.state.formData);
      }
    }
    removePortInput = (portName) => {
      const ports = {...this.state.formData.ports};
      const formErrors = {...this.state.formErrors};
      delete ports[portName];
      delete formErrors[portName];
      this.setState({
        formData: {
          ...this.state.formData,
          ports: ports
        },
        formErrors: formErrors
      });
    }

    render () {
      const {onCancel, visible} = this.props;
      const {formData} = this.state;
      return (
        <Modal
          visible={visible}
          title="Add new route"
          onCancel={onCancel}
          footer={false}
        >
          <div className={styles.modalContent}>
            <Form>
              <div className={styles.inputContainer}>
                <span className={styles.inputLabel}>Server name:</span>
                <FormItem
                  required
                  validateStatus={this.getValidationStatus('serverName')}
                  help={this.getValidationMessage('serverName')}
                >
                  <Input
                    name="serverName"
                    placeholder="Server name"
                    value={formData.serverName}
                    onChange={this.handleChange} />
                </FormItem>
              </div>
              <div className={styles.inputContainer}>
                <span className={styles.inputLabel}>IP:</span>
                <FormItem
                  validateStatus={this.getValidationStatus('ip')}
                  help={this.getValidationMessage('ip')}
                >
                  <Input
                    name="ip"
                    placeholder="181.161.0.1"
                    value={formData.ip}
                    onChange={this.handleChange} />
                </FormItem>
              </div>
              <div className={styles.inputContainer}>
                <span className={styles.inputLabel}>PORT:</span>
                {
                  this.ports.map(([portName, _value], index) => (
                    <div className={styles.portContainer} key={portName}>
                      <FormItem
                        required
                        className={styles.formItemPort}
                        validateStatus={this.getValidationStatus(portName)}
                        help={this.getValidationMessage(portName)}
                      >
                        <Input
                          name={portName}
                          placeholder="3000"
                          value={formData.ports[portName]}
                          onChange={this.handleChange} />
                      </FormItem>
                      <Button
                        type={index === 0 ? 'primary' : 'default'}
                        icon={index === 0 ? 'plus' : 'delete'}
                        onClick={index === 0 ? this.addPortInput : () => this.removePortInput(portName)}
                      />
                    </div>)
                  )}
              </div>
              <Button
                type="primary"
                disabled={this.formIsInvalid}
                onClick={this.onSubmit}
              >
                ADD
              </Button>
            </Form>
          </div>
        </Modal>
      );
    }
}
