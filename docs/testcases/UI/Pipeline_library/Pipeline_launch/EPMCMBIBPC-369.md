# Change instance disk size in config.json

Test verifies that an `instance_disk` value edited in `config.json` is reflected in the launch form's **Exec environment** section.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open library |  |
| 2 | Open the pipeline from the [EPMCMBIBPC-368](EPMCMBIBPC-368.md) case, select version |  |
| 3 | On the pipeline page select **CODE** tab |  |
| 4 | Click on `config.json` file and edit it: change the disk size (the `instance_disk` item in the `configuration` section) to another valid value, save the edited file |  |
| 5 | Click **RUN** button |  |
| 6 | Click **Exec environment** | The disk size equal to the value specified at step 4 is displayed in the **Exec environment** section |
