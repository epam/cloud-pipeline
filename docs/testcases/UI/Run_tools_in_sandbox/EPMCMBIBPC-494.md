# Launch tool in sandbox

Test verifies launching a tool and that it appears on the ACTIVE RUNS tab with a waiting indicator.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-493](EPMCMBIBPC-493.md) case |  |
| 2 | Click **Run** button |  |
| 3 | Click **Launch** button in the appeared pop-up window | <li> the **RUNS** page opens, the **ACTIVE RUNS** tab is active <li> there is at least one pipeline with a **waiting** indicator; the **Run** field shows the pipeline's ID; the **Docker image** field shows the name of the launched tool and its version (`latest`); the **Started** field shows the time and date |
