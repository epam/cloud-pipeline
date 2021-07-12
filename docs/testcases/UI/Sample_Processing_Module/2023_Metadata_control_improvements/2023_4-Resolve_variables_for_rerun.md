# Resolve variables for rerun
Test verifies
- that the users can choose for a rerun resolving or not environment variable in run parameters.

**Prerequisites**:

- Admin user

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as admin user from the prerequisites | |
| 2 | Open the **Tools** page | |
| 3 | Select any tool (e.g. ubuntu) | |
| 4 | At the tool page, hover over the **Run v** button | |
| 5 | Select the ***Custom settings*** option in the list | |
| 6 | Click **Add Parameter** button in the ***Parameters*** section | *Name* and *Value* fields to create new parameter appear |
| 7 | Enter `test_parameter` into the *Name* field and `$RUN_ID` into the *Value* field | |
| 8 | Launch the run | |
| 9 | At the Runs page, click the just-launched run. Store its `run_id` | Log run page opens |
| 10 | Expand the ***Parameters*** section | Parameter `test_parameter` with value `run_id` from step 9 is shown in the ***Parameters*** section |
| 11 | Click the **Stop** hyperlink  | |
| 12 | Wait until the **Rerun** hyperlink appears. | |
| 13 | Click the **Rerun** hyperlink | *Launch* form opens <li> Unchecked *Use resolved values* checkbox is shown in the ***Parameters*** section <li> Value `$RUN_ID` is shown in the *Value* field for `test_parameter` |
| 14 | Check *Use resolved values* checkbox | Value `run_id` from step 9 is shown in the *Value* field for `test_parameter` |
| 15 | Repeat steps 8-10 | Parameter `test_parameter` with value `run_id` from step 9 is shown in the ***Parameters*** section |
| 16 | Repeat steps 11-13 | *Launch* form opens <li> *Use resolved values* checkbox isn't shown in the ***Parameters*** section <li> Value `run_id` from step 9 is shown in the *Value* field for `test_parameter` |
| 17 | Go to the **Runs** page | |
| 18 | Switch to the ***Completed Runs tab*** | |
| 19 | Click the **Rerun** hyperlink for run with `run_id` from step 9 | *Launch* form opens <li> Unchecked *Use resolved values* checkbox is shown in the ***Parameters*** section <li> Value `$RUN_ID` is shown in the *Value* field for `test_parameter` |
| 20 | Repeat steps 8-10 | Parameter `test_parameter` with value equal to the run_id of current run is shown in the ***Parameters*** section |
| 21 | Click the **Stop** hyperlink  | |