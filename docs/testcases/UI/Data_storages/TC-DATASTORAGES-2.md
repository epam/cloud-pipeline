# Check API backups storages

The test verifies that Cloud Pipeline backups are created every day in the provided storage(s).

**Prerequisites**:
- Admin user
- The platform runs in AWS
- Backup storage names
- Path to the backups folder in the storage (e.g. `backups`)
- Backup time in hours - the number of hours after midnight UTC by which the backup files of the day appear in the storages (e.g. `8`)

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as admin user from the prerequisites | |
| 2 | Open the **Library** page | |
| 3 | Click **All storages**, open the storage from the prerequisites | |
| 4 | Navigate to the folder path from the prerequisites | |
| 5 | Open the `cp-api-srv` folder, then the folder of the backup date `<YYYY-MM-DD>`: the current UTC date, or the previous one if fewer hours than the backup time from the prerequisites have passed since midnight UTC | The folder has the file `cp-bkp-api-settings-dump-<YYYYMMDD>.tgz` |
| 6 | Open the `cp-git` folder of the path from the prerequisites, then the folder of the backup date | The folder has the files: <li> `cp-bkp-git-settings-dump-<YYYYMMDD>.tgz` <li> `cp-bkp-<timestamp>_<YYYY_MM_DD>_<GitLab version>_gitlab_backup.tar` |
| 7 | Open the `cp-api-db` folder of the path from the prerequisites, then the folder of the backup date | The folder has the file `cp-bkp-api-db-dump-<YYYYMMDD>.sql.gz` |
| 8 | Repeat steps 3-7 for the all storages from the prerequisites | If there are several storages, each backup file has a non-zero size, and the same size in the all provided storages |
