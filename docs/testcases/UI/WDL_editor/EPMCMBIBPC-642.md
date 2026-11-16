# Check docker/compute node runtime configuration

Test verifies the pop-ups opened from the Runtime section's add docker configuration and add compute node configuration links.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-608](EPMCMBIBPC-608.md) case |  |
| 2 | Expand the **Runtime** collapsed header, click **add docker configuration** link |  |
| 3 | Click on the appeared field | The **Select docker image** pop-up appears, containing: an address bar in the form `{Registry} > {Tool_Group} > {Tool_name}`, a search field; **Cancel**, **OK** buttons |
| 4 | Click **Cancel** button |  |
| 5 | Click the delete icon of the docker configuration row |  |
| 6 | Click **add compute node configuration** link |  |
| 7 | Click on the combobox | A dropdown list of available node types appears |
| 8 | Click the delete icon of the compute node configuration row |  |
