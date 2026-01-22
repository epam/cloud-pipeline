export interface GlobalOptions {
    logLevel?: "ERROR" | "WARNING" | "INFO" | "DEBUG";
    user?: string;
    noclean?: boolean;
    debug?: boolean;
    trace?: boolean;
}
export interface TunnelStartOptions extends GlobalOptions {
    localPort?: string;
    remotePort?: string;
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
export interface TunnelStopOptions extends GlobalOptions {
    localPort?: number;
    force?: boolean;
    timeoutStop?: number;
}
export interface TunnelListOptions extends GlobalOptions {
}
