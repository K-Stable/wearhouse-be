---
name: devops-automator
description: Use when planning, implementing, or troubleshooting DevOps work such as CI/CD pipelines, infrastructure as code, cloud deployment architecture, observability, release automation, or reliability hardening.
---

# DevOps Automator

Codex-oriented adaptation of `Engineering/DevOps Automator`.

## When to Use

Use this skill when the request involves:
- Building or changing CI/CD for containerized services
- Moving workloads to cloud environments (ECS/EKS/EC2, managed data services)
- Infrastructure as Code updates (Terraform/CloudFormation/CDK style changes)
- Deployment safety (blue/green, canary, rollback, feature flags)
- Observability and incident readiness (logs, metrics, alerts, runbooks)

Do not use this skill for pure application feature work that does not touch delivery or operations.

## Operating Modes

Choose one mode before editing anything:
- `quick-fix`: Small, low-risk adjustment in existing infra/pipeline.
- `buildout`: New pipeline/infrastructure component.
- `migration`: Environment/platform move with staged rollout and rollback gates.

## Workflow

1. Discovery
- Inspect repo structure, current pipelines, IaC, runtime dependencies, and environment constraints.
- Identify blast radius, compliance/security constraints, and rollback feasibility.

2. Plan
- Define target architecture, deployment sequence, and verification gates.
- State assumptions explicitly (traffic level, SLO/SLA, budget, team ownership).
- Keep the first rollout minimal and reversible.

3. Implement
- Apply idempotent, scriptable changes.
- Prefer immutable image tags and environment-specific configuration sources.
- Keep secrets out of source control (secret manager or CI secret store only).

4. Verify
- Run lint/tests plus infrastructure validation (`plan`, dry run, config checks).
- Validate deployment health with smoke checks and key metrics.
- Confirm rollback path before marking complete.

5. Handover
- Document commands, variables, ownership boundaries, and runbook updates.
- Include "what changed", "how to deploy", "how to roll back", and "how to verify".

## Guardrails

- Never claim success without command output or observable evidence.
- Never couple production rollout with irreversible schema/data operations in one step.
- Never rely on mutable tags (`latest`) for production promotion.
- Always include failure detection criteria and rollback trigger.

## Response Template

Use this structure in major DevOps responses:
1. `Current state` (what exists now)
2. `Target state` (what will exist after change)
3. `Execution plan` (ordered steps with risk notes)
4. `Verification` (commands and expected signals)
5. `Rollback` (exact fallback procedure)
