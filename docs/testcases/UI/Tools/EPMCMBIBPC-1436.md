# Validation of estimated price availability of filled tool

Test verifies that the estimated price is shown on the launch page and its price-type breakdown tooltip for a tool with filled-in execution settings.

**Prerequisites**:
- User has **EXECUTE** permissions to at least one filled tool of a group

**Preparations**:
1. Open **Tools** page
2. Select **Default** registry
3. Select a group
4. Click a tool that is executable for the user
5. Click **SETTINGS** tab
6. Expand **EXECUTION ENVIRONMENT** section if it's minimized
7. Check whether **Instance type** and **Disk (Gb)** fields are filled in
8. Click the **v** button on the right of the **Run** button
9. Click **Custom settings** in the appeared dropdown list

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Expand **Advanced** section if it's minimized | The **Launch** page shows: <li> the sign `Launch <tool_name> <tool_version_name>. Estimated price per hour: <value> $` <li> a reference-info icon on the right of that sign |
| 2 | Change the price type |  |
| 3 | Hover over the info icon (i in a black circle) near the **Estimated price per hour** sign | Information table appears:<br>`Price per hour: <value> $`<br>`Minimum price: <value> $`<br>`Maximum price: <value> $`<br>`Average price: <value> $` |
| 4 | Click the **v** button on the right of the **Add parameter** button |  |
| 5 | Click **String parameter** in the appeared list | The following appear above the **Add parameter** button: <li> **Name**, **Value** fields <li> the delete button (minus icon in a circle) |
