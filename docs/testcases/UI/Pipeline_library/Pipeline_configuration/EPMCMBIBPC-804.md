# Validation of "Set as default" behavoir

Test verifies that setting a configuration as default is reflected in a new configuration's Template field and in `config.json`.

**Prerequisites**:
- Perform the [EPMCMBIBPC-799](EPMCMBIBPC-799.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Click on **CONFIGURATION** tab, select the configuration edited at the [EPMCMBIBPC-799](EPMCMBIBPC-799.md) case |  |
| 2 | Click **Set as default** button | The page refreshes |
| 3 | Click **+ ADD** button | In the **Create configuration** pop-up, the **Template** field shows the value equal to the configuration name selected at step 1 (`new_conf`) |
| 4 | Close the appeared pop-up |  |
| 5 | Click on **CODE** tab |  |
| 6 | Click on `config.json` file | In the `new_conf` configuration's section, the `default` field has the value `true` |
