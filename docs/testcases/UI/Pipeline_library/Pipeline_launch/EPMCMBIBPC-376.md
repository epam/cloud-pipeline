# Validation of "timeout" parameter

Test verifies that a `timeout` value edited in `config.json` is reflected in the launch form's **Timeout (min)** field.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open library |  |
| 2 | Open the pipeline from the [EPMCMBIBPC-368](EPMCMBIBPC-368.md) case, select version |  |
| 3 | On the pipeline page select **CODE** tab |  |
| 4 | Click on `config.json` file and edit it: change the value of the `timeout` item in the `configuration` section, save the edited file |  |
| 5 | Click **RUN** button |  |
| 6 | Click **Advanced** | The text in the **Timeout (min)** field in the **Advanced** section equals the value specified at step 4 |
