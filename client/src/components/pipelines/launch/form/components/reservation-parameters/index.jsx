import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import styles from './reservation-parameters.css';
import {
  InputNumber,
  Modal,
  Select,
  Slider
} from 'antd';
import {CheckCircleFilled, CloseCircleFilled, ExclamationCircleFilled, LoadingOutlined} from '@ant-design/icons';
import {
  getReservationParametersConfig,
  correctReservationParameters,
  DEFAULT_RAM_REQUESTS_STEP,
  DEFAULT_RAM_REQUESTS_UNIT,
  getInstanceResourcesRestrictions,
  parseRAMRequest,
  transformBytesToK8sRAMRequest,
  getInstanceResources, getInstanceResourcesAvailability
} from './utilities';

class ReservationParameters extends React.PureComponent {
  state = {
    parameters: {},
    config: undefined,
    resources: [],
    resourcesPending: false,
    resourcesError: undefined,
    instanceType: undefined,
    resourcesDetailsVisible: false
  };

  componentDidMount () {
    this.updateConfig();
    this.updateFromProps();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    const {
      parameters: prev = {},
      instanceType: prevInstanceType = {}
    } = prevProps;
    const {
      parameters: curr = {},
      instanceType: currInstanceType = {}
    } = this.props;
    const {
      gpu: prevGpu,
      cpu: prevCpu,
      ram: prevRam
    } = prev;
    const {
      gpu: currGpu,
      cpu: currCpu,
      ram: currRam
    } = curr;
    if (
      prevGpu !== currGpu ||
      prevCpu !== currCpu ||
      prevRam !== currRam
    ) {
      this.updateFromProps();
    }
    const {
      name: prevInstanceTypeName
    } = prevInstanceType;
    const {
      name: currInstanceTypeName
    } = currInstanceType;
    if (
      prevInstanceTypeName !== currInstanceTypeName
    ) {
      this.updateConfig();
    }
  }

  componentWillUnmount () {
    this.token = undefined;
    this.refreshResourcesToken = undefined;
  }

  updateConfig = () => {
    const {
      instanceType: instanceTypeObj = {}
    } = this.props;
    const {
      name: instanceType
    } = instanceTypeObj;
    const token = this.token = {};
    this.refreshResourcesToken = {};
    (async () => {
      try {
        this.setState({
          instanceType: undefined,
          config: undefined,
          resources: [],
          resourcesPending: true,
          resourcesError: undefined,
          resourcesDetailsVisible: false
        });
        const config = await getReservationParametersConfig(instanceType);
        if (this.token === token) {
          const {
            parameters = {}
          } = this.state;
          const {
            cpu: pCpu = 1,
            ram: pRam = 1,
            gpu: pGpu = 1
          } = correctReservationParameters(parameters, {
            config,
            instanceType
          });
          this.setState({
            config,
            instanceType: {
              name: instanceType,
              ...getInstanceResourcesRestrictions({
                config,
                instanceType: instanceTypeObj
              })
            },
            parameters: {
              cpu: pCpu,
              gpu: pGpu,
              ram: pRam
            }
          }, () => {
            this.refreshResources();
            this.reportChange();
          });
        }
      } catch (e) {
        console.error('error reading reservation parameters configuration', e);
      }
    })();
  };

  refreshResources = () => {
    const token = this.refreshResourcesToken = {};
    const {
      config
    } = this.state;
    if (config) {
      (async () => {
        const commit = (st) => {
          if (token === this.refreshResourcesToken) {
            this.setState(st);
          }
        };
        commit({
          resourcesPending: true,
          resourcesError: undefined,
          resourcesDetailsVisible: false
        });
        try {
          const resources = await getInstanceResources(config);
          commit({
            resources,
            resourcesPending: false,
            resourcesError: undefined
          });
        } catch (error) {
          commit({
            resources: [],
            resourcesPending: false,
            resourcesError: `Error fetching available resources: ${error.message}`
          });
        }
      })();
    } else {
      this.setState({
        resources: [],
        resourcesPending: false,
        resourcesError: undefined,
        resourcesDetailsVisible: false
      });
    }
  };

  updateFromProps = () => {
    const {
      parameters = {}
    } = this.props;
    const {
      gpu = 1,
      cpu = 1,
      ram = 1
    } = parameters;
    this.setState({
      parameters: {
        gpu,
        cpu,
        ram
      }
    });
  };

  reportChange = () => {
    const {
      onChange
    } = this.props;
    const {
      parameters = {}
    } = this.state;
    if (onChange) {
      onChange(parameters);
    }
  };

  renderRequestsSelector = (key, options = {}) => {
    const {
      config,
      instanceType,
      parameters = {}
    } = this.state;
    if (!config || !instanceType) {
      return null;
    }
    const {
      enabledKey = `${key}_requests_enabled`,
      step = 1,
      formatter = o => String(o),
      title = `${key.toUpperCase()} request`,
      slider: sliderRaw = false,
      inputValueConverter = o => o,
      inputValueFormatter = o => o
    } = options || {};
    const {
      [enabledKey]: enabled = false
    } = config;
    const {
      [key]: value = 1
    } = parameters;
    const onChange = (val) => {
      const n = Number(val);
      if (!Number.isNaN(n)) {
        this.setState({
          parameters: {
            ...parameters,
            [key]: n
          }
        }, this.reportChange);
      }
    };
    const onChangeInput = (val) => {
      const n = Number(val);
      if (!Number.isNaN(n) && !/[.,]$/.test(val)) {
        this.setState({
          parameters: {
            ...parameters,
            [key]: inputValueFormatter(n)
          }
        }, this.reportChange);
      }
    };
    const {[key]: range} = instanceType;
    const [, max = 1] = range || [];
    const maxInput = parseFloat(inputValueConverter(max));
    let slider = sliderRaw;
    const values = [];
    if (!slider && step > 0) {
      let v = step;
      while (v <= max) {
        values.push(v);
        v += step;
      }
      if (values.length >= 100) {
        slider = true;
      }
    }
    if (enabled && max > 0) {
      const component = (() => {
        if (slider) {
          const onKeyPress = (evt) => {
            if (/^enter$/i.test(evt.key)) {
              evt.preventDefault();
              evt.stopPropagation();
            }
          };
          return (
            <div
              className={styles.reservationParameterInput}
              style={{
                display: 'inline-flex',
                alignItems: 'center'
              }}
            >
              <Slider
                style={{flex: 1, marginRight: 5}}
                min={step}
                max={Math.floor(max / step) * step}
                value={value}
                onChange={onChange}
                tipFormatter={formatter}
                step={step}
              />
              <InputNumber
                style={{flexShrink: 0, width: 100, marginRight: 0}}
                value={parseFloat(inputValueConverter(value))}
                min={1}
                max={maxInput}
                onChange={onChangeInput}
                onKeyDown={onKeyPress}
              />
            </div>
          );
        }
        return (
          <Select
            className={styles.reservationParameterInput}
            value={String(value)}
            onChange={onChange}
          >
            {
              values.map((value) => (
                <Select.Option key={`${key}-${value}`} value={String(value)}>
                  {value > 0 ? formatter(value) : 'Not set'}
                </Select.Option>
              ))
            }
          </Select>
        );
      })();
      return (
        <div key={key} className={styles.reservationParameterRow}>
          <span className={styles.reservationParameterLabel}>
            {title}:
          </span>
          {component}
        </div>
      );
    }
    return null;
  };

  renderCpuRequests = () => this.renderRequestsSelector(
    'cpu',
    {
      reserved: 1
    }
  );

  renderRamRequests = () => {
    const {
      config,
      instanceType
    } = this.state;
    if (!config || !instanceType) {
      return null;
    }
    const {
      ram_requests_unit: ramRequestsUnit = DEFAULT_RAM_REQUESTS_UNIT,
      ram_requests_step: ramRequestsStep = DEFAULT_RAM_REQUESTS_STEP
    } = config;
    const step = parseRAMRequest(ramRequestsStep, ramRequestsUnit);
    const decimal = !ramRequestsUnit.includes('i');
    return this.renderRequestsSelector(
      'ram',
      {
        slider: true,
        title: `RAM request (${ramRequestsUnit})`,
        reserved: 1,
        step,
        inputValueConverter: o => transformBytesToK8sRAMRequest(
          o,
          {unit: ramRequestsUnit}
        ),
        inputValueFormatter: o => parseRAMRequest(o, ramRequestsUnit),
        formatter: o => transformBytesToK8sRAMRequest(
          o,
          {appendBytesLetter: true, decimal}
        )
      });
  }

  renderGpuRequests = () => this.renderRequestsSelector(
    'gpu',
    {
      reserved: 0
    }
  );

  renderResourcesAvailability = () => {
    const {
      config,
      resources,
      parameters,
      resourcesPending,
      resourcesError,
      resourcesDetailsVisible
    } = this.state;
    const openResourcesDetails = () => this.setState({resourcesDetailsVisible: true});
    const closeResourcesDetails = () => this.setState({resourcesDetailsVisible: false});
    const error = resourcesError
      ? (resourcesError.endsWith('.') ? resourcesError : `${resourcesError}.`)
      : undefined;
    if (!config) {
      return null;
    }
    if (resourcesPending) {
      return (
        <div className="cp-text-not-important" style={{marginBottom: 10}}>
          <LoadingOutlined />
          <span
            style={{marginLeft: 5}}
          >
            Fetching available resources...
          </span>
        </div>
      );
    }
    const renderAlert = (content, type) => (
      <div style={{marginBottom: 10}}>
        <div className={classNames({
          'cp-error': type === 'error',
          'cp-warning': type === 'warning'
        })}>
          {
            type === 'error' && (
              <CloseCircleFilled style={{marginRight: 5}} className="cp-error" />
            )
          }
          {
            type === 'warning' && (
              <ExclamationCircleFilled style={{marginRight: 5}} className="cp-warning" />
            )
          }
          {
            type === 'success' && (
              <CheckCircleFilled style={{marginRight: 5}} className="cp-success" />
            )
          }
          {content}
          <a
            onClick={() => this.refreshResources()}
            style={{marginLeft: 5}}
          >
            Refresh
          </a>
        </div>
      </div>
    );
    if (error) {
      return renderAlert(error, 'error');
    }
    const {
      nodes = [],
      best
    } = getInstanceResourcesAvailability(resources, parameters, config);
    const {
      cpu_requests_enabled: cpuRequestsEnabled = false,
      gpu_requests_enabled: gpuRequestsEnabled = false,
      ram_requests_enabled: ramRequestsEnabled = false,
      ram_requests_unit: ramRequestsUnit = DEFAULT_RAM_REQUESTS_UNIT
    } = config || {};
    const fit = nodes.length > 0 ? nodes[0].best : undefined;
    const onSelectNode = (node) => {
      this.setState({
        parameters: node.best
      }, () => {
        closeResourcesDetails();
        this.reportChange();
      });
    };
    const resourcesTypesCount = (cpuRequestsEnabled ? 1 : 0) +
      (gpuRequestsEnabled ? 1 : 0) +
      (ramRequestsEnabled ? 1 : 0);
    const renderInfo = (content) => {
      if (nodes.length > 0) {
        return (
          <div style={{display: 'inline'}}>
            <span
              style={{textDecoration: 'underline', cursor: 'pointer'}}
              onClick={openResourcesDetails}
            >
              {content}
            </span>
            <Modal
              open={resourcesDetailsVisible}
              title={false}
              footer={false}
              closable
              onCancel={closeResourcesDetails}
              width={400 + resourcesTypesCount * 100}
            >
              <table className={styles.nodeResourcesTable}>
                <thead>
                  <tr>
                    <th rowSpan={2}>Node</th>
                    <th
                      colSpan={resourcesTypesCount}
                    >
                      Available resources
                    </th>
                    <td>{'\u00A0'}</td>
                  </tr>
                  <tr>
                    {cpuRequestsEnabled && (<th style={{padding: 0}}>CPU</th>)}
                    {ramRequestsEnabled && (<th style={{padding: 0}}>RAM</th>)}
                    {gpuRequestsEnabled && (<th style={{padding: 0}}>GPU</th>)}
                  </tr>
                </thead>
                <tbody>
                  {
                    nodes.map((nd, index) => (
                      <tr key={`${nd.nodeName}-${index}`}>
                        <td style={{textAlign: 'left'}}>
                          <div>
                            {
                              nd.fits
                                ? <CheckCircleFilled className="cp-success" />
                                : <ExclamationCircleFilled className="cp-warning" />
                            }
                            <span style={{marginLeft: 5}}>{nd.nodeName}</span>
                          </div>
                        </td>
                        {cpuRequestsEnabled && (
                          <td>
                            <span>{nd.available.cpu}</span>
                            <span className="cp-text-not-important">
                              {' out of '}
                              {nd.total.cpu}
                            </span>
                          </td>
                        )}
                        {ramRequestsEnabled && (
                          <td>
                            <span>
                              {transformBytesToK8sRAMRequest(
                                nd.available.memory,
                                {unit: ramRequestsUnit, appendSuffix: true}
                              )}
                            </span>
                            <span className="cp-text-not-important">
                              {' out of '}
                              {transformBytesToK8sRAMRequest(
                                nd.total.memory,
                                {unit: ramRequestsUnit, appendSuffix: true}
                              )}
                            </span>
                          </td>
                        )}
                        {gpuRequestsEnabled && (
                          <td>
                            <span>{nd.available.gpu}</span>
                            <span className="cp-text-not-important">
                              {' out of '}
                              {nd.total.gpu}
                            </span>
                          </td>
                        )}
                        <td>
                          <a onClick={() => onSelectNode(nd)}>
                            Assign
                          </a>
                        </td>
                      </tr>
                    ))
                  }
                </tbody>
              </table>
            </Modal>
          </div>
        );
      }
      return content;
    };
    if (best) {
      return renderAlert(
        renderInfo(
          <span>
            There are enough resources to run the job.
          </span>
        ),
        'success'
      );
    }
    if (fit) {
      return renderAlert(
        renderInfo(
          <span>
            There are not enough resources to run the job. It will be queued.
          </span>
        ),
        'warning'
      );
    }
    return null;
  };

  render () {
    const {
      className,
      style
    } = this.props;
    const reqs = [
      this.renderCpuRequests(),
      this.renderRamRequests(),
      this.renderGpuRequests()
    ].filter(Boolean);
    if (reqs.length === 0) {
      return null;
    }
    return (
      <div
        className={classNames(className, styles.reservationParameters)}
        style={style}
      >
        {reqs}
        {this.renderResourcesAvailability()}
      </div>
    );
  }
}

ReservationParameters.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  instanceType: PropTypes.object,
  parameters: PropTypes.object,
  onChange: PropTypes.func
};

export default ReservationParameters;
