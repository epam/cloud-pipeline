import { useCallback, useMemo } from "react";
import { ConfigKeys, type TerminalTheme } from "../../../utils/types";
import { TERMINAL_ANSI_DEFAULTS, TERMINAL_ANSI_EXTENDED } from "../../../utils/colors";
import styles from "./ThemeCard.module.css";

type ThemeCardProps = {
  parameters: TerminalTheme;
};

const ansiCodes = new Array(8).fill(1).map((_, i) => i);

export default function ThemeCard(props: ThemeCardProps) {
  const { parameters } = props;
  const colorPaletteOverrides = useMemo(() => parameters[ConfigKeys.colorPaletteOverrides] || {}, [parameters]);
  const getANSIColor = useCallback((code: number) => {
    return colorPaletteOverrides[code] || TERMINAL_ANSI_DEFAULTS[code];
  }, [colorPaletteOverrides]);
  return (
    <>
      <span style={{ color: "var(--color-text-secondary)" }}>Theme preview:</span>
      <div
        className={styles.card}
        style={{
          color: parameters[ConfigKeys.foreground],
          fontFamily: (parameters[ConfigKeys.fontFamily] as string) || "inherit",
        }}
      >
        <div
          className={styles.terminal}
          style={{
            backgroundColor: parameters[ConfigKeys.background],
          }}
        >
          <span style={{ color: parameters[ConfigKeys.foreground] }}>
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
            backgroundColor: parameters[ConfigKeys.background],
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
            backgroundColor: parameters[ConfigKeys.background],
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
            backgroundColor: parameters[ConfigKeys.background],
          }}
        >
          <span
            style={{
              color: parameters[ConfigKeys.foreground],
            }}
          >
            {"[root@pipeline ~]#"}
          </span>
          <span>mkdir</span>
          <div
            className={styles.cursor}
            style={{
                backgroundColor: parameters[ConfigKeys.cursor],
                // opacity: alpha !== null ? alpha : 1,
            }}
          />
        </div>
      </div>
    </>
  );
}
