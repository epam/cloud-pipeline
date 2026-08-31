import { useCallback, useMemo } from "react";
import { ConfigKeys, type TerminalTheme } from "../../../utils/types";
import { getAlphaFromRgba, TERMINAL_ANSI_DEFAULTS, TERMINAL_ANSI_EXTENDED } from "../../../utils/colors";
import styles from "./ThemeCard.module.css";

type ThemeCardProps = {
  themeConfig: TerminalTheme;
};

const ansiCodes = new Array(8).fill(1).map((_, i) => i);

export default function ThemeCard(props: ThemeCardProps) {
  const { themeConfig } = props;
  const cursorColor = themeConfig[ConfigKeys.cursorColor];
  const alpha = useMemo(
    () => (cursorColor ? getAlphaFromRgba(cursorColor) : null),
    [cursorColor]
  );
  const colorPaletteOverrides = useMemo(() => themeConfig[ConfigKeys.colorPaletteOverrides] || {}, [themeConfig]);
  const getANSIColor = useCallback((code: number) => {
    return colorPaletteOverrides[code] || TERMINAL_ANSI_DEFAULTS[code];
  }, [colorPaletteOverrides]);
  return (
    <>
      <span style={{ color: "var(--color-text-secondary)" }}>Theme preview:</span>
      <div
        className={styles.card}
        style={{
          color: themeConfig[ConfigKeys.foregroundColor],
          fontFamily: (themeConfig[ConfigKeys.fontFamily] as string) || "inherit",
        }}
      >
        <div
          className={styles.terminal}
          style={{
            backgroundColor: themeConfig[ConfigKeys.backgroundColor],
          }}
        >
          <span style={{ color: themeConfig[ConfigKeys.foregroundColor] }}>
            {"[root@pipeline ~]# ls -la"}
          </span>
          <div className={styles.fileList}>
            <div className={styles.fileRow}>
              <span>lrwxrwxrwx 1 root root</span>
              <span style={{ color: TERMINAL_ANSI_EXTENDED[51] }}>cloud-data</span>
              <span>{"->"}</span>
              <span style={{ color: TERMINAL_ANSI_EXTENDED[27] }}>/cloud-data/</span>
            </div>
            <div className={styles.fileRow}>
              <span>lrwxrwxrwx 1 root root</span>
              <span style={{ color: TERMINAL_ANSI_EXTENDED[51] }}>workdir</span>
              <span>{"->"}</span>
              <span style={{ color: TERMINAL_ANSI_EXTENDED[27] }}>/common/workdir</span>
            </div>
          </div>
        </div>
        <div
          className={styles.ansiRow}
          style={{
            backgroundColor: themeConfig[ConfigKeys.backgroundColor],
          }}
        >
          {ansiCodes.map((code) => (
            <span
              key={`ansi_${code}`}
              style={{
                color: getANSIColor(code),
              }}
            >
              ansi{code.toString().padStart(2, "0")}
            </span>
          ))}
        </div>
        <div
          className={styles.ansiRow}
          style={{
            backgroundColor: themeConfig[ConfigKeys.backgroundColor],
          }}
        >
          {ansiCodes.map((code) => (
            <span
              key={`ansi_${code}`}
              style={{
                color: getANSIColor(code + 8),
              }}
            >
              ansi{(code + 8).toString().padStart(2, "0")}
            </span>
          ))}
        </div>
        <div
          className={styles.promptRow}
          style={{
            backgroundColor: themeConfig[ConfigKeys.backgroundColor],
          }}
        >
          <span
            style={{
              color: themeConfig[ConfigKeys.foregroundColor],
            }}
          >
            {"[root@pipeline ~]#"}
          </span>
          <span>mkdir</span>
          <div
            className={styles.cursor}
            style={{
                backgroundColor: themeConfig[ConfigKeys.cursorColor],
                opacity: alpha !== null ? alpha : 1,
            }}
          />
        </div>
      </div>
    </>
  );
}
