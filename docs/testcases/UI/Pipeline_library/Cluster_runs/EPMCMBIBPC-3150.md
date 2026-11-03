# Validation of GE cluster

Test verifies launching a GridEngine cluster: the master/worker setup tasks and that `qhost`/`qstat`/`qsub` behave correctly across both nodes.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Repeat steps 1-8 of the [EPMCMBIBPC-975](EPMCMBIBPC-975.md) case |  |
| 2 | Save the CPU count for the selected node type |  |
| 3 | Set the **Enable GridEngine** checkbox |  |
| 4 | Click **OK** button | The name of the **Configure cluster** control becomes **GridEngine Cluster (1 child node)** |
| 5 | Expand the **Advanced** section |  |
| 6 | Set the **Start idle** checkbox |  |
| 7 | Click **Launch** button |  |
| 8 | Click **Launch** button in the appeared pop-up window |  |
| 9 | Click the **+** button near the just-launched pipeline name at the **ACTIVE RUNS** tab |  |
| 10 | Save the RunIDs of the parent and the child pipeline runs |  |
| 11 | Click the just-launched pipeline name on the **ACTIVE RUNS** tab |  |
| 12 | Expand the **Parameters** section | Text appears that contains `CP_CAP_SGE: true` |
| 13 | Wait until the **SSH** button appears |  |
| 14 | Click the **SGEMasterSetup** task | The log window contains the row `SGE master node was successfully configured` |
| 15 | Click the **SGEMasterSetupWorkers** task | The log window contains the row `All execution hosts are connected` |
| 16 | Click the **SSH** button |  |
| 17 | In the appeared terminal tab, run the command `qhost` | The command output contains a table with the following values in the **HOSTNAME** column: `global`, `<pipeline_name>-<RunID>` (parent run ID saved at step 10), `<pipeline_name>-<RunID>` (child run ID saved at step 10) |
| 18 | Run the command `qstat` | The command output is absent |
| 19 | Run the command `qsub -b y -t 1:10 sleep 10m` | The command output is `Your job-array 1.1-10:1 ("sleep") has been submitted` |
| 20 | Repeat step 18 | The command output is not absent; the **queue** table column contains `<pipeline_name>-<RunID>` for both the parent and child runs saved at step 10 |

**After**:
- Stop the child runs first, then the parent run launched at step 8
