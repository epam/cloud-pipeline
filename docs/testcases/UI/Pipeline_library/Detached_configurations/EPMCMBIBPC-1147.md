# Validation of run cluster from detach configuration

Test verifies launching a cluster from a detach configuration with two sub-configurations.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-1139](EPMCMBIBPC-1139.md) case |  |
| 2 | Click on **Run v** button |  |
| 3 | Click on **Run cluster** in the appeared list |  |
| 4 | Click **OK** button in the appeared pop-up |  |
| 5 | At the **ACTIVE RUNS** tab click on the **+** button in front of the just-launched pipeline | <li> 2 pipelines are being run <li> the pipeline names equal the ones specified in the detach configuration at the [EPMCMBIBPC-1139](EPMCMBIBPC-1139.md) case |
