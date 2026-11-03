# Validation of auto-scaled cluster

Test verifies that an auto-scaled cluster starts a worker node once demand appears.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open library |  |
| 2 | Create a pipeline (from the SHELL template) |  |
| 3 | Open the created pipeline |  |
| 4 | Click **RUN** button |  |
| 5 | On the appeared page click **Exec environment** |  |
| 6 | Input valid values into the **Node type**, **Disk (Gb)** fields |  |
| 7 | Set the **Cmd template** to: `qsub -b y -e /common/workdir/err -o /common/workdir/out -t 1:10 sleep 1d && sleep infinity` |  |
| 8 | Click **Configure cluster** |  |
| 9 | Click **Auto-scaled cluster** |  |
| 10 | Specify `1` into the **Auto-scaled up to:** field |  |
| 11 | Click **OK** |  |
| 12 | Click **Launch** button |  |
| 13 | Click **Launch** button in the appeared pop-up window | <li> the master node is started; a run appears on the **ACTIVE RUNS** tab, its name in the form `{pipeline name created at step 2}-{runID}` <li> in a few minutes, one child node is started; on the **ACTIVE RUNS** tab, a **+** button appears in front of the run name; clicking it shows the child pipeline, whose **Parent run** field equals the parent-run ID |

**After**:
- Stop the child node first, then the master node
