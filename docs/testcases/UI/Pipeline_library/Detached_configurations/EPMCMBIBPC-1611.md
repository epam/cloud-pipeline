# Validation of config with filled parameters in detach configuration

Test verifies that a detach configuration attached to a pipeline configuration with filled parameter values displays them read-only and unchanged after save/refresh.

**Prerequisites**:
- Existing pipeline with parameters of all types (parameter value fields should be non-empty)

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-1091](EPMCMBIBPC-1091.md) case |  |
| 2 | Click on the created configuration |  |
| 3 | Click on the field opposite the **Pipeline** label |  |
| 4 | Select the pipeline from the prerequisites in the appeared pop-up window |  |
| 5 | Click on the pipeline version |  |
| 6 | Click **OK** button |  |
| 7 | Click **Yes** in the appeared pop-up window | Parameters matching the pipeline's configuration are displayed for the detach configuration |
| 8 | Click **Save** button, refresh the page | The field values aren't changed |
| 9 | Try to change a value in the **name** or **value** field of any parameter | Nothing changes, all parameter fields are disabled |
