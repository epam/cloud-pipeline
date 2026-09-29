# [MANUAL] Autocomplete validation

Test verifies that the search field's autocomplete dropdown lists matching field names and completes the selected one.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-727](EPMCMBIBPC-727.md) case |  |
| 2 | Enter `pipe` into the search field | A dropdown list appears containing the items: `pipeline.id`, `pipeline.version`, `pipeline.name` |
| 3 | Click on one of the items in the dropdown list (`pipeline.id`) | The selected command is fully displayed in the search field (`pipeline.id`) |
