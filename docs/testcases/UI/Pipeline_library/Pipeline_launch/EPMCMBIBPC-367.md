# Test of parameters that have type display on launch form

Test verifies that path/input/output/common typed `config.json` parameters display their entered folder paths on the launch form.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open library |  |
| 2 | Create a pipeline (from the SHELL template) |  |
| 3 | Open the created pipeline, select version |  |
| 4 | On the pipeline page select **CODE** tab |  |
| 5 | Click on `config.json` file and edit it: for each parameter type from the list `path`, `input`, `output`, `common`, add the following parameter block into the `parameters` section, save the edited file:<br>`"param" : {`<br>`    "type" : {parameter type from the list below},`<br>`    "value": {path to the existing folder},`<br>`    "required" : false`<br>`}` |  |
| 6 | Click **RUN** button (see examples in the attached files) | The parameters from step 5 are displayed in the **Parameters** section (the left field has the parameter name, the right field has the parameter value - the entered folder paths) |
