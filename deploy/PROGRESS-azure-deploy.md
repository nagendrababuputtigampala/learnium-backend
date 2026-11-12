# feature/azure-deploy — progress note

Base: dev @ 62f4f27 (M6 merge). Decisions (Nagendra, 2026-11-12): two
environments — dev DB on Neon Free, prod DB on Azure PostgreSQL B1ms,
both stacks on the one free B2ats v2 VM; generation OFF; storage local
volume first (Step 2 revisits).

- [x] Slice 1: docker-compose.azure.yml (one env per invocation via -p +
      --env-file; backend unpublished, frontend-only port, per-project
      appfiles volume, log rotation) + deploy/prod.env.example +
      deploy/dev.env.example + .gitignore guard for filled env files.
      Validated: YAML parses; property names checked against
      application.yml. Docker is unavailable on the Edison VM, so
      `docker compose config` runs on the Azure VM at deploy time.
- [x] Slice 2: deploy/vm-setup.sh (Docker install, swap, repo clones).
- [x] Slice 3: deploy/backup.sh + deploy/reset-demo.sh (tracker 56 + 53).
- [x] Next: bundle for Nagendra to push; PR to dev via connector.
