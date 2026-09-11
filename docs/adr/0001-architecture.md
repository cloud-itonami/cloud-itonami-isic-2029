# ADR-0001: AdhesiveAdvisor ⊣ Adhesive Plant Operations Governor architecture

## Status

Accepted. `cloud-itonami-isic-2029` promoted from `:spec` to
`:implemented` in the `kotoba-lang/industry` registry, following the
verified fresh-scaffold protocol established by prior actors in this
fleet.

## Context

`cloud-itonami-isic-2029` publishes an OSS blueprint for ISIC 2029
(manufacture of other chemical products n.e.c.) **plant operations
coordination** (production-batch product-type/weight/output-quality
viscosity-and-purity data logging, reactor/mixing-line-equipment
maintenance scheduling, safety-concern flagging, and outbound shipment
coordination). Like every actor in this fleet, the blueprint alone is
not an implementation: this ADR records the governed-actor
architecture that promotes it to real, tested code, following the
same langgraph StateGraph + independent Governor + Phase 0->3 rollout
pattern established across the cloud-itonami fleet.

ISIC 2029 is unlike its 20xx siblings already implemented in this
fleet: it is a **residual "not elsewhere classified" chemicals
category**, spanning unrelated product families (adhesives/glues,
essential oils, matches, photographic chemicals) that do not share a
single plant shape or hazard profile. Rather than force the whole
heterogeneous n.e.c. bucket into one actor, this build follows the
task's own guidance and adopts ONE concrete illustrative product line:
**industrial/consumer adhesives and glues manufacturing** (reaction/
mixing via a batch reactor or mixing tank, plus filling/packaging
lines). The closest domain analog for the plant-operations *shape* is
`cloud-itonami-isic-2021` (Manufacture of pesticides and other
agrochemical products): both are back-office coordination actors for a
fixed processing PLANT with heavy manufacturing equipment (a reactor/
mixing-tank analog to 2021's blending-tank/high-shear-mixer), a real
physical safety dimension, and the same four-op shape
(`:log-production-batch`/`:schedule-maintenance`/`:flag-safety-
concern`/`:coordinate-shipment`) and two-entity verified/registered
gate structure (equipment for maintenance scheduling, batch for
shipment coordination). The two verticals are, however, distinct: 2021
sits inside an EXPLICIT pesticide-registration/label-approval
regulatory structure it must never decide, while 2029's adhesive
illustration sits inside a chemical-safety-certification structure
(e.g. GHS/CLP hazard classification, SDS certification, third-party
product-safety marks) this actor must likewise never decide or grant.
This build mirrors 2021's architecture closely but adapts the hazard
profile, equipment/product vocabulary, and the domain-specific
PERMANENT governor block from "pesticide-registration/label-approval
decision" to "chemical-safety-certification decision"
(`certification-decision-blocked-violations`, the domain-specific twin
of `line-actuate-blocked-violations`), alongside two domain-specific
quality-data validation checks (viscosity plausibility, purity
plausibility) plus one domain-specific quality-spec check (purity
below spec floor) in place of 2021's single active-ingredient
plausibility+label-ceiling pair.

This vertical has NO pre-existing `kotoba-lang/adhesivemfg`-style
capability library to wrap (verified: no such repo exists). This build
therefore uses self-contained domain logic -- pure functions in
`adhesivemfg.registry` (equipment/batch verification, shipment-weight
recompute, product-type validation, viscosity plausibility validation,
purity plausibility validation, purity-below-spec-floor validation)
are re-verified independently by the governor, the same "ground truth,
not self-report" discipline established across prior actors (most
directly `cloud-itonami-isic-2021`'s `pesticidemfg.registry`).

This blueprint's own `:itonami.blueprint/governor` keyword,
`:adhesive-plant-operations-governor`, is grep-verified UNIQUE
fleet-wide (`gh search code "adhesive-plant-operations-governor"
--owner cloud-itonami`, zero hits before this repo was created); so is
the `adhesivemfg` namespace prefix (`gh search code "adhesivemfg"
--owner cloud-itonami`, zero hits).

## Decision

### Decision 1: Self-contained domain logic (no external adhesive/chemical-manufacturing capability library to wrap)

Unlike actors that delegate to pre-existing domain libraries, this
adhesive/chemical vertical has NO pre-existing capability library to
wrap. The equipment/batch-verification / shipment-weight /
product-type / viscosity / purity validation functions live as pure
functions in `adhesivemfg.registry` and are re-verified independently
by `adhesivemfg.governor` -- the same "ground truth, not self-report"
discipline established across prior actors (most directly
`cloud-itonami-isic-2021`'s `pesticidemfg.registry`).

### Decision 2: One concrete illustrative product line, not the whole n.e.c. bucket

ISIC 2029's own scope note (residual "not elsewhere classified"
chemical products) does not describe one plant shape the way most
other 20xx classes do. Rather than build a generic, unfalsifiable
"any n.e.c. chemical product" actor, this build picks industrial/
consumer adhesives and glues manufacturing as ONE concrete
illustration, documented plainly in the README, and scopes every
domain fact (product types, viscosity range, purity spec floors) to
that illustration. A future actor covering a different n.e.c. product
family (e.g. essential oils, matches, photographic chemicals) would be
a separate build, not an extension of this one -- the same "one
concrete plant shape, explicitly scoped" discipline
`pesticidemfg.registry`'s own ISIC 2021 scope note establishes for its
own (narrower, single-family) scope decision.

### Decision 3: Coordination, not control, and NOT a certification authority — scope boundary at the back-office

This actor is **strictly back-office coordination** of adhesive/glue
plant operations. It does NOT:
- Control reactor/mixing-line equipment directly
- Make plant-safety or product-safety decisions (exclusive to the human plant supervisor)
- Actuate the reactor/mixing or filling/packaging line
- Decide, grant, or revoke a chemical-safety certification (exclusive to a certification authority, e.g. a GHS/CLP hazard-classification body, an SDS certifier, a third-party product-safety certification mark)

All proposals are `:effect :propose` only. The advisor proposes; the
governor validates; escalation paths funnel to human plant-supervisor
approval. This is not a replacement for the supervisor's authority or
a certification authority's process — it is a proposal-screening and
documentation layer.

**CRITICAL SAFETY BOUNDARY**: adhesive/chemical manufacturing is a
safety-critical domain (chemical-handling/reaction hazard: toxicity,
exposure risk, flammability, reactive hazard). Safety-concern flagging
NEVER auto-commits. All safety concerns escalate immediately to human
review, and no proposal may ever attempt a certification decision,
regardless of confidence or phase.

### Decision 4: Safety-concern escalation — always human sign-off

`:flag-safety-concern` (chemical-handling/reaction-hazard toxicity/
exposure-risk/flammability/reactive-hazard concern, equipment-safety
concern, crew exposure) ALWAYS escalates, never auto-commits. This is
not a "low-stakes proposal" — it is a circuit-breaker that must reach
human authority.

### Decision 5: Two independent verified/registered gates (equipment AND batch), not one

Like `cloud-itonami-isic-2021`, this vertical has TWO entity kinds
each gating a different op: `:schedule-maintenance` independently
verifies the referenced **equipment** unit's own `:verified?`/
`:registered?` fields; `:coordinate-shipment` independently verifies
the referenced **batch**'s own `:verified?`/`:registered?` fields.
Both are the same "plant/batch record must be independently
verified/registered before any action" HARD invariant applied to the
two distinct record kinds this domain actually has.
`:coordinate-shipment` additionally independently recomputes whether a
batch's own recorded shipped-to-date weight plus the proposal's own
claimed weight would exceed the batch's own recorded production
weight — never taken on the advisor's self-report.

### Decision 6: A second permanent block, distinct from line-actuate — the chemical-safety-certification-decision boundary

Adapting `cloud-itonami-isic-2021`'s registration/label-approval
boundary to this vertical's own regulatory shape: a compromised or
mis-wired caller could attempt to have this actor's
`:log-production-batch` proposal carry a `:decide-certification?
true` flag, asking the actor to "decide" that a batch's product is
chemical-safety certified. `certification-decision-blocked-
violations` in `adhesivemfg.governor` HARD-blocks this unconditionally
and permanently — structurally identical in shape and severity to
`line-actuate-blocked-violations`, but guarding the certification-
authority boundary instead of the physical-equipment boundary. Two
permanent, unconditional, non-overridable blocks instead of one is
this vertical's central architectural adaptation, mirroring 2021's own
Decision 5.

### Decision 7: Viscosity and purity — plausibility, plus a purity spec floor

Mirroring `cloud-itonami-isic-2021`'s active-ingredient plausibility +
label-ceiling pair, this vertical adds THREE new governor checks over
two new domain facts (`:viscosity-cp`, `:purity-pct`), matching the
task's own op description ("output-quality (viscosity/purity test)
data logging"):
- `:log-production-batch` INDEPENDENTLY re-validates a patch's own
  declared `:viscosity-cp` against a physically plausible range (0 to
  1,000,000 cP, `adhesivemfg.registry/viscosity-valid?`) — a
  fabricated or sensor-error reading is rejected rather than let
  through.
- `:log-production-batch` INDEPENDENTLY re-validates a patch's own
  declared `:purity-pct` against a physically plausible range (0-100%,
  `adhesivemfg.registry/purity-valid?`).
- `:log-production-batch` INDEPENDENTLY re-derives the effective
  product type (patch's own `:product-type`, else the batch's
  already-recorded type) and, when the patch declares a `:purity-pct`,
  checks it against that product type's own closed regulatory spec
  FLOOR (`adhesivemfg.registry/purity-below-spec-floor?`, modeled on
  representative typical industrial adhesive product-spec minimum
  active-solids-content ranges) — a FLOOR rather than 2021's CEILING,
  since an adhesive batch's efficacy requires AT LEAST its product
  type's own minimum purity/solids content, not a maximum. This
  mirrors the "ground truth, not self-report" discipline every other
  governor check in this fleet establishes, applied to genuinely new
  domain-specific quality facts this vertical's own product mix
  introduces.

### Decision 8: HARD invariants (no override)

Four HARD governor invariants (elaborated into thirteen concrete
checks in `adhesivemfg.governor`, mirroring
`cloud-itonami-isic-2021`'s own elaboration of its HARD invariants
into concrete checks, plus the one new domain-specific permanent block
per Decision 6 and the three new domain-specific checks per
Decision 7) block proposals and cannot be overridden by human
approval:
1. Plant/batch record (equipment for maintenance, batch for shipment) must be independently verified/registered before any action is taken against it, and a shipment's weight must independently recompute within the batch's own logged production weight
2. Proposals must be `:effect :propose` only (never direct equipment control)
3. Direct reactor/mixing-line-equipment control, line actuation, or a chemical-safety-certification-authority decision is permanently blocked
4. The op allowlist is closed — `:log-production-batch`/`:schedule-maintenance`/`:flag-safety-concern`/`:coordinate-shipment` only

## Consequences

(+) Other-chemical-products (adhesive/glue) plant operations
back-office now has a documented, governed, auditable coordination
layer that funnels all decisions through independent validation before
human approval.

(+) The "coordination, not control, not certification authority"
boundary is explicit in code: all `:effect :propose`, all real-world
actuation requires human plant-supervisor sign-off, and no path exists
for this actor to decide a chemical-safety certification.

(+) Scope is bounded and verifiable: four HARD invariants (elaborated
into thirteen concrete governor checks) protect against scope creep
into unauthorized equipment operation, line actuation, certification-
decision-making, or non-compliant purity/viscosity data. Safety
concerns are a circuit-breaker, not a threshold.

(+) The "one concrete illustrative product line" scope decision
(Decision 2) keeps this build falsifiable and testable instead of
gesturing at an unbounded residual category.

(-) Still a simulation/proposal layer, not a real plant-operations
control system. Equipment actuation and reactor/mixing/filling/
packaging-line operation remain human-controlled via external
channels, and chemical-safety certification remains a
certification-authority process entirely outside this actor.

(-) No integration with real plant-management databases (equipment
telemetry, batch tracking, freight dispatch, or an authoritative
chemical-safety-certification database) — this is a standalone
coordinator blueprint; the closed `purity-spec-floor-pct` table is a
representative, illustrative subset of typical industrial adhesive
product specs, not an exhaustive multi-vendor/multi-standard
specification database. This build also does not cover ISIC 2029's
other product families (essential oils, matches, photographic
chemicals, etc.) — see Decision 2.

## Verification

- `cloud-itonami-isic-2029`: `kbb -M:test` green (all tests pass;
  see the superproject ADR and `kotoba-lang/industry` registry entry
  for the exact `Ran N tests containing M assertions, 0 failures, 0
  errors` output, verified from an independent fresh clone), `clojure
  -M:lint` clean, `kbb -M:dev:run` demo narrative exercises
  proposal submission, escalation, and every HARD-hold scenario
  directly (not-propose-effect, unknown-op, equipment-not-verified,
  batch-not-verified, shipment-weight-exceeded, line-actuate-blocked,
  certification-decision-blocked, already-scheduled,
  invalid-product-type, invalid-viscosity, invalid-purity,
  purity-below-spec-floor).
- All source is `.cljc` (portable ClojureScript / JVM / nbb) — no
  JVM-only interop; the actor graph is invoked exclusively via
  `langgraph.graph/run*` (not `.invoke`, which is not cljs-portable).
- Audit ledger is append-only, all decisions are traced; every settled
  request (commit or hold) leaves exactly one ledger fact.
- `deps.edn` pins `io.github.kotoba-lang/langgraph` and
  `io.github.kotoba-lang/langchain` via `:local/root` directly in the
  top-level `:deps` (not only under a `:dev` alias), so a bare
  `kbb -M:test` resolves offline inside the monorepo checkout.
