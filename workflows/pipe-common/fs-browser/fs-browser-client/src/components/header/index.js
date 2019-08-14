import React from 'react';
import {parse} from '../../utilities/query-parameters';
import PathInput from './path-input';
import styles from './header.css';

function header({history}) {
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
      <PathInput
        path={path}
        onNavigate={onNavigate}
      />
    </div>
  );
}

export default header;
