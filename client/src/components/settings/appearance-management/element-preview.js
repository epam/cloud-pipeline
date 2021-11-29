import React from 'react';
import {Button, Icon, Alert, Input} from 'antd';
import Menu, {MenuItem} from 'rc-menu';

import styles from './element-preview.css';

export class ElementPreview extends React.Component {
    state = {
      colors: this.props.colors,
      config: this.props.config
    }
    componentDidUpdate (prevProps, prevState, snapshot) {
      if (
        prevProps.config !== this.props.config ||
      prevProps.colors !== this.props.colors
      ) {
        this.updateFromProps();
      }
    }

  updateFromProps = () => {
    const {colors, config} = this.props;
    this.setState({
      colors,
      config
    });
  }
  renderButtons = () => {
    return (<div
      className={styles.container}>
      <Button
        style={{
          margin: '5px',
          width: 100,
          userSelect: 'none',
          backgroundColor: this.state.colors['@primary-color'],
          color: this.state.colors['@primary-text-color']}}>Primary</Button>
      <Button
        style={{
          margin: '5px',
          width: 100}}>Default</Button>
      <Button style={{
        margin: '5px',
        width: 100,
        userSelect: 'none',
        backgroundColor: this.state.colors['@btn-danger-background-color'],
        color: this.state.colors['@btn-danger-color']}}>Danger</Button>
      <Button style={{
        margin: '5px',
        width: 100,
        userSelect: 'none',
        backgroundColor: this.state.colors['@btn-disabled-background-color'],
        color: this.state.colors['@btn-disabled-color']}}>Disabled</Button>
    </div>);
  };
    renderTables = () => {
      return null;
    }

    renderMenu = () => (
      <div className={styles.container}>
        <Menu style={{
          backgroundColor: this.state.colors['@panel-background-color'],
          borderColor: this.state.colors['@menu-border-color']}}>
          <MenuItem style={{color: this.state.colors['@menu-color']}}>MenuItem 1</MenuItem>
          <MenuItem style={{color: this.state.colors['@menu-color']}}>MenuItem 2</MenuItem>
          <MenuItem style={{color: this.state.colors['@menu-color']}}>MenuItem 3</MenuItem>
          <MenuItem style={{color: this.state.colors['@menu-color']}}>MenuItem 4</MenuItem>
        </Menu>
      </div>);
    renderCards = () => (
      <div className={styles.container}>
        <div
          style={{
            width: 200,
            backgroundColor: this.state.colors['@card-background-color'],
            border: `1px solid ${this.state.colors['@card-border-color']}`,
            borderRadius: 2
          }}>
          <div
            style={{
              width: '100%',
              backgroundColor: this.state.colors['@card-header-background']
            }}>
            <h4 style={{padding: 10}}>Header title</h4>
          </div>
          <div style={{padding: 10}}>
            <p>Card content</p>
            <p>Card content</p>
          </div>
        </div>
      </div>);
    renderForms = () => (
      <div className={styles.container}>
        <Input
          placeholder="placeholder text"
          style={{
            backgroundColor: this.state.colors['@input-background'],
            borderColor: this.state.colors['@input-border'],
            color: this.state.colors['@input-color'],
            marginBottom: 10,
            width: 150
          }} />
        <Input
          placeholder="disabled"
          disabled
          style={{
            backgroundColor: this.state.colors['@input-background-disabled'],
            borderColor: this.state.colors['@input-border'],
            color: this.state.colors['@input-color'],
            width: 150
          }} />
      </div>
    );
    renderAlerts = () => (
      <div className={styles.container}>
        <Alert
          type="info"
          message={
            <span>
              <Icon
                style={{color: this.state.colors['@alert-info-icon']}}
                type="info-circle" />
              &nbsp;
              <span>info</span>
            </span>}
          style={{
            margin: '5px',
            textAlign: 'center',
            width: 150,
            userSelect: 'none',
            backgroundColor: this.state.colors['@alert-info-background'],
            borderColor: this.state.colors['@alert-info-border']}} />
        <Alert
          type="success"
          message={
            <span>
              <Icon
                style={{color: this.state.colors['@alert-success-icon']}}
                type="check-circle-o" />
              &nbsp;
              <span>success</span>
            </span>}
          style={{
            margin: '5px',
            textAlign: 'center',
            width: 150,
            userSelect: 'none',
            backgroundColor: this.state.colors['@alert-success-background'],
            borderColor: this.state.colors['@alert-success-border']}} />
        <Alert
          type="warning"
          message={
            <span>
              <Icon
                style={{color: this.state.colors['@alert-warning-icon']}}
                type="exclamation-circle-o" />
              &nbsp;
              <span>warning</span>
            </span>}
          style={{
            margin: '5px',
            textAlign: 'center',
            width: 150,
            userSelect: 'none',
            backgroundColor: this.state.colors['@alert-warning-background'],
            borderColor: this.state.colors['@alert-warning-border']}} />
        <Alert
          type="error"
          message={
            <span>
              <Icon
                style={{color: this.state.colors['@alert-error-icon']}}
                type="cross-circle-o" />
              &nbsp;
              <span>error</span>
            </span>}
          style={{
            margin: '5px',
            textAlign: 'center',
            width: 150,
            userSelect: 'none',
            backgroundColor: this.state.colors['@alert-error-background'],
            borderColor: this.state.colors['@alert-error-border']}} />
      </div>);
    renderNavigation = () => (<div>navigation</div>);

    render () {
      const {section} = this.props;
      switch (section.toLowerCase()) {
        case 'buttons':
          return this.renderButtons();
        case 'tables':
          return this.renderTables();
        case 'alerts':
          return this.renderAlerts();
        case 'menu':
          return this.renderMenu();
        case 'navigation panel':
          return this.renderNavigation();
        case 'cards':
          return this.renderCards();
        case 'forms':
          return this.renderForms();
        default:
          return null;
      }
    }
}
