import React from 'react';
import {inject, observer} from 'mobx-react';
import {parse} from '../../utilities/query-parameters';

@inject((stores, params) => {
  const {path} = parse(params.history.location.search);
  return {
    path,
  };
})
@observer
class Browser extends React.Component {
  render() {
    const {path} = this.props;
    return (
      <div>
        Browser
      </div>
    );
  }
}

export default Browser;
