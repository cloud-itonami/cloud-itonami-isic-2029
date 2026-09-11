# cloud-itonami-isic-2029: Manufacture of other chemical products n.e.c.

Open Business Blueprint for **ISIC Rev.5 2029**: manufacture of other chemical products not elsewhere classified — an autonomous "actor" (LLM advisor behind an independent Governor, langgraph-clj StateGraph, append-only audit ledger) that coordinates back-office **plant operations**: production-batch data logging (product-type/weight/output-quality viscosity and purity test data), reactor/mixing-line-equipment maintenance scheduling, safety-concern flagging, and outbound shipment coordination.

This repository designs a forkable OSS business for other-chemical-products plant operations: run by a qualified operator so a plant keeps its own operating records instead of renting a closed SaaS.

## Scope: one concrete illustrative product line within a residual n.e.c. category

ISIC 2029 is a **residual chemicals category**: it covers whatever chemical-product manufacturing does not fit any more specific ISIC 20xx class — for example adhesives/glues, essential oils, matches, and photographic chemicals. These product families do not share a single plant shape or hazard profile the way ISIC 2021's pesticide/agrochemical family or ISIC 2022's paint/coatings family do, so this build does not attempt to model the entire heterogeneous n.e.c. bucket in one actor.

**Chosen concrete illustration: industrial/consumer adhesives and glues manufacturing.** This build models one representative plant shape from within ISIC 2029's scope — reaction/mixing (via a batch reactor or mixing tank) plus filling/packaging lines producing hot-melt, solvent-based, water-based, pressure-sensitive, reactive-polyurethane, epoxy, cyanoacrylate, and rubber-based adhesive/glue products. This mirrors `cloud-itonami-isic-2021`'s (pesticides and other agrochemical products) and `cloud-itonami-isic-2022`'s (paints, varnishes and similar coatings) own "one plant shape, back-office coordination" design, adapted to the residual-category framing this specific ISIC class requires.

## What this actor does

Proposes **plant operations coordination**, not equipment operation or certification decision-making:
- `:log-production-batch` — reaction/mixing batch, output-quality (viscosity/purity test) data logging (administrative, not an operational decision)
- `:schedule-maintenance` — reactor/mixing-line-equipment maintenance scheduling proposal
- `:flag-safety-concern` — surface a chemical-handling/reaction-hazard concern (toxicity, exposure risk, flammability, reactive hazard) (always escalates)
- `:coordinate-shipment` — outbound product shipment coordination proposal

## What this actor does NOT do

**CRITICAL SCOPE BOUNDARY — this is a safety-critical, regulated domain**
(reactor/mixing-line equipment, chemical-handling/reaction hazard, chemical-safety certification authority):

- Does NOT control reactor/mixing-line equipment directly
- Does NOT make plant-safety or product-safety decisions (that's the plant supervisor's exclusive human authority)
- Does NOT actuate the reactor/mixing or filling/packaging line (human plant supervisor decides)
- Does NOT decide, grant, or revoke a chemical-safety certification — that is EXCLUSIVELY a certification authority's (e.g. a GHS/CLP hazard-classification body, an SDS certifier, a third-party product-safety certification mark) call, never this actor's
- ONLY proposes/coordinates operations back-office; all actuation and all certification decisions require the appropriate human or certification authority
- Safety-concern flagging ALWAYS escalates — never auto-decided, no confidence threshold or phase below escalation

## Architecture

Classic governed-actor pattern (`adhesivemfg.operation/build`, a langgraph-clj StateGraph):
1. **`adhesivemfg.advisor`** (sealed intelligence node, `AdhesiveAdvisor`): proposes decisions only, never commits
2. **`adhesivemfg.governor`** (independent, `Adhesive Plant Operations Governor`): validates against domain rules, re-derived from `adhesivemfg.registry`'s pure functions and `adhesivemfg.store`'s SSoT -- never trusts the advisor's own self-report
   - HARD invariants (always `:hold`, no override):
     - Plant/batch record must be independently verified/registered (`:verified?` AND `:registered?`) before any action is taken against it (equipment before maintenance scheduling, batch before shipment coordination)
     - The request's own `:effect` must be `:propose` (never a direct-write bypass)
     - `:op` must be in the closed four-op allowlist
     - The proposal's own `:effect` must be one of the four propose-shaped effects (no direct reactor/mixing-line-equipment control)
     - Directly actuating the reactor/mixing or filling/packaging line (`:actuate-line? true`) is a PERMANENT, unconditional block
     - Deciding or granting a chemical-safety certification (`:decide-certification? true`) is a PERMANENT, unconditional block — exclusively a certification authority's call
     - A shipment may not push a batch's own recorded shipped weight past its own logged production weight (independently recomputed)
     - No double-scheduling the same maintenance record
     - No fabricated `:product-type` value on a production-batch patch
     - No physically implausible `:viscosity-cp` or `:purity-pct` value on a production-batch patch
     - A batch's own declared `:purity-pct` must independently stay at or above its product type's own regulatory spec floor — never taken on the advisor's self-report that the formulation "meets spec"
   - ESCALATE (always human sign-off, overridable by a human):
     - `:flag-safety-concern` always escalates, regardless of confidence
     - Low-confidence proposals
3. **`adhesivemfg.phase`** (Phase 0->3 rollout): `:schedule-maintenance`/`:flag-safety-concern`/`:coordinate-shipment` are NEVER in any phase's `:auto` set (permanent, matching the governor's own posture); only `:log-production-batch` may auto-commit at phase 3 when clean
4. **`adhesivemfg.store`** (append-only audit ledger + SSoT): a single `MemStore` backend behind a `Store` protocol (see ns docstring for why a second Datomic-backed backend is out of scope for this build)

## Development

```bash
# Run tests (top-level deps.edn already pins langgraph+langchain local/root)
kbb -M:test

# Run tests via the workspace :dev override alias (equivalent, kept for sibling-repo parity)
kbb -M:dev:test

# Run the demo
kbb -M:dev:run

# Lint
kbb -M:lint
```

## Status

`:implemented` — `governor.cljc`/`store.cljc`/`advisor.cljc`/`registry.cljc` + `deps.edn` complete the module set; tests green, demo runnable, langgraph-clj integration verified.

## License

AGPL-3.0-or-later
