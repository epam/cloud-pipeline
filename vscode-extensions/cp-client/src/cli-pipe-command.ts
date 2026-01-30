/**
 * Extended Command class with helper methods for adding option groups
 */

import { Command, CommandOptions, ExecutableCommandOptions } from "commander";

export class PipeCommand extends Command {
    constructor(name?: string) {
        super(name)
    }

    // // @ts-expect-error - Intentionally returning PipeCommand for fluent API
    override createCommand(name?: string): Command {
        const res = new PipeCommand(name);
        return res as unknown as Command;
    }


    // @ts-expect-error - Intentionally returning PipeCommand for fluent API
    override command(
        nameAndArgs: string,
        description?: string,
        opts?: CommandOptions | ExecutableCommandOptions
    ): PipeCommand {
        let res: PipeCommand;
        if (description) {
            res = super.command(nameAndArgs, description, opts);
        } else {
            res = super.command(nameAndArgs, opts) as PipeCommand;
        }
        return res;
    }

    // @ts-expect-error - Intentionally returning PipeCommand for fluent API
    override description(text: string): PipeCommand {
        const res = super.description(text) as PipeCommand;
        return res;
    }

    // override option(
    //     flags: string,
    //     description?: string,
    //     fn?: ((value: string, previous: any) => any) | any,
    //     defaultValue?: any,
    // ): any {
    //     let result: any;
    //     if (fn !== undefined && description) {
    //         result = super.option(flags, description, fn, defaultValue);
    //     } else if (description) {
    //         result = super.option(flags, description);
    //     } else {
    //         result = super.option(flags);
    //     }
    //     return result as PipeCommand;
    // }

    /**
     * Add common options for all commands
     */
    addCommonOptions(): this {
        return this
            .option("-ct, --connection-timeout <seconds>", "Connection timeout in seconds")

            .option("-v, --log-level <level>", "Log level")
            .option("-u, --user <user>", "User name (admin only)")
            .option("--debug", "Enable debug logging")
            .option("--trace", "Enable trace logging");
    }

    /**
     * Add options for tunnel list command
     */
    addTunnelListOptions(): this {
        return this.addCommonOptions();
    }

    /**
     * Add options for run list/view-runs command
     */
    addRunListOptions(): this {
        return this
            .option("-s, --status <status>", "List pipelines with specific status (ANY/FAILURE/PAUSED/RUNNING/STOPPED/SUCCESS)")
            .option("-df, --date-from <date>", "List runs started after specified date")
            .option("-dt, --date-to <date>", "List runs completed before specified date")
            .option("-p, --pipeline <pipeline>", "List history for specific pipeline (<name>@<version> or <name>)")
            .option("-pid, --parent-id <id>", "List runs for specific parent pipeline run", parseInt)
            .option("-f, --find <text>", "Search runs with substring in parameters")
            .option("-t, --top <n>", "Display top N records", parseInt)
            .option("-nd, --node-details", "Display node details (for single run)")
            .option("-pd, --parameters-details", "Display parameters (for single run)")
            .option("-td, --tasks-details", "Display tasks (for single run)")
            .option("-uf, --user-filter <users>", "Filter by users (comma-separated)")
            .option("--tags-details", "Display tags details (for single run)")
            .addCommonOptions();
    }

    /**
     * Add options for tunnel start command
     */
    addTunnelStartOptions(): this {
        return this
            .option("-lp, --local-port <port>", "Local port (single or range 4567-4569)")
            .option("-rp, --remote-port <port>", "Remote port (default 22)")
            .option("-s, --ssh", "Configure passwordless SSH")
            .option("-sp, --ssh-path <path>", "SSH config path")
            .option("-sh, --ssh-host <host>", "SSH host")
            .option("-su, --ssh-user <user>", "SSH user (can be used multiple times)")
            .option("-sk, --ssh-keep", "Keep SSH config after tunnel stops")
            .option("-d, --direct", "Direct connection without proxy")
            .option("-f, --foreground", "Run tunnel in foreground")
            .option("-ke, --keep-existing", "Skip if tunnel already exists")
            .option("-ks, --keep-same", "Skip if same config already exists")
            .option("-re, --replace-existing", "Replace existing tunnel")
            .option("-rd, --replace-different", "Replace if config differs")
            .option("--ignore-existing", "Ignore existing tunnels")
            .option("--ignore-owner", "Replace tunnels from other users")
            .option("-rg, --region <region>", "Edge region name")
            .option("-l, --log-file <file>", "Log file path")
            .option("-t, --timeout <seconds>", "Tunnel health check timeout")
            .option("-ts, --timeout-stop <seconds>", "Tunnel stop timeout")
            .option("--noclean", "Disable resource cleanup")
            .addCommonOptions();
    }

    /**
     * Add options for tunnel stop command
     */
    addTunnelStopOptions(): this {
        return this
            .option("-lp, --local-port <port>", "Local port to stop")
            .option("-f, --force", "Force stop (SIGKILL)")
            .option("-ts, --timeout-stop <seconds>", "Timeout for graceful stop")
            .addCommonOptions();
    }
}
