# Check of default command is saved only for edited tool

Test verifies that a default **Cmd template** saved for one tool is not applied to another tool that has no default settings.

**Prerequisites**:
- Perform the [EPMCMBIBPC-430](EPMCMBIBPC-430.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open **Tools** page |  |
| 2 | Select **Default** registry |  |
| 3 | Select a group |  |
| 4 | Click a tool without default settings |  |
| 5 | Click **SETTINGS** tab |  |
| 6 | Expand **EXECUTION ENVIRONMENT** section | The **Cmd template** field is empty |
