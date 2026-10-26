# Validation of edit configuration in config.json

Test verifies that editing a configuration's name, disk and timeout directly in `config.json` is reflected on the Configuration tab.

**Prerequisites**:
- Perform the [EPMCMBIBPC-797](EPMCMBIBPC-797.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Click on **CODE** tab |  |
| 2 | Click on `config.json` file |  |
| 3 | Click on **EDIT** button |  |
| 4 | For the `conf` configuration specify: change the value of `name` (e.g. `new_conf`), change the value of `instance_disk` (e.g. `15`), change the value of `timeout` (e.g. `1`) |  |
| 5 | Click **SAVE** button |  |
| 6 | Enter a valid commit name in the appeared pop-up window |  |
| 7 | Click **Commit** button |  |
| 8 | Click on **CONFIGURATION** tab |  |
| 9 | Select the configuration with the name specified at step 4 |  |
| 10 | Click on **Exec environment** header to expand the section |  |
| 11 | Click on **Advanced** header to expand the section | <li> the configuration has a name equal to the one specified at step 4 <li> the **Disk (Gb)** field shows the value specified at step 4 <li> the **Timeout (min)** field shows the value specified at step 4 |
