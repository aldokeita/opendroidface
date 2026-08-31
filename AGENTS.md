# AGENTS.md

Guidance for Claude Code and other agents working in this repository.

## What this repo is

`aldokeita/opendroidface` — a fork of `yashab-cyber/opendroid` (Apache-2.0). Upstream
ships the autonomous Android agent; this fork adds a **robot face layer** on top of it.
`PLAN.md` is the roadmap for that layer and is the authority on what to work on next.
`SETUP.md` covers the local toolchain, the two remotes, and the release path.

## Agent skills

### Robot face conventions

Where face code may live, which upstream files may be touched, and how a change is
verified before it counts as done. See `.claude/skills/opendroid-face/SKILL.md` — read
it before editing, adding, or reviewing code here.

### Issue tracker

GitHub Issues on this fork, `aldokeita/opendroidface`. Never file or read issues on the
`yashab-cyber/opendroid` upstream. External PRs are not a triage surface.
See `docs/agents/issue-tracker.md`.

### Triage labels

Canonical defaults — `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`,
`wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: `CONTEXT.md` at the root, ADRs in `docs/adr/`. Neither exists yet —
they are created lazily, so proceed silently when they are absent.
See `docs/agents/domain.md`.
