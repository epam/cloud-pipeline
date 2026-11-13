# Check 'conda' run capability

Test verifies that user has possibility to create/remove conda environments via the `conda` run capability.

**Prerequisites**:
- Admin user
- The `conda` run capability is configured on the platform (the `launch.capabilities` preference)

| Steps | Actions | Expected results |
|:---:|---|---|
| 1 | Login as the admin user from the prerequisites | |
| 2 | Open the **Tools** page | |
| 3 | Select test tool | |
| 4 | At the tool page, hover over the **Run v** button | |
| 5 | Click the **Custom settings** button in the list | |
| 6 | Expand the **Exec environment** section | |
| 7 | Click into the field near the ***Run capabilities*** label. Select ***conda*** capability from list |  |
| 8 | Expand the **Advanced** section. Tick the **Start idle** checkbox, set **Price type** to `On-demand` | |
| 9 | Launch the run | |
| 10 | At the **Runs** page, click the just-launched run | |
| 11 | Wait until the **SSH** hyperlink appears | |
| 12 | Click the **SSH** hyperlink | |
| 13 | In the opened tab, enter and perform the command: `conda env list`. The first conda command of a run may take several minutes | The output contains list all the environments that includes at least of `base` environment |
| 14 | Enter and perform the commands: <br> `conda create -y -n test-env` <br> `conda env list` | The output contains list all the environments that includes `base` and `test-env` environments |
| 15 | Enter and perform the commands: <br> `conda env remove -y -n test-env` <br> `conda env list` | The output contains list all the environments that includes `base` and doesn't include `test-env` environment |

**After:**
- Stop the run launched at step 9
