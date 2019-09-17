import React from 'react';
import {parse} from '../../utilities/query-parameters';
import PathInput from './path-input';
import Upload from '../upload';
import styles from './header.css';

function header({disabled, history}) {
  const {location} = history;
  const {path} = parse(location.search);
  const onNavigate = (p) => {
    if (p) {
      history.push(`/?path=${p}`);
    } else {
      history.push('/');
    }
  };
  return (
    <div
      className={styles.header}
    >
      <Upload
        path={path}
        showUploadArea={false}
        showButton
        uploadAreaClassName={styles.uploadArea}
      >
        <PathInput
          className={styles.pathInput}
          disabled={disabled}
          path={path}
          onNavigate={onNavigate}
        />
      </Upload>
    </div>
  );
}

export default header;
