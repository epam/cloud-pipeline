# Change instance type in config.json

Test verifies that an `instance_size` value edited in `config.json` is reflected in the launch form's **Exec environment** section.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open library |  |
| 2 | Create a pipeline (from the SHELL template) |  |
| 3 | Open the created pipeline, select version |  |
| 4 | On the pipeline page select **CODE** tab |  |
| 5 | Click on `config.json` file and edit it: change the instance type (the `instance_size` item in the `configuration` section) to another valid value, save the edited file |  |
| 6 | Click **RUN** button |  |
| 7 | Click **Exec environment** | An instance equal to the one specified at step 5 is displayed in the **Exec environment** section |
