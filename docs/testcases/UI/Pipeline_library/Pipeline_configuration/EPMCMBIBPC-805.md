# Validation of configuration remove

Test verifies removing a non-default configuration, with a confirmation step.

**Prerequisites**:
- Perform the [EPMCMBIBPC-804](EPMCMBIBPC-804.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Click on **CONFIGURATION** tab, select the configuration from step 1 of the [EPMCMBIBPC-799](EPMCMBIBPC-799.md) case |  |
| 2 | Click **Remove** button |  |
| 3 | Click **No** button in the appeared pop-up | The pop-up is closed |
| 4 | Repeat step 2 |  |
| 5 | Click **Yes** button in the appeared pop-up | The pop-up is closed, the page refreshes, the configuration selected at step 1 is removed from the configurations tabs list |
| 6 | Click on **CODE** tab |  |
| 7 | Click on the `config.json` file | The section of the configuration selected at step 1 doesn't display in the `config.json` file |
