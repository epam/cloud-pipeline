# Hybrid auto-scaled cluster: CPU "deadlock" with additional restrictions

Test verifies that the `CP_CAP_AUTOSCALE_HYBRID_MAX_CORE_PER_NODE` parameter rejects a job whose CPU request exceeds the configured per-node core limit.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Repeat steps 1-9 of the [EPMCMBIBPC-3154](EPMCMBIBPC-3154.md) case |  |
| 2 | Click the **Add system parameter** button |  |
| 3 | Select the row `CP_CAP_AUTOSCALE_HYBRID_MAX_CORE_PER_NODE` in the list |  |
| 4 | Click **OK** button |  |
| 5 | In the value field for the parameter specify `40` |  |
| 6 | Click the **Launch** button |  |
| 7 | Click the **Launch** button in the appeared pop-up window |  |
| 8 | Click the just-launched pipeline name at the **ACTIVE RUNS** tab |  |
| 9 | Wait until the **SSH** button appears |  |
| 10 | Click the **SSH** button |  |
| 11 | In the appeared terminal tab, run the command `qsub -b y -pe local 50 sleep 5m` |  |
| 12 | Wait 20 seconds |  |
| 13 | Run the command `qstat` | The command output is absent (empty) |
| 14 | Close the terminal tab |  |
| 15 | Click the **GridEngineAutoscaling** task at the **Run logs** page | The following text appears in the logs form: `The following jobs cannot be satisfied with the requested resources and therefore they will be rejected: 1 (50 cpu)` |
| 16 | Stop the run launched at step 7 |  |
