# Validation of adding new config feature

Test verifies that adding a new configuration creates a matching section in `config.json`.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-795](EPMCMBIBPC-795.md) case |  |
| 2 | Click on **+ ADD** button |  |
| 3 | Specify a valid configuration name into the **Configuration name** field in the appeared pop-up window (e.g. `test_conf`) |  |
| 4 | Check that the **Template** field isn't empty |  |
| 5 | Click **CREATE** button | A new tab with the name specified at step 3 appears in the configuration tabs list |
| 6 | Click on **CODE** tab |  |
| 7 | Click on `config.json` file | A new section appears with a run configuration whose name equals the one entered at step 3 |
