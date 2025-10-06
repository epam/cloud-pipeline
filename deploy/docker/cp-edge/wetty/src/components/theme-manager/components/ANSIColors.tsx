import { useMemo } from "react";
import Input from "../../shared/input";
import { ConfigKeys, type ThemeConfig } from "../types";
import { rgbStringToHex } from "../../utils/colors";

type ANSIColorsProps = {
  themeConfig: ThemeConfig;
  onChange: (colors: Record<string, string>) => void;
};

export default function ANSIColors (props: ANSIColorsProps) {
  const {themeConfig, onChange} = props;
  const colors = useMemo<Record<string, string>>(() => themeConfig[ConfigKeys.colorPaletteOverrides] ?? {}, [themeConfig]);
  const onInputChange = (key: string, value: string) => {
    const newColors = {...colors} as Record<string, string>;
    newColors[String(key)] = value;
    onChange(newColors)
  };

  const onReset = () => {
    onChange({});
  };
  const hasChanges = Object.values(colors).filter(Boolean).length > 0;
  return (
    <div style={{display: 'flex', flexDirection: 'column'}}>
      <div>
        <span style={{color: 'var(--color-text-secondary)'}}>
          ANSI Colors:
        </span>
        {hasChanges
          ? <a style={{marginLeft: 5}} onClick={onReset}>
              Reset
            </a>
          : null}
      </div>
      <div style={{display: 'flex', flexWrap: 'wrap'}}>
        {Array.from({length: 16}).map((_, idx) => {
          const color = colors[idx + 1];
          const key = String(idx + 1);
          return (
            <div key={key} style={{width: 'calc(100% / 8)'}}>
              <Input
                label={key}
                type="color"
                style={{label: {width: '18px'}, input: {padding: 0}}}
                value={color
                  ? rgbStringToHex(colors[key]) as string
                  : ''
                }
                onChange={(value) => onInputChange(key, value as string)}
              />
          </div>
          )
        })}
      </div>
    </div>
  )
}