import React from 'react';
import classNames from 'classnames';
import PropTypes from 'prop-types';
import {message, Button, Alert} from 'antd';
import styles from './scheme-parameter-input.css';
import {
  checkSchemeParameterValid,
  createNewEntry
} from './utilities';
import LaunchFormSchemeParameterEntry from './scheme-parameter-entry';
import {
  buildSchemeParameterValue,
  parseSchemeParameterValue
} from '../../../../utilities/parameter-utilities';

class LaunchFormSchemeParameterTable extends React.Component {
  state = {
    value: undefined,
    error: undefined
  };

  componentDidMount () {
    this.updateFromProps();
  }

  componentDidUpdate (prevProps) {
    if (prevProps.value !== this.props.value) {
      this.updateFromProps();
    }
  }

  updateFromProps = () => {
    const {value, parameter} = this.props;
    try {
      const parsed = parseSchemeParameterValue(value, parameter?.name);
      this.setState({value: parsed, error: undefined});
    } catch (error) {
      this.setState({error: error.message});
    }
  };

  get isRequired () {
    const {
      parameter
    } = this.props;
    const {
      config = {}
    } = parameter || {};
    const {
      required = false
    } = config || {};
    return required;
  }

  get properties () {
    const {
      parameter
    } = this.props;
    const {
      config = {}
    } = parameter || {};
    const {
      scheme
    } = config || {};
    if (!scheme) {
      return null;
    }
    const {
      properties = []
    } = scheme;
    return properties || [];
  }

  onCancel = () => {
    const {onCancel} = this.props;
    if (onCancel) {
      onCancel();
    }
    this.updateFromProps();
  };

  onSave = () => {
    const {value = []} = this.state;
    const {onChange, parameter} = this.props;
    const {
      config = {}
    } = parameter || {};
    const {scheme} = config || {};
    try {
      const schemeParameterValue = buildSchemeParameterValue(value, scheme);
      if (onChange) {
        onChange(schemeParameterValue);
      }
    } catch (error) {
      message.error(error.message, 5);
    }
  };

  onAddEntry = () => {
    const {properties} = this;
    const {value = []} = this.state;
    const newValue = (value || []).slice();
    newValue.push(createNewEntry(properties));
    this.setState({value: newValue});
  };

  onChangeEntry = (entryId) => (e) => {
    const {value = []} = this.state;
    const newValue = (value || []).slice();
    newValue.splice(entryId, 1, e);
    this.setState({value: newValue});
  };

  onRemoveEntry = (entryId) => () => {
    const {value = []} = this.state;
    const newValue = (value || []).slice();
    newValue.splice(entryId, 1);
    this.setState({value: newValue});
  };

  render () {
    const {
      className,
      style,
      parameter,
      disabled,
      currentProjectId,
      currentProjectMetadata,
      currentMetadataEntity,
      rootEntityId,
      metadataAutoComplete
    } = this.props;
    const {
      value = []
    } = this.state;
    const {properties, isRequired} = this;
    if (!properties || properties.length === 0) {
      return null;
    }
    const getPropName = (prop) => {
      const {name, prettyName = name} = prop;
      return prettyName;
    };
    const emptyListError = isRequired && value.length === 0;
    const valid = checkSchemeParameterValid(value, properties) && !emptyListError;
    return (
      <div
        className={classNames(className, styles.schemeParameterTableContainer)}
        style={style}
      >
        {
          parameter && parameter.config && parameter.config.description ? (
            <div className={styles.schemeParameterTableDescription}>
              {parameter.config.description}
            </div>
          ) : false
        }
        <div className={styles.schemeParameterTableScroller}>
          <table className={classNames(styles.schemeParameterTable, 'cp-bordered')}>
            <thead>
              <tr>
                {
                  properties.map((prop) => (
                    <th
                      key={prop.name}
                      className={
                        classNames(
                          'cp-divider left right bottom',
                          styles.parameterCol,
                          styles[`parameter-type-${prop.type}`]
                        )
                      }
                    >
                      <div>{getPropName(prop)}</div>
                      {
                        prop.description && (
                          <div
                            className="cp-text-not-important"
                            style={{
                              wordBreak: 'break-word',
                              fontWeight: 'normal',
                              fontSize: 'smaller'
                            }}
                          >
                            {prop.description}
                          </div>
                        )
                      }
                    </th>
                  ))
                }
                <td
                  className={classNames(styles.entryAction, 'cp-divider left right bottom')}
                >
                  {'\u00A0'}
                </td>
              </tr>
            </thead>
            <tbody>
              {
                (value || []).map((entry, entryId) => (
                  <LaunchFormSchemeParameterEntry
                    key={`entry-${entryId}`}
                    entry={entry}
                    parameter={parameter}
                    properties={properties}
                    disabled={disabled}
                    onChange={this.onChangeEntry(entryId)}
                    onRemove={this.onRemoveEntry(entryId)}
                    currentProjectId={currentProjectId}
                    currentProjectMetadata={currentProjectMetadata}
                    currentMetadataEntity={currentMetadataEntity}
                    rootEntityId={rootEntityId}
                    metadataAutoComplete={metadataAutoComplete}
                  />
                ))
              }
            </tbody>
          </table>
        </div>
        {
          emptyListError && (
            <div className={styles.schemeParameterTableWarning}>
              <Alert
                message={(
                  <div>
                    At least 1 object is required
                  </div>
                )}
                type="warning" showIcon
              />
            </div>
          )
        }
        <div className={styles.schemeParameterTableActions}>
          <Button onClick={this.onAddEntry} disabled={disabled}>
            ADD OBJECT
          </Button>
          <div
            style={{marginLeft: 'auto', display: 'inline-flex', alignItems: 'center', gap: 5}}
          >
            <Button onClick={this.onCancel} disabled={disabled}>
              CANCEL
            </Button>
            <Button type="primary" disabled={disabled || !valid} onClick={this.onSave}>
              SAVE
            </Button>
          </div>
        </div>
      </div>
    );
  }
}

LaunchFormSchemeParameterTable.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  parameter: PropTypes.object,
  value: PropTypes.any,
  required: PropTypes.bool,
  onChange: PropTypes.func,
  onCancel: PropTypes.func,
  disabled: PropTypes.bool,
  currentProjectId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  currentProjectMetadata: PropTypes.object,
  currentMetadataEntity: PropTypes.oneOfType([PropTypes.array, PropTypes.object]),
  rootEntityId: PropTypes.string,
  metadataAutoComplete: PropTypes.bool
};

export default LaunchFormSchemeParameterTable;
