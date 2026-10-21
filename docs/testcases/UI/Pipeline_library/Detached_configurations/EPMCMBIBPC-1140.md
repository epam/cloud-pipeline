# Validation of config with empty parameters in detach configuration

Test verifies that a detach configuration attached to a pipeline configuration with empty parameter values shows those values as editable and preserves edits across saves.

**Prerequisites**:
- An existing pipeline with parameters of all types (parameter value fields should be empty)

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-1112](EPMCMBIBPC-1112.md) case |  |
| 2 | Click on the created configuration |  |
| 3 | Click on **+ ADD** button |  |
| 4 | Specify a valid name for a new configuration, click **Create** button |  |
| 5 | Click on the tab with the just-created configuration |  |
| 6 | Click on the field opposite the **Pipeline** label |  |
| 7 | Select the pipeline from the prerequisites in the appeared pop-up window |  |
| 8 | Click on the pipeline version |  |
| 9 | Click **OK** button |  |
| 10 | Click **Yes** in the appeared pop-up window | Parameters matching the pipeline's configuration are displayed for the detach configuration |
| 11 | Click **Save** button, refresh the page | The field values aren't changed |
| 12 | Specify a new value into the **value** field of the **String** parameter | The **String** parameter's value is changed |
| 13 | Repeat step 11 | The field values aren't changed, the **String** parameter's value stays changed |
| 14 | Specify a new value into the **value** field of any **path** parameter | The **path** parameter's value is changed |
| 15 | Repeat step 11 | The field values aren't changed, the **path** parameter's value stays changed |
| 16 | Try to change a value in the **name** field of the **String** parameter | Nothing changes, the **name** field is disabled |
