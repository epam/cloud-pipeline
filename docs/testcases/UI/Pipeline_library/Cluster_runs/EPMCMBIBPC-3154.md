# Hybrid auto-scaled cluster: CPU "deadlock"

Test verifies that a job requesting more CPU than fits on one node stays queued (deadlocked) until a second job frees the capacity, spinning up a hybrid auto-scaled worker.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Repeat steps 1-5 of the [EPMCMBIBPC-975](EPMCMBIBPC-975.md) case |  |
| 2 | Input a valid value into the **Node type** field (e.g. `m5.large`) |  |
| 3 | Input a valid value into the **Disk (Gb)** field |  |
| 4 | Click the **Configure cluster** control |  |
| 5 | Select the **Auto-scaled cluster** tab |  |
| 6 | Set the **Enable Hybrid cluster** checkbox |  |
| 7 | Click **OK** button |  |
| 8 | Click the **Advanced** section |  |
| 9 | Set the **Start idle** checkbox |  |
| 10 | Click the **Launch** button |  |
| 11 | Click the **Launch** button in the appeared pop-up window |  |
| 12 | Click the just-launched pipeline name at the **ACTIVE RUNS** tab |  |
| 13 | Wait until the **SSH** button appears |  |
| 14 | Click the **SSH** button |  |
| 15 | In the appeared terminal tab, run the command `qsub -b y -pe local 150 sleep 5m` | The command output is `Your job 1 ("sleep") has been submitted` |
| 16 | Wait 20 seconds |  |
| 17 | Run the command `qstat` | The command output is absent (empty) |
| 18 | Close the terminal tab |  |
| 19 | Click the **GridEngineAutoscaling** task at the **Run logs** page | The following text appears in the logs form: `The following jobs cannot be satisfied with the requested resources and therefore they will be rejected: 1 (150 cpu)` |
| 20 | Repeat step 14 |  |
| 21 | In the appeared terminal tab, run the command `qsub -b y -pe local 50 sleep 5m` |  |
| 22 | Repeat step 18 |  |
| 23 | Wait 1 minute | The **Nested runs** label with the run ID appears |
| 24 | Stop the appeared nested run |  |
| 25 | Stop the run launched at step 11 |  |
