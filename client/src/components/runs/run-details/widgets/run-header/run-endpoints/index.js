import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import styles from './run-endpoints.css';
import {parseRunServiceUrlConfiguration} from '../../../../../../utils/multizone';
import MultizoneUrl from '../../../../../special/multizone-url';
import RunSSHButton from '../run-actions/run-ssh-button';
import RunFsBrowserButton from '../run-actions/run-fs-browser-button';
import {Icon} from 'antd';

const ACTIVE_RUN_STATUSES = ['RUNNING', 'PAUSED', 'PAUSING', 'RESUMING'];

function parseExternalUrlsTag (run) {
  const {tags, status} = run || {};
  const tagValue = tags && tags.EXTERNAL_URLS;
  if (!tagValue) {
    return [];
  }
  try {
    const parsed = JSON.parse(tagValue);
    if (!Array.isArray(parsed)) {
      return [];
    }
    const isActiveRun = ACTIVE_RUN_STATUSES.includes(status);
    return parsed.filter(({displayOnCompletion}) => {
      if (displayOnCompletion === false) {
        return isActiveRun;
      }
      return true;
    });
  } catch (_) {
    return [];
  }
}

function RunEndpoints (props) {
  const {
    className,
    style,
    run
  } = props;
  if (!run) {
    return null;
  }
  const {serviceUrl} = run;
  const regionedUrls = parseRunServiceUrlConfiguration(serviceUrl);
  const externalUrls = parseExternalUrlsTag(run);
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
            <Icon style={{marginRight: 5}} type="export" />
            <span>{regionedUrl.name}</span>
          </MultizoneUrl>
        ))
      }
      {
        externalUrls.map(({url, name}, eIndex) => (
          <a
            key={`external-endpoint-${eIndex}`}
            href={url}
            target="_blank"
            rel="noopener noreferrer"
            className={styles.runEndpoint}
          >
            <Icon style={{marginRight: 5}} type="export" />
            <span>{name || url}</span>
          </a>
        ))
      }
      <RunSSHButton run={run} className={styles.runEndpoint} icon="code-o" />
      <RunFsBrowserButton run={run} className={styles.runEndpoint} icon="folder-open" />
    </div>
  );
}

RunEndpoints.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  run: PropTypes.object
};

export default RunEndpoints;
