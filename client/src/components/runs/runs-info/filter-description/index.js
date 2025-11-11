import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import RunStatusIcon from '../../../special/run-status-icon';
import UserName from '../../../special/UserName';
import RunTags from '../../run-tags';
import styles from './filter-description.css';

function ActiveRunsFilterDescription (props) {
  const {
    className,
    style,
    filters,
    postfix
  } = props;
  const {
    owners = [],
    instanceTypes = [],
    dockerImages = [],
    tags = {},
    statuses = []
  } = filters;
  const parts = [];
  const buildArrayInfo = (array = [], mapper = (o) => o, options = {}) => {
    const {
      prefix: prefixText,
      postfix: postfixText
    } = options;
    const entryPrefix = prefixText
      ? (<span className={styles.filterDescriptionDivider}>{prefixText}</span>)
      : undefined;
    const entryPostfix = postfixText
      ? (<span className={styles.filterDescriptionDivider}>{postfixText}</span>)
      : undefined;
    if (array.length > 1) {
      parts.push((
        <div className={styles.filterDescriptionEntry}>
          {entryPrefix}
          {
            array
              .map((o, idx) => (
                <div
                  className={styles.filterDescriptionEntry}
                  key={`entry-${idx}`}
                >
                  {mapper(o)}
                  {
                    idx < array.length - 1 && (
                      <span className={styles.filterDescriptionDivider}>{', '}</span>
                    )
                  }
                </div>
              ))
          }
          {entryPostfix}
        </div>
      ));
    } else if (array.length > 0) {
      parts.push((
        <div className={styles.filterDescriptionEntry}>
          {entryPrefix}
          {mapper(array[0])}
          {entryPostfix}
        </div>
      ));
    }
  };
  if (statuses.length > 0 && statuses.length < 4) {
    // not "all" active statuses (RUNNING, PAUSING, PAUSED, RESUMING)
    buildArrayInfo(
      statuses,
      (status) => (
        <div
          className={styles.filterDescriptionEntry}
        >
          <RunStatusIcon status={status} />
          <span>{status}</span>
        </div>
      ),
      {postfix: ' runs'}
    );
  } else {
    parts.push((<span>all active runs</span>));
  }
  buildArrayInfo(
    owners,
    (user) => (<UserName userName={user} showIcon style={{fontWeight: 'bold'}} />),
    {prefix: owners.length > 1 ? ' for owners ' : ' for owner '}
  );
  buildArrayInfo(
    dockerImages,
    (dockerImage) => (<b>{dockerImage.split('/').slice(-2).join('/')}</b>),
    {prefix: dockerImages.length > 1 ? ', docker images ' : ', docker image '}
  );
  buildArrayInfo(
    instanceTypes,
    (instanceType) => (<b>{instanceType}</b>),
    {prefix: instanceTypes.length > 1 ? ', instance types ' : ', instance type '}
  );
  const tagsArray = Object.keys(tags);
  if (tagsArray.length > 0) {
    parts.push(tagsArray.length > 1 ? ', tags ' : ', tag ');
    parts.push((
      <RunTags onlyKnown={false} run={{status: 'RUNNING', tags}} overflow={false} />
    ));
  }
  if (parts.length > 0) {
    return (
      <div className={classNames(className, styles.filterDescription)} style={style}>
        <span>Displaying</span>
        <span className={styles.filterDescriptionDivider}>{' '}</span>
        {
          parts
            .map((part, idx) => (
              <div
                key={`part-${idx}`}
                className={styles.filterDescriptionEntry}
              >
                {part}
              </div>
            ))
        }
        {postfix && (<span className={styles.filterDescriptionDivider}>{postfix}</span>)}
      </div>
    );
  }
  return null;
}

ActiveRunsFilterDescription.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  filters: PropTypes.shape({
    tags: PropTypes.object,
    owners: PropTypes.arrayOf(PropTypes.string),
    dockerImages: PropTypes.arrayOf(PropTypes.string),
    instanceTypes: PropTypes.arrayOf(PropTypes.string),
    statuses: PropTypes.arrayOf(PropTypes.string)
  }),
  postfix: PropTypes.node
};

export default ActiveRunsFilterDescription;
