import React from 'react';
import PropTypes from 'prop-types';
import {Link} from 'react-router';
import {Icon, Popover} from 'antd';
import localization from '../../../../../utils/localization';
import styles from './run-executable.css';
import classNames from 'classnames';

function RunPipeline (props) {
  const {
    className,
    style,
    run,
    localization
  } = props;
  const {
    pipelineId,
    pipelineName,
    version: pipelineVersion,
    configuration: configurationCorrected
  } = run || {};
  const configuration = configurationCorrected && !/^default$/i.test(configurationCorrected)
    ? configurationCorrected
    : undefined;
  const pipelineLabel = localization ? localization.localizedString('Pipeline') : 'Pipeline';
  const pipelineDescription = (() => {
    if (pipelineName && pipelineVersion && configuration) {
      return `${pipelineName} (${pipelineVersion} - ${configuration})`;
    }
    if (pipelineName && pipelineVersion) {
      return `${pipelineName} (${pipelineVersion})`;
    }
    if (pipelineName) {
      return pipelineName;
    }
    if (pipelineId) {
      return `${pipelineLabel} #${pipelineId}`;
    }
    return undefined;
  })();
  const pipelineLink = (() => {
    if (pipelineId && pipelineVersion) {
      return `/${pipelineId}/${pipelineVersion}`;
    }
    if (pipelineId) {
      return `/${pipelineId}`;
    }
    return undefined;
  })();
  if (pipelineLink && pipelineDescription) {
    return (
      <div className={className} style={style}>
        <Icon type="fork" className={styles.toolIcon} />
        <Link to={pipelineLink}>
          {pipelineDescription}
        </Link>
      </div>
    );
  }
  if (pipelineDescription) {
    return (
      <Popover
        content={
          pipelineName
            ? (
              <span>
                {pipelineLabel} <b>{pipelineName}</b> has been removed
              </span>
            ) : (
              <span>
                {pipelineLabel} has been removed
              </span>
            )
        }
      >
        <div
          className={classNames(className, 'cp-danger')}
          style={style}
        >
          <Icon type="fork" className={styles.toolIcon} />
          <span>{pipelineDescription}</span>
          <Icon
            type="exclamation-circle"
            className={styles.toolIcon}
            style={{
              marginLeft: 5
            }}
          />
        </div>
      </Popover>
    );
  }
  return null;
}

RunPipeline.propTypes = {
  className: PropTypes.string,
  run: PropTypes.object
};

export default localization.localizedComponent(RunPipeline);
