# Launch Form

The UI for configuring and launching a pipeline or tool run. `LaunchPipeline.js` in this directory is
a thin wrapper around the actual form, `form/LaunchPipelineForm.js`.

**Put new logic in `form/utilities/`, `utilities/` (this directory's own, separate from `form/`'s) or
`form/components/`, and touch `LaunchPipelineForm.js` only to wire it in.** Check whether an existing
file already covers the concern before adding a new one — `form/utilities/parameter-utilities.js`,
`launch-cluster.js`, `run-capabilities.js` and `validator-utilities.js` are where most of this logic
already lives. A focused subcomponent (`form/components/reservation-parameters/`,
`form/components/custom-tags/`, `form/components/upload-parameters-button/`) is the pattern for
anything with its own UI, not a new render method on the form class.

This isn't a rule to eliminate touching the main file — some changes genuinely belong there. It's a
rule to keep that file's share of any given diff as small as the change allows, every time.
