# Validation of run single config from detach configuration

Test verifies that changing the Node type in a detached configuration and running it uses the new value.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-1144](EPMCMBIBPC-1144.md) case |  |
| 2 | Open the detached configuration from the [EPMCMBIBPC-1144](EPMCMBIBPC-1144.md) case |  |
| 3 | Expand the **Exec environment** section |  |
| 4 | Select a new value for the **Node type** field (different from the previous one) |  |
| 5 | Click the **Save** button |  |
| 6 | Click the **Run** button |  |
| 7 | Click **OK** button in the appeared pop-up | <li> the pipeline is being run <li> its name equals the one specified in the **Pipeline** field in the **Exec environment** section of the detach configuration |
| 8 | At the **ACTIVE RUNS** tab click on the just-launched pipeline |  |
| 9 | Click the **Instance** collapsed header | The displayed **Node type** value equals the one selected at step 4 |
