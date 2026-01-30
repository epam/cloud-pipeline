/**
 * Common types and interfaces for CLI
 */

export interface GlobalCommandOptions {
  apiUrl?: string;
  apiToken?: string;

  connectionTimeout?: string;

  logLevel?: string;
  user?: string;
  noclean?: boolean;
  debug?: boolean;
  trace?: boolean;
}

export interface TunnelStartCommandOptions extends GlobalCommandOptions {
  localPort?: string;
  remotePort?: string;
  ssh?: boolean;
  sshPath?: string;
  sshHost?: string;
  sshUser?: string;
  sshKeep?: boolean;
  direct?: boolean;
  foreground?: boolean;
  keepExisting?: boolean;
  keepSame?: boolean;
  replaceExisting?: boolean;
  replaceDifferent?: boolean;
  ignoreExisting?: boolean;
  ignoreOwner?: boolean;
  region?: string;
  logFile?: string;
  timeout?: string;
  timeoutStop?: string;
}

export interface TunnelStopCommandOptions extends GlobalCommandOptions {
  localPort?: string;
  force?: boolean;
  timeoutStop?: string;
}
