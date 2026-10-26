# Validation of auto-scaled cluster with default child nodes

Test verifies that a cluster configured with a default child node count starts that many workers immediately, then auto-scales up and back down.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open library |  |
| 2 | Create a pipeline (from the SHELL template) |  |
| 3 | Open the created pipeline |  |
| 4 | Click **RUN** button |  |
| 5 | On the appeared page click **Exec environment** and **Advanced** |  |
| 6 | Input valid values into the **Node type**, **Disk (Gb)** fields |  |
| 7 | Set the **Cmd template** to: `qsub -b y -e /common/workdir/err -o /common/workdir/out -t 1:10 sleep 5m && sleep infinity` |  |
| 8 | Click **Configure cluster** |  |
| 9 | Click **Auto-scaled cluster** |  |
| 10 | Click **Setup default child nodes count** |  |
| 11 | Specify `2` into the **Auto-scaled up to** field |  |
| 12 | Specify `1` into the **Default child nodes** field |  |
| 13 | Click **OK** button |  |
| 14 | Click **Launch** button |  |
| 15 | Click **Launch** button in the appeared pop-up window | The master node is started; a run appears on the **ACTIVE RUNS** tab, its name in the form `{pipeline name created at step 2}-{runID}` |
| 16 | On the **Runs** page (**ACTIVE RUNS** tab), click the **+** button in front of the pipeline launched at step 14 | The first child pipeline is displayed below the parent one, name in the form `{pipeline name created at step 2}-{first child runID}`; the first child pipeline's **Parent run** field equals the parent-run ID |
| 17 | Click on the parent pipeline's row |  |
| 18 | At the opened run log page wait until the **GridEngineAutoscaling** task appears |  |
| 19 | Click the **GridEngineAutoscaling** task |  |
| 20 | Wait until `Additional worker with host={second child pipeline name} and instance type={instance type} has been created` appears (the instance type is the same as the node type selected at step 6) |  |
| 21 | Open the **Runs** page, click the **+** button in front of the pipeline launched at step 14 | A second child pipeline is displayed below the parent one, name in the form `pipeline-{second child runID}`; its **Parent run** field equals the parent-run ID; there are 3 running pipelines: the parent and 2 child ones |
| 22 | Click on the parent pipeline's row |  |
| 23 | Click the **GridEngineAutoscaling** task |  |
| 24 | Wait until `Additional worker with host={second child pipeline name} has been stopped` appears |  |
| 25 | Open the **Runs** page, click the **+** button in front of the pipeline launched at step 14 | The parent pipeline and first child pipeline have running status; the second child pipeline has **Completed** status |

**After**:
- Stop the default child node first, then the master node
