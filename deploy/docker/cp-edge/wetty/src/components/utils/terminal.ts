import {
  ThemeName,
  SocketEvent,
  type HtermTerminal,
  type SocketLike,
  type HtermPrefs,
  ConfigKeys,
} from "./types";
import { hterm } from "../../lib/hterm_all.js";
import socketIO from "socket.io-client";
import { DEFAULT_THEMES } from "./themes.js";
import {enableResources} from "./terminal-resources.ts";

export function getPreferenceInfo(terminal: Terminal, key: string) {
  if (!terminal?.prefs) {
    return { termDefault: undefined, themeDefault: undefined };
  }
  const termDefault = terminal!.prefs!.getDefault(key);
  const themeDefault = DEFAULT_THEMES[terminal?.currentTheme]?.[key];
  const value = terminal!.prefs!.get(key);
  return { value, termDefault, themeDefault };
}

const getThemeConfig = (terminal: Terminal, resetToDefaults = false) => {
  const keys = [
    ...new Set([
      ...Object.values(DEFAULT_THEMES).flatMap((theme) => Object.keys(theme)),
      ...Object.values(ConfigKeys),
    ]),
  ];
  const config = keys.reduce<{ [key: string]: unknown }>((config, key) => {
    const { value, termDefault, themeDefault } = getPreferenceInfo(
      terminal!,
      key
    );
    if (resetToDefaults) {
      config[key] = themeDefault ?? termDefault;
    } else {
      config[key] = value === termDefault ? themeDefault ?? termDefault : value;
    }
    return config;
  }, {});
  return config;
};

export class Terminal {
  private static instance: Terminal | null = null;
  private socket: SocketLike | null = null;
  public term: HtermTerminal | null = null;
  public prefs: HtermPrefs | null = null;
  private buffer: string = "";
  public currentTheme: string = localStorage.getItem('theme') || ThemeName.DEFAULT;
  private readonly origin: string;
  private isConnected: boolean = false;
  private initialized: boolean = false;

  constructor() {
    this.origin = location.origin;
    if (WEB_SSH_ORIGIN !== "") {
      this.origin = WEB_SSH_ORIGIN;
    }
  }

  static getInstance(): Terminal | null {
    return Terminal.instance;
  }

  static createInstance(): Terminal {
    if (!Terminal.instance) {
      Terminal.instance = new Terminal();
    }
    return Terminal.instance;
  }

  async initialize(): Promise<void> {
    console.log('Socket.io -> initialization');
    const socket = this.socket = socketIO(this.origin, { path: "/ssh/socket.io" });
    socket.on(SocketEvent.CONNECT, () => {
      console.log('Socket.io -> "connect" event');
      this.isConnected = true;
      this.resizeTerminal();
    });
    socket.on(SocketEvent.OUTPUT, (data?: unknown) => {
      if (typeof data === "string") {
        this.receive(data);
      }
    });
    socket.on(SocketEvent.DISCONNECT, () => {
      console.log('Socket.io -> "disconnect" event');
      this.isConnected = false;
      console.log("Socket.io connection closed");
    });
    let themeRequest: string | undefined;
    const setTheme = async (theme?: string) => {
      const t = theme ?? themeRequest;
      if (t) {
        if (this.initialized) {
          console.log(`term -> set theme to "${t}"`);
          await this.setTheme(t);
        } else {
          console.log(`term -> set theme request to "${t}" (waiting for initialization)`);
          themeRequest = t;
        }
      }
    };
    socket.on(SocketEvent.THEME, (sshTheme: unknown) => {
      console.log('Socket.io -> "term.theme" event, payload:', sshTheme);
      if (typeof sshTheme === "string") {
        setTheme(sshTheme);
      }
    });
    await this.initializeHterm();
    await setTheme();
    console.log('Socket.io -> emitting ready event');
    socket.emit(SocketEvent.READY, 'ready');
  }

  private async initializeHterm(): Promise<void> {
    const term = this.term = new hterm.Terminal();
    if (!term) {
      console.error('term -> hterm not initialized');
      throw new Error("Hterm not initialized");
    }
    window.term = term;
    this.prefs = term.prefs_;
    const terminalElement = document.getElementById("terminal");
    if (!terminalElement) {
      console.error('term -> terminal element not found');
      throw new Error("Terminal element not found");
    }
    await new Promise<void>((resolve) => {
      console.log('term -> decorating terminal');
      term.decorate(terminalElement);
      term.onTerminalReady = () => {
        console.log('term -> received on ready event');
        const io = term.io.push();
        io.onVTKeystroke = this.send;
        io.sendString = this.send;
        io.onTerminalResize = this.resize;
        term.installKeyboard();
        term.setCursorPosition(0, 0);
        term.setCursorVisible(true);
        enableResources(term.document_);
        this.resizeTerminal();
        if (this.buffer) {
          term.io.writeUTF16(this.buffer);
          this.buffer = "";
        }
        resolve();
      };
    });
    console.log('term -> ready');
    await this.setTheme(this.currentTheme);
    this.initialized = true;
    console.log('term -> initialized');
  }

  async setTheme(themeName: string): Promise<void> {
    if (this.term && this.prefs) {
      this.term.setProfile(themeName, async () => {
        themeName = themeName.toLowerCase();
        if (!DEFAULT_THEMES[themeName]) {
          console.error(`${themeName} theme not found`);
          themeName = ThemeName.DEFAULT;
        }
        this.currentTheme = themeName;
        const config = getThemeConfig(this);
        await this.prefs!.importFromJson(config);
        this.prefs!.set("audible-bell-sound", "");
        localStorage.setItem('theme', themeName);
      });
    }
  }

  send = (text: string): void => {
    if (this.socket && this.isConnected) {
      this.socket.emit(SocketEvent.INPUT, text);
    }
  };

  private receive = (data: string): void => {
    if (!this.term) {
      this.buffer += data;
      return;
    }
    this.term.io.writeUTF16(data);
  };

  resize = (cols: number, rows: number): void => {
    if (this.socket && this.isConnected) {
      console.log(`term -> resize (${cols} x ${rows})`);
      console.log(`Socket.io -> emitting resize event (${cols} x ${rows})`);
      this.socket.emit(SocketEvent.RESIZE, { cols, rows });
    } else {
      console.log(`term -> skipping resize (${cols} x ${rows}), not connected`);
    }
  };

  resizeTerminal = () => {
    if (this.term && this.term.screenSize) {
      this.resize(this.term.screenSize.width, this.term.screenSize.height);
    }
  };

  focusTerminal = (): void => {
    const terminalDiv = document.getElementById("terminal");
    if (terminalDiv) {
      terminalDiv.focus();
    }
  };

  resetTheme = async (theme: string) => {
    theme = theme.toLowerCase();
    const newTheme = DEFAULT_THEMES[theme];
    if (newTheme) {
      const config = getThemeConfig(this, true);
      await this.prefs!.importFromJson(config);
    }
  };

  disconnect = (): void => {
    if (this.socket) {
      this.socket.disconnect();
      this.socket = null;
    }
    this.isConnected = false;
  };

  clearTerminal(): void {
    if (this.term) {
      this.term.wipeContents();
    }
  }
}

export async function initializeTerminal(): Promise<Terminal> {
  const terminal = Terminal.createInstance();
  await terminal.initialize();
  return terminal;
}
