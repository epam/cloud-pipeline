# Change instance price type to spot in config.json

Test verifies that setting `is_spot: true` in `config.json` selects **Spot** as the launch form's price type.

**Prerequisites**:
- The test could be run only if this functionality is supported by the specific platform

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open library |  |
| 2 | Open the pipeline from the [EPMCMBIBPC-368](EPMCMBIBPC-368.md) case, select version |  |
| 3 | On the pipeline page select **CODE** tab |  |
| 4 | Click on the `config.json` file and edit it: in the `configuration` section set `"is_spot": true`; save the edited file |  |
| 5 | Click **RUN** button |  |
| 6 | Click **Advanced** | **Spot** is selected for the price type in the **Advanced** section |
