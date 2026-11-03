# Validation of Slurm cluster

Test verifies launching a Slurm cluster: the master/worker setup tasks and that `sinfo`/`srun` report both nodes from the master.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Repeat steps 1-8 of the [EPMCMBIBPC-975](EPMCMBIBPC-975.md) case |  |
| 2 | Set the **Enable Slurm** checkbox |  |
| 3 | Click **OK** button | The name of the **Configure cluster** control becomes **Slurm Cluster (1 child node)** |
| 4 | Expand the **Advanced** section |  |
| 5 | Set the **Start idle** checkbox |  |
| 6 | Click **Launch** button |  |
| 7 | Click **Launch** button in the appeared pop-up window |  |
| 8 | Click the **+** button near the just-launched pipeline name at the **ACTIVE RUNS** tab |  |
| 9 | Save the RunIDs of the parent and the child pipeline runs |  |
| 10 | Click the just-launched pipeline name on the **ACTIVE RUNS** tab |  |
| 11 | Expand the **Parameters** section | Text appears that contains `CP_CAP_SLURM: true` |
| 12 | Wait until the **SSH** button appears |  |
| 13 | Click the **SLURMMasterSetup** task | The log window contains the row `SLURM master node has been successfully configured` |
| 14 | Click the **SLURMMasterSetupWorkers** task | The log window contains the row `All SLURM hosts are connected` |
| 15 | Click the **SSH** button |  |
| 16 | In the appeared terminal tab, run the command `sinfo` | The command output contains a row like:<br>`main.q*      up   infinite      2   idle <pipeline_name>-[<master_RunID>-<worker_RunID>]`, where `<pipeline_name>` is the name of the pipeline created at step 1, `<master_RunID>` is the RunID of the parent run saved at step 9, `<worker_RunID>` is the RunID of the child run saved at step 9 |
| 17 | Run the command `srun -N2 -l /bin/hostname` | The command output contains rows (in any order): `0: <pipeline_name>-<master_RunID>` and `1: <pipeline_name>-<worker_RunID>` |

**After**:
- Stop the child runs first, then the parent run launched at step 7
