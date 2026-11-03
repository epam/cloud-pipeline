# Launch pipeline that have several configurations

Test verifies that selecting a non-default configuration on the RUN dropdown pre-fills its saved values.

**Prerequisites**:
- Perform the [EPMCMBIBPC-799](EPMCMBIBPC-799.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Click the **v** button near the **RUN** button |  |
| 2 | Click on the `new_conf` item in the appeared list |  |
| 3 | Click on **Exec environment** header to expand the section |  |
| 4 | Click on **Advanced** header to expand the section | <li> the **Disk (Gb)** field shows the value entered at step 4 of the [EPMCMBIBPC-799](EPMCMBIBPC-799.md) case (`15`) <li> the **Timeout (min)** field shows the value entered at step 4 of the [EPMCMBIBPC-799](EPMCMBIBPC-799.md) case (`1`) |
