import type { XTerminal } from "../utils/xterm/xterm-terminal";

export type ThemeManagerProps = {
  onCancel: () => void;
  terminal?: XTerminal;
};
