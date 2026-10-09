# Validation of hybrid auto-scaled cluster

Test verifies that a hybrid auto-scaled cluster spins up a worker of the same instance family but not necessarily the same size, sized to satisfy a queued job's core count.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Repeat steps 1-5 of the [EPMCMBIBPC-975](EPMCMBIBPC-975.md) case |  |
| 2 | Input a valid value into the **Node type** field (e.g. `m5.large`) |  |
| 3 | Save the CPU count for the node type selected at step 2 |  |
| 4 | Input a valid value into the **Disk (Gb)** field |  |
| 5 | Click the **Configure cluster** control |  |
| 6 | Select the **Auto-scaled cluster** tab |  |
| 7 | Set the **Enable Hybrid cluster** checkbox |  |
| 8 | Click **OK** button |  |
| 9 | Click the **Advanced** section |  |
| 10 | Set the **Cmd template** to `qsub -b y -pe local <CPU_count + 1> sleep 15m && sleep infinity`, where `<CPU_count>` is the CPU count saved at step 3 |  |
| 11 | Click the **Launch** button |  |
| 12 | Click the **Launch** button in the appeared pop-up window |  |
| 13 | Click the just-launched pipeline name at the **ACTIVE RUNS** tab |  |
| 14 | Expand the **Parameters** section | Text appears that contains:<br>`CP_CAP_AUTOSCALE: true`<br>`CP_CAP_AUTOSCALE_WORKERS: 1`<br>`CP_CAP_AUTOSCALE_HYBRID: true` |
| 15 | Wait until the **SSH** button appears |  |
| 16 | Click the **SSH** button |  |
| 17 | In the appeared terminal tab, run the command `qhost` | The command output contains a table with 2 rows and the following values in the **HOSTNAME** column: `global` and `<pipeline_name>-<RunID>` (where `<pipeline_name>` is the name of the pipeline created at step 1) |
| 18 | Run the command `qstat` | The command output contains a table with 1 row: **job-ID** `1`, **state** `qw`, **slots** `<CPU_count + 1>` (where `<CPU_count>` is the count saved at step 3) |
| 19 | Close the terminal tab |  |
| 20 | At the **Run logs** page wait until the **Nested runs** label appears |  |
| 21 | Click the run ID near the **Nested runs** label | The node type is different from the one selected at step 2: the node family is the same (e.g. `m5`), but the node type in the family is not the same (e.g. not `large`) |
| 22 | Repeat steps 15-17 | The command output contains a table with 3 rows |
| 23 | Repeat step 18 | The command output contains a table with 1 row: **state** `r`, **queue** `main.q@pipeline-<RunID>` (where `<RunID>` is the nested run ID that appeared after step 20) |

**After**:
- Stop the child run first, then the parent run launched at step 12
