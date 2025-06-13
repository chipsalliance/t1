## Workflows Explanation

When each PR opened, `00-lint` and `00-test-main` workflows will be triggered.

- `00-lint`: run format check
- `00-test-main`: batch run test cases for designs specified in
`.github/designs` and `.github/verilator`.

`01-emulator-ci` workflow is a reusable workflow file that will specify the
test cases run logic, and will be called by `00-test-main` workflow.

`02-pd` workflow is a reusable workflow that will be called by `00-test-main`
workflow and execute RTL check and send a physical design check to our backend server.

## Naming Convention

For workflows that needs to be run each time developers open a PR,
prefix the workflow file with number starting from 0.

For workflows that will be manually run, prefix the workflow file with
`99`.
