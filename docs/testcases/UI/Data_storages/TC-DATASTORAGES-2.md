# Check API backups storages

The test verifies that Cloud Pipeline backups are created every day in the provided storage(s).

**Prerequisites**:
- Admin user
- Backup storage names 
- Path to the current day folder in the storage (e.g. `api/backups`)
- Time appears backup in the hours - the time of the appearance of backup files in the storages in hours (e.g. `8`)

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as admin user from the prerequisites | |
| 2 | Open the **Library** page | |
| 3 | Navigate to the storage from the prerequisites | |
| 4 | Navigate to the folder path from the prerequisites | |
| 5 | Navigate to the current or previous date folder (according to the time backup appears) | |
| 6 | Navigate through the existing folders (`cp-api-srv`, `cp-git`, `cp-api-db`) | All backup files exist and have non-empty sizes |
| 7 | Repeat steps 3-6 for the all storages from the prerequisites | Backup file sizes are the same in the all provided storages |
