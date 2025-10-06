import React, { useState } from 'react';
import Button from '../shared/button/Button';
import Input from '../shared/input';
import Select from '../shared/select';
import styles from './ThemeManager.module.css';
import type { Terminal } from '../utils/terminal';

export type ThemeManagerProps = {
  onApply: (themeConfig: ThemeConfig) => void;
  onCancel: () => void;
  terminal?: Terminal;
};

export type ThemeConfig = {
  'background-color'?: string;
  'foreground-color'?: string;
  'font-size'?: string;
  'font-family'?: string;
  'enable-bold'?: boolean;
};

const ThemeManager: React.FC<ThemeManagerProps> = ({ onApply, onCancel, terminal }) => {
  const [themeConfig, setThemeConfig] = useState<ThemeConfig>({
    'background-color': '#1a1a1a',
    'foreground-color': '#ffffff',
    'font-size': '14',
    'font-family': 'monospace',
  });

  const onInputChange = (field: keyof ThemeConfig, value: string) => {
    setThemeConfig(prev => ({
      ...prev,
      [field]: value
    }));
  };

  const handleApply = () => {
    onApply(themeConfig);
    terminal?.applyConfig(themeConfig);
  };

  return (
    <div className={styles.themeManager}>
      <div className={styles.themeManager__form}>
        <Input
          label="Background Color"
          type="color"
          className={styles.themeManager__field}
          value={themeConfig['background-color']}
          onChange={(v) => typeof v === 'string' && onInputChange('background-color', v)}
        />
        <Input
          label="Text Color"
          type="color"
          className={styles.themeManager__field}
          value={themeConfig['foreground-color']}
          onChange={(v) => typeof v === 'string' && onInputChange('foreground-color', v)}
        />
        <Select
          label="Font Size"
          className={styles.themeManager__field}
          value={themeConfig['font-size']}
          onChange={(v) => typeof v === 'string' && onInputChange('font-size', v)}
          placeholder="Select size"
          options={[
            { label: '12px', value: '12' },
            { label: '14px', value: '14' },
            { label: '16px', value: '16' },
            { label: '18px', value: '18' }
          ]}
          size="md"
          fullWidth
        />
      </div>
      <div className={styles.themeManager__actions}>
        <Button variant="primary" onClick={handleApply}>
          Apply
        </Button>
        <Button variant="secondary" onClick={onCancel}>
          Cancel
        </Button>
      </div>
    </div>
  );
};

export default ThemeManager;
