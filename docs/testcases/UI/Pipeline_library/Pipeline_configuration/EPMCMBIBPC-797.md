# Validation of edit new configuration

Test verifies that editing a new configuration's name, disk and timeout on the Configuration tab is reflected in `config.json`.

**Prerequisites**:
- Perform the [EPMCMBIBPC-796](EPMCMBIBPC-796.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Click on **CONFIGURATION** tab |  |
| 2 | Select the configuration created at step 5 of the [EPMCMBIBPC-796](EPMCMBIBPC-796.md) case |  |
| 3 | Change the value of the **Name** field (e.g. `conf`) |  |
| 4 | Click on **Exec environment** header to expand the section |  |
| 5 | Change the value of the **Disk (Gb)** field (e.g. `23`) |  |
| 6 | Click on **Advanced** header to expand the section |  |
| 7 | Change the value of the **Timeout (min)** field (e.g. `2`) |  |
| 8 | Click **Save** button | A message `Updating {configuration_name} configuration` appears |
| 9 | Click on **CODE** tab |  |
| 10 | Click on `config.json` file | For the non-default configuration section, the following values are displayed: `name`: `{value specified at step 3}`, `instance_disk`: `{value specified at step 5}`, `timeout`: `{value specified at step 7}` |
