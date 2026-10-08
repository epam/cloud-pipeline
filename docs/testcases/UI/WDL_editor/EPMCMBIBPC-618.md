# Validation of adding parameter in the task

Test verifies that filling a task's Alias and an input parameter enables Save/Revert and shows the new task on the diagram.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-610](EPMCMBIBPC-610.md) case |  |
| 2 | Enter a value into the **Alias** field (e.g. `test`) |  |
| 3 | At the **Inputs** section, enter a value into the **Name** field (e.g. `test_in`) |  |
| 4 | At the **Inputs** section, select a **Type** value (e.g. `Int`) |  |
| 5 | At the **Inputs** section, enter a value into the **Value** field (e.g. `0`) |  |
| 6 | Close the **Properties** panel | <li> **Save**, **Revert changes** buttons become enabled <li> at the diagram, a new task appears: its name equals the value specified at step 2; it has one input with the name specified at step 3 |
