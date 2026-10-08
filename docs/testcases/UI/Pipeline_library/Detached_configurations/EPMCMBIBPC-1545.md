# Validation of price type field of cluster run

Test verifies that launching a cluster from a detached configuration with two sub-configurations runs each pipeline with its own price type.

**Prerequisites**:
- Some of the functionality is supported by a specific platform only

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-1091](EPMCMBIBPC-1091.md) case |  |
| 2 | Click on the created configuration |  |
| 3 | Click on the field opposite the **Pipeline** label |  |
| 4 | Select the existing pipeline in the appeared pop-up window |  |
| 5 | Click on the pipeline version |  |
| 6 | Click **OK** button |  |
| 7 | Click **Yes** in the appeared pop-up window |  |
| 8 | Click on **+ ADD** button |  |
| 9 | Specify a valid configuration name |  |
| 10 | Click **CREATE** button |  |
| 11 | Repeat steps 3-7 for the just-created configuration |  |
| 12 | For the first configuration (created at step 1), in the **Advanced** section click the **Price type** dropdown list and select **Spot** (this item could be unavailable on a specific platform, e.g. for Azure) |  |
| 13 | For the second configuration (created at step 10), in the **Advanced** section click the **Price type** dropdown list and select **On-demand** |  |
| 14 | Click **Run v** button |  |
| 15 | Click **Run cluster** in the appeared dropdown list |  |
| 16 | Click **OK** button in the appeared pop-up |  |
| 17 | Open the run logs for both pipelines |  |
| 18 | Click on the **Instance** section for both pipelines' logs | <li> the first pipeline is running with **Spot** price type <li> the second pipeline is running with **On-demand** price type |
