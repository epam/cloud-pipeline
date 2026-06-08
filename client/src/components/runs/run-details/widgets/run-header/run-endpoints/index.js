import React from 'react';
import PropTypes from 'prop-types';
import {ExportOutlined, CodeOutlined, FolderOpenOutlined} from '@ant-design/icons';
import classNames from 'classnames';
import styles from './run-endpoints.css';
import {
  parseRunServiceUrlConfiguration} from '../../../../../../utils/multizone';
import MultizoneUrl from '../../../../../special/multizone-url';
import RunSSHButton from '../run-actions/run-ssh-button';
import RunFsBrowserButton from '../run-actions/run-fs-browser-button';

function RunEndpoints (props) {
  const {
    className,
    style,
    run
  } = props;
  if (!run) {
    return null;
  }
  const {
    serviceUrl
  } = run;
  const regionedUrls = parseRunServiceUrlConfiguration(serviceUrl);
  return (
    <div
      className={classNames(styles.runEndpoints, className)}
      style={style}
    >
      {
        regionedUrls.map((regionedUrl, eIndex) => (
          <MultizoneUrl
            key={`endpoint-${regionedUrl.name}-${eIndex}`}
            target={regionedUrl.sameTab ? '_top' : '_blank'}
            configuration={regionedUrl.url}
            dropDownIconStyle={{marginTop: 5}}
            className={styles.runEndpoint}
          >
            <ExportOutlined style={{marginRight: 5}} />
            <span>{regionedUrl.name}</span>
          </MultizoneUrl>
        ))
      }
      <RunSSHButton run={run} className={styles.runEndpoint} icon={CodeOutlined} />
      <RunFsBrowserButton run={run} className={styles.runEndpoint} icon={FolderOpenOutlined} />
    </div>
  );
}

RunEndpoints.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  run: PropTypes.object
};

export default RunEndpoints;
