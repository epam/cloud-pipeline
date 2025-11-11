import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import styles from './plugins.css';
import {Button, Icon, Select} from 'antd';
import {getPluginTypeName, UI_PLUGIN_TYPE_LAUNCH_FORM} from '../utilities';
import UsersRolesSelect from '../../special/users-roles-select';

function ConfigurePlugin (props) {
  const {
    className,
    style,
    disabled,
    plugin,
    availablePlugins = [],
    onChange,
    onRemove
  } = props;

  const {
    plugin: assignedPlugin = {},
    sids = []
  } = plugin || {};
  const {
    id: assignedPluginId,
    type: assignedPluginType = UI_PLUGIN_TYPE_LAUNCH_FORM
  } = assignedPlugin;

  const reportOnChange = (pluginData) => {
    if (onChange) {
      onChange(pluginData);
    }
  };

  const onRemoveConfirm = () => {
    if (onRemove) {
      onRemove(plugin);
    }
  };

  const filteredPlugins = availablePlugins.filter((pl) => pl.type === assignedPluginType);
  const availableTypes = [...new Set(availablePlugins.map((ap) => ap.type))];

  const onTypeChange = (type) => {
    const somePlugin = availablePlugins.find((p) => p.type === type);
    if (somePlugin) {
      reportOnChange({
        ...plugin,
        plugin: somePlugin
      });
    }
  };

  const onPluginChange = (id) => {
    const somePlugin = availablePlugins.find((p) => String(p.id) === String(id));
    if (somePlugin) {
      reportOnChange({
        ...plugin,
        plugin: somePlugin
      });
    }
  };

  const onSidsChange = (newSids) => {
    reportOnChange({
      ...plugin,
      sids: newSids
    });
  };

  return (
    <div className={className} style={style}>
      <div className={classNames(styles.configurePluginRow)}>
        <span>Page:</span>
        <Select
          className={styles.control}
          disabled={disabled}
          value={assignedPluginType}
          onChange={onTypeChange}
        >
          {
            availableTypes.map((pl) => (
              <Select.Option
                key={pl}
                value={pl}
              >
                {getPluginTypeName(pl)}
              </Select.Option>
            ))
          }
        </Select>
        <span>Plugin:</span>
        <Select
          className={styles.control}
          disabled={disabled}
          value={assignedPluginId === undefined ? undefined : String(assignedPluginId)}
          onChange={onPluginChange}
        >
          {filteredPlugins.map((availablePlugin) => (
            <Select.Option key={String(availablePlugin.id)} value={String(availablePlugin.id)}>
              {availablePlugin.name}
            </Select.Option>
          ))}
        </Select>
        <Button
          disabled={disabled}
          size="small"
          type="danger"
          onClick={onRemoveConfirm}
          style={{marginRight: 10}}>
          <Icon type="delete" />
        </Button>
      </div>
      <div className={classNames(styles.configurePluginRow, styles.flexStart)}>
        <span>Assign to:</span>
        <UsersRolesSelect
          style={{flex: 1}}
          disabled={disabled}
          value={sids}
          onChange={onSidsChange}
          placeholder="Specify users, roles or groups"
        />
      </div>
    </div>
  );
}

ConfigurePlugin.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  disabled: PropTypes.bool,
  plugin: PropTypes.object,
  onChange: PropTypes.func,
  onRemove: PropTypes.func,
  availablePlugins: PropTypes.oneOfType([PropTypes.array, PropTypes.object])
};

export default ConfigurePlugin;
