# Validate error messages on "Configure cluster" pop-up

Test verifies the validation messages shown for invalid values in the **Configure cluster** pop-up's child-node and auto-scaling fields.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open library |  |
| 2 | Create a pipeline (from the SHELL template) |  |
| 3 | Open the pipeline created at step 2 |  |
| 4 | Click **RUN** button opposite the pipeline version |  |
| 5 | Click **Exec environment** |  |
| 6 | Set valid **Node type** and **Disk (Gb)** values |  |
| 7 | Click **Configure cluster** |  |
| 8 | Click **Cluster** |  |
| 9 | Input `0` into the **Child nodes** field | The message `Value should be greater than 0` is displayed |
| 10 | Input `-1` into the **Child nodes** field | The message `Value should be greater than 0` is displayed |
| 11 | Input `asdf` into the **Child nodes** field | The message `Enter positive number` is displayed |
| 12 | Click **Auto-scaled cluster** |  |
| 13 | Input `0` into the **Auto-scaled up to** field | The message `Value should be greater than 0` is displayed |
| 14 | Input `-1` into the **Auto-scaled up to** field | The message `Value should be greater than 0` is displayed |
| 15 | Input `asdf` into the **Auto-scaled up to** field | The message `Enter positive number` is displayed |
| 16 | Click **Setup default child nodes count** |  |
| 17 | Input `3` into the **Default child nodes** field | The message `Max child nodes count should be greater than child nodes count` is displayed |
| 18 | Input `0` into the **Default child nodes** field | The message `Value should be greater than 0` is displayed |
| 19 | Input `-1` into the **Default child nodes** field | The message `Value should be greater than 0` is displayed |
| 20 | Input `asdf` into the **Default child nodes** field | The message `Enter positive number` is displayed |
