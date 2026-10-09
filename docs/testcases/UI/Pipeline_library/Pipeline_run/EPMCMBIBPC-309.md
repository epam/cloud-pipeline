# Check log for task

Test verifies the log content shown for the `Task1`, pipeline-named, and `MountDataStorages` tasks.

**Prerequisites**:
- Perform the [EPMCMBIBPC-305](EPMCMBIBPC-305.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Click on task **Task1** | Text like `001: [YYY-MM-DD HH:mm:ss] Running python pipeline` is displayed |
| 2 | Click on task `{pipeline_name}` | Text like in the attached example (`run_log.txt`) is displayed |
| 3 | Click on task **MountDataStorages** | Text like in the attached example (`MountDataStorages.log`) is displayed |
