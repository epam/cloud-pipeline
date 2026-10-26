# Validation of add configuration from pipeline

Test verifies linking a new detach configuration to an existing pipeline's configuration for the first time.

**Prerequisites**:
- The existing pipeline with several configurations

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-1091](EPMCMBIBPC-1091.md) case |  |
| 2 | Click the created configuration |  |
| 3 | Click on the field opposite the **Pipeline** label | A pop-up window appears containing the library-tree on the left panel and a list of subfolders on the right panel |
| 4 | Select the pipeline from the prerequisites in the appeared pop-up window | Pipeline versions appear on the right panel; there is a combobox opposite each pipeline version |
| 5 | Click on the pipeline version | A special mark (`anticon anticon-check-circle`) appears opposite the selected version name |
| 6 | Click **OK** button |  |
| 7 | Click **Yes** in the appeared pop-up window | <li> all detach configuration fields are filled with values equal to the parameters of the selected pipeline's configuration <li> an **Estimated price per hour** label with a value appears opposite the **Name** field |
