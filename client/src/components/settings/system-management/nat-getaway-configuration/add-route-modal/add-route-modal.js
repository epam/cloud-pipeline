import React from 'react';
import {Modal, Input, Button, Form, message, Spin} from 'antd';

import {ResolveIp} from '../../../../../models/nat';
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
      errors: {},
      pending: false
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

    get portsDuplicates () {
      const portsObj = Object.values(this.state.form.ports)
        .reduce((r, c) => {
          r[c] = (r[c] || 0) + 1;
          return r;
        }, {});
      const duplicates = Object.entries(portsObj).filter(([key, value]) => value > 1);
      return Object.fromEntries(duplicates);
    }

    handleChange = (name) => (event) => {
      const {value} = event.target;
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
          }}, () => {
          this.setState({
            ...this.state,
            errors: {
              ...errors,
              [name]: validate(name, value, this.portsDuplicates)
            }
          });
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
        console.log(this.state.form);
        this.props.onAdd(this.state.form);
        this.resetForm();
      }
      this.cancelForm();
    }

    onResolveIP = async () => {
      const {serverName} = this.state.form;
      const request = new ResolveIp(serverName);
      this.setState({
        pending: true
      });
      await request.fetch();
      if (request.error) {
        message.error(request.error);
        this.setState({
          pending: false
        });
      }
      if (request.loaded && request.value && request.value.length) {
        this.setState({
          ...this.state,
          form: {
            ...this.state.form,
            ip: request.value[0]
          },
          pending: false
        });
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
            value={this.state.form.ports[name]}
            onChange={this.handleChange(name)} />
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
            <Spin spinning={this.state.pending}>
              <Form>
                <div className={styles.inputContainer}>
                  <span className={styles.inputLabel}>Server name:</span>
                  <FormItem
                    validateStatus={this.getValidationStatus('serverName')}
                    help={this.getValidationMessage('serverName')}
                  >
                    <Input
                      placeholder="Server name"
                      value={form.serverName}
                      onChange={this.handleChange('serverName')} />
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
                        placeholder="127.0.0.1"
                        value={form.ip}
                        onChange={this.handleChange('ip')} />
                    </FormItem>
                    <Button
                      disabled={!form.serverName || (this.getValidationStatus('serverName') === 'error')}
                      size="large"
                      onClick={this.onResolveIP}
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
            </Spin>
          </div>
        </Modal>
      );
    }
}
