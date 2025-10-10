import type { Terminal } from "@xterm/xterm";

export type CommonProps = {
  id?: string,
  className?: string,
  disabled?: boolean,
  style?: React.CSSProperties
};

declare global {
  interface Window {
    term?: Terminal;
  }
}