# Validation of detach config behavior if user change base pipeline config

Test verifies that a detached configuration's own parameters survive changes made later to the base pipeline configuration it was created from.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-1140](EPMCMBIBPC-1140.md) case |  |
| 2 | Open the pipeline's configuration that was specified for the detach configuration at step 7 of the [EPMCMBIBPC-1140](EPMCMBIBPC-1140.md) case |  |
| 3 | Select the pipeline version, click on **CONFIGURATION** tab |  |
| 4 | Remove some parameters |  |
| 5 | Click **Save** button |  |
| 6 | Open the detach configuration used at step 1 |  |
| 7 | Select the configuration created at step 4 of the [EPMCMBIBPC-1140](EPMCMBIBPC-1140.md) case | The parameters in the **Parameters** section stay unchanged |
| 8 | Click on **Set as default** button |  |
| 9 | Click on **Run v** button |  |
| 10 | Click on **Run cluster** in the appeared list |  |
| 11 | Click **OK** button in the appeared pop-up |  |
| 12 | At the **ACTIVE RUNS** tab click the **+** button in front of the just-launched pipeline | For the child pipelines, the **Parent run** field equals the ID of the parent run |
| 13 | Click on the just-launched pipeline |  |
| 14 | Click on **Instance** collapsed header | The parent-run parameters equal the configuration set as default at step 8 |
