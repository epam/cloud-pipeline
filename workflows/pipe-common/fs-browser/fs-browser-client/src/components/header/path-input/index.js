import React from 'react';
import PropTypes from 'prop-types';
import {observer} from 'mobx-react';
import {Link} from 'react-router-dom';
import {
  Input,
} from 'antd';
import styles from './path-input.css';

@observer
class PathInput extends React.Component {
  static propTypes = {
    disabled: PropTypes.bool,
    onNavigate: PropTypes.func,
    path: PropTypes.string,
  };

  static defaultProps = {
    disabled: false,
    onNavigate: null,
    path: null,
  };

  state = {
    editMode: false,
  };

  renderEditMode = () => {
    const {editMode} = this.state;
    if (!editMode) {
      return null;
    }
    const {disabled, path, onNavigate} = this.props;
    const onPressEnter = (e) => {
      this.toggleEditMode(false);
      if (onNavigate) {
        onNavigate(e.target.value);
      }
    };
    return (
      <Input
        autoFocus
        className={styles.input}
        disabled={disabled}
        size="small"
        defaultValue={path}
        onPressEnter={onPressEnter}
        onBlur={() => this.toggleEditMode(false)}
      />
    );
  };

  renderBreadcrumbs = () => {
    const {editMode} = this.state;
    if (editMode) {
      return null;
    }
    const {disabled, path, onNavigate} = this.props;
    const paths = [{
      name: 'Root',
      path: '',
    }];
    const parts = (path || '')
      .split('/')
      .filter(l => l.length > 0)
      .map((p, index, array) => ({
        name: p,
        path: array.slice(0, index + 1).join('/'),
      }));
    paths.push(...parts);
    const onLinkClicked = link => (e) => {
      e.preventDefault();
      e.stopPropagation();
      if (onNavigate && !disabled) {
        onNavigate(link.path);
      }
    };
    const renderLink = (link, index, array) => {
      if (array.length - 1 === index || disabled) {
        return (
          <span
            key={index}
            className={styles.link}
          >
            {link.name}
          </span>
        );
      }
      const url = link.path ? `/?path=${link.path}` : '/';
      return (
        <span
          key={index}
          className={styles.link}
        >
          <Link
            to={url}
            onClick={onLinkClicked(link)}
          >
            {link.name}
          </Link>
        </span>
      );
    };
    return paths.map(renderLink);
  };

  toggleEditMode = (editMode) => {
    this.setState({editMode});
  };

  render() {
    return (
      // eslint-disable-next-line
      <div
        className={styles.container}
        onClick={() => this.toggleEditMode(true)}
      >
        {this.renderBreadcrumbs()}
        {this.renderEditMode()}
      </div>
    );
  }
}

export default PathInput;
