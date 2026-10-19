# Validation of adding parameter in the scatter

Test verifies that naming a scatter's input enables Save/Revert and shows it on the diagram.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-626](EPMCMBIBPC-626.md) case |  |
| 2 | At the **Inputs** section, enter a value into the **Name** field (e.g. `test_in`) |  |
| 3 | Close the **Properties** panel | <li> **Save**, **Revert changes** buttons become enabled <li> at the diagram, a new scatter appears; the task has one input with the name specified at step 2 |
