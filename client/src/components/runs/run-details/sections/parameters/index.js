import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import {Link} from 'react-router';
import {Popover, Row} from 'antd';
import DataStorageLink from '../../../../special/data-storage-link';
import AdaptedLink from '../../../../special/AdaptedLink';
import {CP_CAP_LIMIT_MOUNTS} from '../../../../pipelines/launch/form/utilities/parameters';
import DataStorageList from '../../../controls/data-storage-list';
import instanceParameters from './instance-parameters';
import roleModel from '../../../../../utils/roleModel';
import {sortRunParameters} from '../../../logs/misc/post-process-run';
import ShareWith from '../../../ShareWith';
import styles from './run-parameters.css';

const MAX_PARAMETER_VALUES_TO_DISPLAY = 5;

class RunParametersSection extends React.Component {
  state = {
    resolvedValues: true
  }

  switchResolvedValues = () => {
    this.setState({resolvedValues: !this.state.resolvedValues});
  };

  renderRunParameter = (runParameter) => {
    if (!runParameter || !runParameter.name) {
      return null;
    }
    const valueSelector = () => {
      if (this.state.resolvedValues) {
        return runParameter.resolvedValue || runParameter.value || '';
      }
      return runParameter.value || '';
    };
    if (/^(input|output|common|path)$/i.test(runParameter.type)) {
      const valueParts = valueSelector().split(/[,|]/);
      return (
        <tr key={runParameter.name}>
          <th className={styles.parameterName}>
            <span>{runParameter.name}: </span>
          </th>
          <td className={styles.parameterValue}>
            <ul>
              {
                valueParts.map((value, index) => (
                  <li
                    key={`${value}-${index}`}
                  >
                    <DataStorageLink
                      key={`link-${value}-${index}`}
                      path={value}
                      isFolder={/^output$/i.test(runParameter.type) ? true : undefined}
                    >
                      {value}
                    </DataStorageLink>
                  </li>
                ))
              }
            </ul>
          </td>
        </tr>
      );
    }
    if (runParameter.name === 'parent-id' && parseInt(valueSelector()) !== 0) {
      return (
        <tr key={runParameter.name}>
          <th
            className={styles.parameterName}
          >
            {runParameter.name}:
          </th>
          <td className={styles.parameterValue}>
            <AdaptedLink
              to={`/run/${valueSelector()}`}>
              {valueSelector()}
            </AdaptedLink>
          </td>
        </tr>
      );
    }
    if (runParameter.name === CP_CAP_LIMIT_MOUNTS) {
      const values = (valueSelector() || '').split(',').map(v => v.trim());
      const isNone = /^none$/i.test(valueSelector());
      return (
        <tr key={runParameter.name}>
          <th
            className={styles.parameterName}
            style={{verticalAlign: 'middle'}}
          >
            {runParameter.name}:
          </th>
          <td className={styles.parameterValue}>
            {
              isNone && (<span>None</span>)
            }
            {
              !isNone && (<DataStorageList identifiers={values} />)
            }
          </td>
        </tr>
      );
    }
    if (/^metadata$/i.test(runParameter.type)) {
      const [metadataFolder, metadataClassName, itemsStr] = (valueSelector() || '').split(':');
      if (metadataFolder && metadataClassName && itemsStr) {
        const itemsCount = itemsStr.split(/[,;]/).length;
        return (
          <tr
            key={runParameter.name}>
            <th className={styles.parameterName}>{runParameter.name}:</th>
            <td className={styles.parameterValue}>
              <Link to={`/folder/${metadataFolder}/metadata/${metadataClassName}`}>
                {metadataClassName} ({itemsCount})
              </Link>
            </td>
          </tr>
        );
      }
      return (
        <tr
          key={runParameter.name}>
          <th className={styles.parameterName}>{runParameter.name}:</th>
          <td className={styles.parameterValue}>{valueSelector()}</td>
        </tr>
      );
    }
    let values = (valueSelector() || '').split(',').map(v => v.trim());
    if (values.length === 1) {
      return (
        <tr
          key={runParameter.name}>
          <th className={styles.parameterName}>{runParameter.name}:</th>
          <td className={styles.parameterValue}>{values[0]}</td>
        </tr>
      );
    }
    if (values.length <= MAX_PARAMETER_VALUES_TO_DISPLAY + 1) {
      return (
        <tr key={runParameter.name}>
          <th className={styles.parameterName}>
            <span>{runParameter.name}:</span>
          </th>
          <td className={styles.parameterValue}>
            <ul>
              {values.map((value, index) => <li key={index}>{value}</li>)}
            </ul>
          </td>
        </tr>
      );
    }
    return (
      <tr key={runParameter.name}>
        <th className={styles.parameterName}>
          <span>{runParameter.name}:</span>
        </th>
        <td className={styles.parameterValue}>
          <ul>
            {
              values
                .filter((value, index) => index < MAX_PARAMETER_VALUES_TO_DISPLAY)
                .map((value, index) => <li key={index}>{value}</li>)
            }
            <li>
              <Popover
                placement="right"
                content={
                  <div style={{maxHeight: '50vh', overflow: 'auto', paddingRight: 20}}>
                    {values.map((value, index) => <Row key={index}>{value}</Row>)}
                  </div>
                }>
                <a>And {values.length - MAX_PARAMETER_VALUES_TO_DISPLAY} more</a>
              </Popover>
            </li>
          </ul>
        </td>
      </tr>
    );
  };

  renderInstanceParameter = (parameter) => {
    const {run} = this.props;
    if (!run) {
      return null;
    }
    return (
      <tr key={parameter.key}>
        <th className={styles.parameterName}>
          {parameter.title || parameter.key}
        </th>
        <td className={styles.parameterValue}>
          {parameter.render(run)}
        </td>
      </tr>
    );
  };

  refreshRun = () => {
    const {refreshRun} = this.props;
    refreshRun && refreshRun();
  };

  render () {
    const {
      className,
      style,
      run
    } = this.props;
    if (!run) {
      return null;
    }
    const {
      pipelineRunParameters = []
    } = run;
    const filteredRunParameters = sortRunParameters(
      (pipelineRunParameters || []).filter(p => p.name && p.value)
    );
    const getParameterType = p => {
      switch ((p.type || '').toLowerCase()) {
        case 'common':
        case 'input':
          return 'input';
        case 'output':
          return 'output';
        default:
          return 'general';
      }
    };
    const types = ['input', 'output', 'general'];
    const instanceParametersFiltered = instanceParameters
      .filter((p) => (p.available && typeof p.available === 'function' && p.available(run)) ||
        (p.available && typeof p.available !== 'function') ||
        p.available === undefined);
    const canShare = run.initialized &&
      run.status === 'RUNNING' &&
      roleModel.isOwner(run);
    return (
      <div
        className={classNames(className, styles.runParametersSection)}
        style={style}
      >
        <table className={styles.runParametersTable}>
          <tbody>
            {
              instanceParametersFiltered.length > 0 && (
                <tr key={`type_instance_parameters`}>
                  <th colSpan={2} className={styles.section}>
                    INSTANCE
                  </th>
                </tr>
              )
            }
            {canShare ? (
              <tr>
                <th className={styles.parameterName}>
                  Share with:
                </th>
                <td className={styles.parameterValue}>
                  <ShareWith run={run} onSave={this.refreshRun} />
                </td>
              </tr>
            ) : null}
            {
              instanceParametersFiltered.map(this.renderInstanceParameter)
            }
            {
              types.map((type, index) => {
                const parameters = filteredRunParameters
                  .filter(p => getParameterType(p) === type);
                if (parameters.length === 0) {
                  return [];
                }
                const rows = [];
                rows.push((
                  <tr key={`type_${index}`}>
                    <th
                      colSpan={2}
                      className={styles.section}
                    >
                      {type ? type.toUpperCase() : 'GENERAL'}
                    </th>
                  </tr>
                ));
                rows.push(...parameters.map((p, i) => this.renderRunParameter(p, i, type)));
                return rows;
              })
            }
          </tbody>
        </table>
        <div>
          <a
            onClick={this.switchResolvedValues}>
            {this.state.resolvedValues ? 'Show original values' : 'Show resolved values'}
          </a>
        </div>
      </div>
    );
  }
}

RunParametersSection.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  run: PropTypes.object
};

export default RunParametersSection;
