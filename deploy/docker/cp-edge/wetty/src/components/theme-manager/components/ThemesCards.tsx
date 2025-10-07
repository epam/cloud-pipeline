import { useMemo } from "react";
import { ConfigKeys, type TerminalTheme } from "../../utils/types";
import { getAlphaFromRgba } from "../../utils/colors";

type ThemeCardProps = {
  themeConfig: TerminalTheme;
};

export default function ThemeCard(props: ThemeCardProps) {
  const { themeConfig } = props;
  const cursorColor = themeConfig[ConfigKeys.cursorColor];
  const alpha = useMemo(() => (cursorColor ? getAlphaFromRgba(cursorColor) : null), [cursorColor]);
  const colorPaletteOverrides = themeConfig[ConfigKeys.colorPaletteOverrides] || {};
  return (
    <div
      style={{
        width: '100%',
        color: themeConfig[ConfigKeys.foregroundColor],
        display: "flex",
        flexDirection: "column",
        gap: 1,
        fontSize: "smaller",
        border: "1px solid var(--color-border-dark)",
        borderRadius: "var(--border-radius)",
        overflow: "hidden",
        boxShadow: "0 1px 2px 0 rgba(0, 0, 0, 0.05)",
        marginBottom: 10,
        fontFamily: themeConfig[ConfigKeys.fontFamily] as string || 'inherit',
      }}
    >
      <div
        style={{
          display: "flex",
          flexDirection: "column",
          gap: 1,
          backgroundColor: themeConfig[ConfigKeys.backgroundColor],
          flexGrow: 1,
          justifyContent: "flex-end",
          padding: 5,
        }}
      >
        <span style={{ color: colorPaletteOverrides[10]}}>{'>'}ls</span>
        <div style={{ display: "flex", gap: 8 }}>
          <span>dir</span>
          <span>executable</span>
          <span style={{ color: themeConfig[ConfigKeys.foregroundColor] }}>file</span>
        </div>
      </div>
      <div
        style={{
          backgroundColor: themeConfig[ConfigKeys.backgroundColor],
          display: "flex",
          alignItems: "center",
          padding: '0 5px',
          gap: 8,
        }}
      >
        <span style={{
          color: colorPaletteOverrides[14],
        }}>~/Documents</span>
        <span>mkdir</span>
        <div
          style={{
            width: "8px",
            height: 15,
            backgroundColor: themeConfig[ConfigKeys.cursorColor],
            margin: 5,
            opacity: alpha !== null ? alpha : 1,
          }}
        />
      </div>
    </div>
  );
}
