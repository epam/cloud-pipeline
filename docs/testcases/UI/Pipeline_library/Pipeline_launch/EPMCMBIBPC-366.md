# Test of non-required parameters display on launch form

Test verifies that a non-required `config.json` parameter with a value shows that value on the launch form with an editable name field.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open library |  |
| 2 | Create a pipeline (from the SHELL template) |  |
| 3 | Open the created pipeline, select version |  |
| 4 | On the pipeline page select **CODE** tab |  |
| 5 | Click on `config.json` file and edit it: add the following parameter into the `parameters` section, save the edited file:<br>`"param": {`<br>`    "value": "test_value",`<br>`    "required": "false"`<br>`}` |  |
| 6 | Click **RUN** button | <li> in the parameters section, the parameter from step 5 is displayed (the left field has the parameter name `param`, the right field has the value `test_value`) <li> the left field with the parameter name is enabled for editing |
