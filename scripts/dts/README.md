# Data Transfer Service Launcher

Launcher script allows deploying Data Transfer Service for local directories' synchronisation to Windows hosts.
It handles Data Transfer Service failures and host restarts allowing to maintain continuous data synchronisation process.

## Install

To install dts either `install_dts.sh` or `InstallDts.ps1` scripts can be used depending on a host platform.
Download the corresponding script and set `API`, `API_TOKEN` and `API_PUBLIC_KEY` environment variable placeholders in it.
Once the script is ready it can be executed from a bash terminal or Powershell console with administrative permissions using the commands below.

```powershell
# Windows
Set-ExecutionPolicy Bypass -Scope Process -Force -Confirm:$false
& "$HOME\Downloads\InstallDts.ps1"
```

```bash
# Linux
bash ~/Downloads/install_dts.sh
```

## Configure

To register new dts the commands below can be used. Notice that DTS name should be the same as in the _Install_ step.

```bash
DTS_NAME="{PUT DTS HOST NAME HERE}"
pipe dts create --name "$DTS_NAME" \
                --url "$DTS_NAME" \
                --prefix "$DTS_NAME"
```

To synchronise some data directory as well as dts logs directory every minute the commands below can be used.

```powershell
# Windows
pipe dts preferences update "$DTS_NAME" -p 'dts.local.sync.rules=[{
                                                "source": "c:\\local\\path\\to\\source\\directory",
                                                "destination": "s3://data/storage/path/to/destination/directory",
                                                "cron": "0 0/1 * ? * *",
                                                "transferTriggers": [
                                                  { "maxSearchDepth": 3, "globMatchers": [ "file_prefix*.extension" ] },
                                                  { "maxSearchDepth": 2, "globMatchers": [ "file_name" ] }
                                                ],
                                                "deleteSource": false
                                            }, {
                                                "source": "c:\\Program Files\\CloudPipeline\\DTS\\logs",
                                                "destination": "s3://data/storage/path/to/logs/directory",
                                                "cron": "0 0/1 * ? * *"
                                            }]'
```

```bash
# Linux
pipe dts preferences update "$DTS_NAME" -p 'dts.local.sync.rules=[{
                                                "source": "/local/path/to/source/directory",
                                                "destination": "s3://data/storage/path/to/destination/directory",
                                                "cron": "0 0/1 * ? * *",
                                                "transferTriggers": [
                                                  { "maxSearchDepth": 3, "globMatchers": [ "file_prefix*.extension" ] },
                                                  { "maxSearchDepth": 2, "globMatchers": [ "file_name" ] }
                                                ],
                                                "deleteSource": false
                                            }, {
                                                "source": "/opt/CloudPipeline/DTS/logs",
                                                "destination": "s3://data/storage/path/to/logs/directory",
                                                "cron": "0 0/1 * ? * *"
                                            }]'
```

To restart dts the command below can be used.

```bash
pipe dts preferences update "$DTS_NAME" -p 'dts.restart.force=true'
```

To enable dts heartbeat the command below can be used.

```bash
pipe dts preferences update "$DTS_NAME" -p 'dts.heartbeat.enabled=true'
```

## Cron

The following cron expressions can be used as a template in synchronisation rules in dts.

- `0 0/1 * ? * *` - every minute (00:00, 00:01, ..., 00:00, 00:01, ...)
- `0 0 * ? * *` - every hour (00:00, 01:00, ..., 00:00, 01:00, ...)
- `0 30 14 ? * *` - every day at 14:30 (14:30, 14:30, ..., 14:30, 14:30, ...)

## Transfer triggers

Some directories (pipeline results, for example) should be synchronized only when a certain file(s) appears in it.
Such file triggers could be configured via `transferTriggers` attribute and contains objects with the following properties:
- `maxDepthSearch` - value, which is limiting the search depth (0 means 'check sync source directory only')
- `globMatchers` - list of UNIX-like glob expressions, describing target file patterns; 
                   In case multiple globs are specified for the same trigger they are interpreted using `OR` logical operator

```json
{
    "source": ...,
    "destination": ...,
    "cron": ...,
    "transferTriggers": [
        {
          "maxSearchDepth": 3, // might be omitted - default value, configured in DTS, will be used instead
          "globMatchers": [
            "file_prefix*.extension",
            "file_name"
          ]
        },
        ...
    ]
}
```
