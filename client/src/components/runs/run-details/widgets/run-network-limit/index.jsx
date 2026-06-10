import React from 'react';
import classNames from 'classnames';
import {ExclamationCircleOutlined} from '@ant-design/icons';
import {inject, observer} from 'mobx-react';
import RunTags, {KNOWN_TAG_NAMES, networkLimitValueRender} from '../../../run-tags';
import displayDate from '../../../../../utils/displayDate';
import RunDetail, {RunDetailProps} from '../run-detail';

function RunNetworkLimit(props) {
  const {className, style, run, preferences, inline} = props;
  if (!run || !preferences) {
    return null;
  }
  if (!preferences.loaded) {
    preferences.fetchIfNeededOrWait();
    return null;
  }
  const {tags = {}} = run;
  const networkLimitTag = tags[KNOWN_TAG_NAMES.network_limit.toUpperCase()];
  const suffix = preferences?.systemRunTagDateSuffix || '';
  const networkLimitTagTimestamp = suffix
    ? tags[`${KNOWN_TAG_NAMES.network_limit.toUpperCase()}${suffix}`]
    : undefined;
  if (
    networkLimitTag === undefined ||
    !RunTags.shouldDisplayTags(run, this.props.preferences, true)
  ) {
    return null;
  }
  return (
    <RunDetail
      className={classNames(className, 'cp-error')}
      style={style}
      run={run}
      inline={inline}
    >
      <ExclamationCircleOutlined />
      <span>Network is limited to</span>
      <b>{networkLimitValueRender(networkLimitTag)}</b>
      {networkLimitTagTimestamp ? (
        <span>{`(on ${displayDate(networkLimitTagTimestamp)})`}</span>
      ) : null}
    </RunDetail>
  );
}

RunNetworkLimit.propTypes = {
  ...RunDetailProps,
};

export default inject('preferences')(observer(RunNetworkLimit));
