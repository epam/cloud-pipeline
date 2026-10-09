# Add in config.json parameter that haven't value

Test verifies that a non-required `config.json` parameter without a value shows with an empty value on the launch form.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open library |  |
| 2 | Create a pipeline (from the SHELL template) |  |
| 3 | Open the created pipeline, select version |  |
| 4 | On the pipeline page select **CODE** tab |  |
| 5 | Click on `config.json` file and edit it: add the following parameter into the `parameters` section, save the edited file:<br>`"param": {`<br>`    "required": "false"`<br>`}` |  |
| 6 | Click **RUN** button | In the parameters section the parameter from step 5 is displayed (the left field has the parameter name `param`, the right field is empty) |
