import React, { useEffect, useMemo, useState } from "react";
import Button from "../shared/button/Button";
import Input from "../shared/input";
import Select from "../shared/select";
import styles from "./ThemeManager.module.css";
import { ConfigKeys, type ANSIPalette, type ParameterValue, type TerminalTheme } from "../utils/types";
import ANSIColors from "./components/ANSIColors";
import { type ThemeManagerProps } from "./types";
import ThemeColorPicker from "./components/ThemeColorPicker";
import ThemeFontPicker from "./components/ThemeFontPicker";
import { checkThemeChanged, DEFAULT_THEMES } from "../utils/themes";
import ThemeCard from "./components/theme-card/ThemeCard";

const ThemeManager: React.FC<ThemeManagerProps> = ({ onCancel, terminal }) => {
  const [parameters, setParameters] = useState<TerminalTheme | undefined>();
  const [theme, setTheme] = useState<string | undefined>(undefined);
  useEffect(() => {
    if (terminal?.initialized) {
      setParameters(terminal.theme);
      if (theme !== terminal.currentThemeName) {
        setTheme(terminal.currentThemeName);
      }
    }
  }, [terminal, theme]);
  const onInputChange = (
    field: ConfigKeys,
    value: ParameterValue | ANSIPalette
  ) => {
    terminal!.setParameter(field, value);
    setParameters(prevValue => ({
      ...prevValue,
      [field]: value
    }));
  };
  const onChangeTheme = async (value: string | number) => {
    if (typeof value === "string") {
      await terminal!.setTheme(
        value,
        true,
        () => setParameters(terminal!.theme)
      );
      setTheme(value);
    }
  };
  const hasChanges = checkThemeChanged(theme, terminal);
  const onRevertChanges = async () => {
    terminal!.resetTheme(terminal!.currentThemeName);
    setParameters(terminal!.theme);
  };
  const onReset = async () => {
    terminal!.resetToDefaults();
    setTheme(terminal!.currentThemeName);
    setParameters(terminal!.theme);
  };
  const labelWidth = 120;
  const labelStyle = useMemo(
    () => ({
      width: labelWidth,
      display: "flex",
      justifyContent: "flex-end",
      paddingRight: 8,
    }),
    [labelWidth]
  );
  if (!terminal || !parameters) {
    return null;
  }
  console.log(parameters[ConfigKeys.fontFamily])
  return (
    <div className={styles.themeManager}>
      <div className={styles.themeManager__form}>
        <Select
          label="Theme"
          className={styles.themeManager__field}
          wrapperClassName={styles.themeManager__field}
          style={{
            label: labelStyle,
            input: { flex: "1 0" },
          }}
          value={theme}
          onChange={onChangeTheme}
          placeholder="Select theme"
          options={Object.keys(DEFAULT_THEMES).map((key) => ({
            value: key,
            label: key.charAt(0).toUpperCase() + key.replaceAll('-', ' ').slice(1),
          }))}
          fullWidth
        />
        <ThemeColorPicker
          label="Background Color"
          value={parameters[ConfigKeys.background]}
          onChange={(v) => onInputChange(ConfigKeys.background, v)}
          labelWidth={labelWidth}
        />
        <ThemeColorPicker
          label="Text Color"
          value={parameters[ConfigKeys.foreground]}
          onChange={(v) => onInputChange(ConfigKeys.foreground, v)}
          labelWidth={labelWidth}
        />
        <ThemeColorPicker
          label="Cursor Color"
          value={parameters[ConfigKeys.cursor]}
          onChange={(v) => onInputChange(ConfigKeys.cursor, v)}
          labelWidth={labelWidth}
        />
        {/* //TODO: Do we need it ? */}
        {/* <Input type="checkbox" label="Bold text"
          style={{
            label: labelStyle,
            input: { marginLeft: 0 },
          }}
          className={styles.themeManager__field}
          checked={parameters[ConfigKeys.enableBold] === true ||
            parameters[ConfigKeys.enableBold] === null}
          onChange={(v) =>
            typeof v === "boolean" && onInputChange(ConfigKeys.enableBold, v ? true : false)
          }
        /> */}
        <Input
          label="Font size"
          type="number"
          style={{
            label: labelStyle,
            input: { flex: "1 0" },
          }}
          min={6}
          max={32}
          className={styles.themeManager__field}
          value={parameters[ConfigKeys.fontSize]}
          onChange={(v) => onInputChange(ConfigKeys.fontSize, v)}
        />
        <ThemeFontPicker
          labelWidth={labelWidth}
          value={parameters[ConfigKeys.fontFamily] as string | undefined}
          onChange={(v) => onInputChange(ConfigKeys.fontFamily, v)}
          className={styles.themeManager__field}
        />
        <ANSIColors
          parameters={parameters}
          onChange={(v) => onInputChange(ConfigKeys.colorPaletteOverrides, v)}
          style={{ marginBottom: 4 }}
        />
        <ThemeCard parameters={parameters} />
      </div>
      <div className={styles.themeManager__actions}>
        {terminal!.preferencesTouched ? (
          <Button
            variant="secondary"
            onClick={onReset}
            style={{ marginRight: 'auto' }}
          >
            Reset to defaults
          </Button>
        ): null}
        {hasChanges && (
          <Button variant="secondary" onClick={onRevertChanges}>
            Revert changes
          </Button>
        )}
        <Button variant="primary" onClick={onCancel}>
          Close
        </Button>
      </div>
    </div>
  );
};

export default ThemeManager;
