# Run tool that have not nginx port

Test verifies that a tool endpoint configured with an invalid port returns a 502 Bad Gateway error.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open **Tools** page |  |
| 2 | Select **Default registry** |  |
| 3 | Select tool group |  |
| 4 | Select a tool with an endpoint |  |
| 5 | Click the **Settings** tab |  |
| 6 | Specify an invalid port in the **Tools endpoints** section |  |
| 7 | Click **EXECUTION ENVIRONMENT** |  |
| 8 | Specify valid values into the **Instance type** and **Disk (Gb)** fields |  |
| 9 | Click **Save** button |  |
| 10 | Click **Run** button |  |
| 11 | Click **Launch** button in the appeared pop-up window | The pipeline starts |
| 12 | In the **Runs** page click on the just-launched tool |  |
| 13 | Wait until the hyperlink in the header opposite the **Endpoint** label appears |  |
| 14 | Click the appeared hyperlink | A page with a `502 Bad Gateway` error appears |
