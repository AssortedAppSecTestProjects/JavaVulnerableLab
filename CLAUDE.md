# Java Vulnerable Lab — Claude Code instructions

These instructions apply to every session in this project. Follow them without being asked.

## Apiiro security gates (required)

Do not skip these steps. They are mandatory for every implementation task.

1. **Before writing any code:** always run `/apiiro-secure-prompt` on the implementation task. Incorporate the returned security requirements into the work. Do not start coding until this has run.
2. **Before marking a task complete:** always run `/apiiro-diff-scan` on the changes. Do not declare the task done until the scan has finished and results have been reviewed.

Run these on every implementation task: new features, behavior-changing bug fixes, API/endpoint work, authentication/authorization, data persistence, secrets handling, and third-party integrations.

If a skill is not auto-invoked, read and follow the matching skill file under `~/.claude/skills/` (`apiiro-secure-prompt`, `apiiro-diff-scan`) and execute it before proceeding.
