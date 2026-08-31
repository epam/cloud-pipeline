import type { Terminal } from "../utils/terminal";

export type ThemeManagerProps = {
  onCancel: () => void;
  terminal?: Terminal;
};
