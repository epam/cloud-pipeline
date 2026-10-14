# Change "CMD_template" in config.json

Test verifies that a `cmd_template` value edited in `config.json` is reflected in the launch form's **Cmd template** field.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open library |  |
| 2 | Open the pipeline from the [EPMCMBIBPC-368](EPMCMBIBPC-368.md) case, select version |  |
| 3 | On the pipeline page select **CODE** tab |  |
| 4 | Click on `config.json` file and edit it: change the value of the `cmd_template` item in the `configuration` section, save the edited file |  |
| 5 | Click **RUN** button |  |
| 6 | Click **Advanced** | The text in the **Cmd template** field in the **Advanced** section equals the value specified at step 4 |
