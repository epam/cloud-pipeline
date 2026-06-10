import classNames from 'classnames';
import {CommonProps} from '../../@types/common.ts';
import {MetadataEntityRef} from '../../@types/metadata.ts';
import './issues.css';

function Issues(props: CommonProps & {entity?: MetadataEntityRef}) {
  const {className, style, entity} = props;
  return (
    <div className={classNames(className, 'issues-container')} style={style}>
      {entity
        ? `Issues for #${entity.entityId} (${entity.entityClass})`
        : 'Select object to view issues'}
    </div>
  );
}

export {Issues};
