// Global CLI options shared across all commands
export interface GlobalOptions {
  logLevel?: "ERROR" | "WARNING" | "INFO" | "DEBUG";
  user?: string;
  noclean?: boolean;
  debug?: boolean;
  trace?: boolean;
}

// Options for tunnel start command
export interface TunnelStartOptions extends GlobalOptions {
  localPort?: number;
  remotePort: number; // Single port or range
  connectionTimeout?: number;
  ssh?: boolean;
  sshPath?: string;
  sshHost?: string;
  sshUser?: string | string[];
  sshKeep?: boolean;
  direct?: boolean;
  logFile?: string;
  timeout?: number;
  timeoutStop?: number;
  foreground?: boolean;
  keepExisting?: boolean;
  keepSame?: boolean;
  replaceExisting?: boolean;
  replaceDifferent?: boolean;
  ignoreExisting?: boolean;
  ignoreOwner?: boolean;
  retries?: number;
  region?: string;
}


// Options for tunnel stop command
export interface TunnelStopOptions extends GlobalOptions {
  localPort?: number;
  force?: boolean;
  timeoutStop?: number;
}

// Options for tunnel list command
export type TunnelListOptions = GlobalOptions;
