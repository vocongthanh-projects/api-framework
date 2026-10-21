# Repository AI Instructions

This repository contains reusable API test automation infrastructure. Product endpoints, DTOs, credentials, test data, and business scenarios belong to consumer repositories.

## Before changing files

- Read this file, `README.md`, and the relevant module documentation.
- Read `.agents/AGENTS.md` and the applicable files in `.agents/rules/`. These contain the detailed API design, test, and configuration standards.
- Treat the Maven coordinates and module boundaries already present in the repository as authoritative; do not rename or split them based only on assumptions.

## OTS/FLI CI boundary

- A source push pipeline on GitLab 10 must stay lightweight: validate configuration and dependency metadata, then compile/package as configured. Do not add or trigger product/API test suites on that runner.
- API suites run through the separately configured test infrastructure, such as an explicit manual or scheduled trigger. Do not assume the GitLab source runner has the required network access, services, data, or runtime environment.
- Do not publish, deploy, push, or trigger remote pipelines unless the user explicitly asks for that action.
