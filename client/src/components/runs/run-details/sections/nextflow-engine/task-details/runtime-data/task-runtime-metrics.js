import React from 'react';
import classNames from 'classnames';
import {TaskRuntimeDataDetailsProps, injectRuntimeData} from './task-runtime-data-details';
import styles from './runtime-data.css';

function TaskRuntimeMetrics (props) {
  const {
    className,
    style,
    data = ''
  } = props;
  const params = data
    .split('\n')
    .map(line => line.trim())
    .filter(line => line.length > 0)
    .map(line => {
      const parts = line.split('=');
      const [key, ...values] = parts;
      return {
        key,
        value: values.join('='),
        header: parts.length === 1
      };
    });
  return (
    <div
      className={classNames(className)}
      style={style}
    >
      <table className={styles.runtimeMetricsTable}>
        <tbody>
          {
            params.map((param, pIdx) => (
              <tr
                key={`param-${param.key}-${pIdx}`}
              >
                {
                  param.header
                    ? (
                      <th
                        colSpan={2}
                        className="cp-divider bottom light"
                      >
                        {param.key}
                      </th>
                    ) : [
                      <th className="cp-divider bottom light" key="key">{param.key}:</th>,
                      <td className="cp-divider bottom light" key="value">{param.value}</td>
                    ]
                }
              </tr>
            ))
          }
        </tbody>
      </table>
    </div>
  );
}

TaskRuntimeMetrics.propTypes = TaskRuntimeDataDetailsProps;

export default injectRuntimeData('trace', 'Error loading task metrics')(TaskRuntimeMetrics);
