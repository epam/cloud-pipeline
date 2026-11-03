# Validation of change pipeline configuration for detach configuration

Test verifies that switching a detached sub-configuration's linked Pipeline field to a different pipeline/version refills its fields accordingly.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-1129](EPMCMBIBPC-1129.md) case |  |
| 2 | Click on the second configuration (created at step 1) |  |
| 3 | Click on the **Pipeline** field |  |
| 4 | Select a pipeline and version different from the first configuration |  |
| 5 | Click **OK** button |  |
| 6 | Click **Yes** button in the appeared pop-up |  |
| 7 | Click **Save** button |  |
| 8 | Refresh the page | The detach configuration's fields are filled with values equal to the parameters of the pipeline's configuration selected at step 4 |
