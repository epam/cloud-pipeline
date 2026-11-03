# Run pipeline that have configured output in JSON file

Test verifies that an Output parameter path configured in `config.json` saves the pipeline's output file to that path, and that the parameter's name is fixed on the launch form.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open library |  |
| 2 | Click **+ Create v**, in the appeared list select **Storages** -> **Create new object storage** |  |
| 3 | Specify a valid value in the **Storage path** field in the appeared window, click **Create** button |  |
| 4 | Click **+ Create v**, in the appeared list select **Pipeline** -> **SHELL** |  |
| 5 | Specify a valid pipeline name in the appeared window, click **Create** button |  |
| 6 | Open the storage created at step 3 |  |
| 7 | Click **+ Create v**, in the appeared list select **Folder** item |  |
| 8 | Specify a valid folder name in the appeared window, click **OK** button |  |
| 9 | Open the pipeline created at step 5 |  |
| 10 | Click on the pipeline version, choose **CODE** tab |  |
| 11 | Click on `config.json` file and edit it: add the parameter `result` with `type: output` and `value: {full path to the folder created at step 8}`; set `required: true` for that parameter; save the edited file |  |
| 12 | Add a script for saving files into the code file (replace the code file with the attached one) |  |
| 13 | Click **Run** button | On the launch pipeline page, the parameter `result` is displayed in the parameters section, the field with the parameter name is disabled for editing |
| 14 | Click **Launch** button |  |
| 15 | Wait for it to finish |  |
| 16 | Open the storage created at step 3 |  |
| 17 | Open the folder with the path specified at step 11 | The file created in the pipeline code (step 12) is displayed |
