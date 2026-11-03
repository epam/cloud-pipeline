# Validate of run parameters for tool in sandbox

Test verifies the tool settings page's Port field and the generated Cmd template.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open **Tools** page |  |
| 2 | Select **Default registry** |  |
| 3 | Select tool group |  |
| 4 | Select a tool with an endpoint |  |
| 5 | Click the **Settings** tab | <li> the tool editing page appears <li> the **Port** field shows a port value (an integer) |
| 6 | Click **EXECUTION ENVIRONMENT** | The **Cmd template** field shows the tool's starting command in the container |
| 7 | If there are no endpoints, click **Add endpoint** button, specify `8081` in the **Port** field and click **Save** button |  |
