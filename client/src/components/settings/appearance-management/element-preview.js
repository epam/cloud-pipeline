import React from 'react';
import {Button, Table, Alert, Menu} from 'antd';

export class ElementPreview extends React.Component {
    state = {
      colors: this.props.colors,
      config: this.props.config
    }
    componentDidUpdate (prevProps, prevState, snapshot) {
      console.log(this.props.config, this.props.colors);
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
      // className="cp-panel"
      style={{
        width: '20%',
        padding: '20px',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'space-between',
        borderRadius: '4px'
      }}>
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
    renderTables = () => (<div>tables</div>);
    renderMenu = () => (<div>menu</div>);
    renderAlerts = () => (<div>alerts</div>);
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
        default:
          return null;
      }
    }
}
