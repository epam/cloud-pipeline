import React from 'react';
import {Modal, Input, Button, Form} from 'antd';

import {validate} from '../helpers';
import styles from './add-route-modal.css';

const FormItem = Form.Item;
export default class AddRouteForm extends React.Component {
    state={
      form: {
        ports: {'port': undefined},
        ip: undefined,
        serverName: undefined
      },
      errors: {}
    }

    get ports () {
      return Object.entries(this.state.form.ports);
    }

    get formIsInvalid () {
      const {form, errors} = this.state;
      const ports = form.ports || {};
      const flattenform = {
        ip: form.ip,
        serverName: form.serverName,
        ...ports};
      return !!Object.entries(errors)
        .map(([field, status]) => (status && status.error) || false)
        .filter(error => error)
        .length || Object.values(flattenform).includes(undefined);
    }

    handleChange = (event) => {
      const {name, value} = event.target;
      const {form, errors} = this.state;

      if (!name.startsWith('port')) {
        this.setState({
          form: {
            ...form,
            [name]: value
          },
          errors: {
            ...errors,
            [name]: validate(name, value)
          }
        });
      } else {
        this.setState({
          form: {
            ...form,
            ports: {
              ...form.ports,
              [name]: value
            }
          },
          errors: {
            ...errors,
            [name]: validate(name, value)
          }
        });
      }
    }

    addPortInput = () => {
      this.setState({
        form: {
          ...this.state.form,
          ports: {
            ...this.state.form.ports,
            [`port${this.ports.length - 1}${Math.ceil(Math.random() * 100)}`]: undefined
          }
        }
      });
    }

    removePortInput = (name) => {
      const {form, errors} = this.state;
      const ports = {...form.ports};
      delete ports[name];
      delete errors[name];
      this.setState({
        form: {
          ...form,
          ports: ports
        },
        errors: errors
      });
    }

    resetForm = () => {
      this.setState({
        form: {
          ports: {'port': undefined},
          ip: undefined,
          serverName: undefined
        },
        errors: {}
      });
    }

    cancelForm = () => {
      this.props.onCancel();
      this.resetForm();
    }

    getValidationStatus = (key) => {
      const {errors} = this.state;
      return errors[key] && errors[key].error ? 'error' : 'success';
    }
    getValidationMessage = (key) => {
      const {errors} = this.state;
      return (errors[key] && errors[key].message) || '';
    }

    onSubmit = async () => {
      if (!this.formIsInvalid) {
        this.props.onAdd(this.state.form);
        this.resetForm();
      }
    }

    renderFooter = () => (
      <div className={styles.footerButtonsContainer}>
        <Button
          onClick={this.cancelForm}
        >
          CANCEL
        </Button>
        <Button
          type="primary"
          disabled={this.formIsInvalid}
          onClick={this.onSubmit}
        >
          ADD
        </Button>
      </div>
    );

    renderPorts = () => this.ports.map(([name, _value]) => (
      <div className={styles.portContainer} key={name}>
        <FormItem
          className={styles.formItemPort}
          validateStatus={this.getValidationStatus(name)}
          help={this.getValidationMessage(name)}
        >
          <Input
            name={name}
            value={this.state.form.ports[name]}
            onChange={this.handleChange} />
        </FormItem>
        {this.ports.length > 1 && <Button
          type="danger"
          icon="delete"
          onClick={() => this.removePortInput(name)}
        />}
      </div>)
    );

    render () {
      const {onCancel, visible} = this.props;
      const {form} = this.state;

      return (
        <Modal
          title="Add new route"
          visible={visible}
          onCancel={onCancel}
          footer={this.renderFooter()}
        >
          <div className={styles.modalContent}>
            <Form>
              <div className={styles.inputContainer}>
                <span className={styles.inputLabel}>Server name:</span>
                <FormItem
                  validateStatus={this.getValidationStatus('serverName')}
                  help={this.getValidationMessage('serverName')}
                >
                  <Input
                    name="serverName"
                    placeholder="Server name"
                    value={form.serverName}
                    onChange={this.handleChange} />
                </FormItem>
              </div>
              <div className={styles.inputContainer}>
                <span className={styles.inputLabel}>IP:</span>
                <div className={styles.ipContainer}>
                  <FormItem
                    validateStatus={this.getValidationStatus('ip')}
                    help={this.getValidationMessage('ip')}
                  >
                    <Input
                      name="ip"
                      placeholder="127.0.0.1"
                      value={form.ip}
                      onChange={this.handleChange} />
                  </FormItem>
                  <Button
                    disabled={!form.ip || (this.getValidationStatus('ip') === 'error')}
                    size="large"
                  >
                    Resolve
                  </Button>
                </div>
              </div>
              <div className={styles.inputContainer}>
                <span className={styles.inputLabel}>PORT:</span>
                {this.renderPorts()}
              </div>
              <div className={styles.addButtonContainer}>
                <Button
                  icon="plus"
                  onClick={this.addPortInput}
                >
                  Add
                </Button>
              </div>
            </Form>
          </div>
        </Modal>
      );
    }
}
