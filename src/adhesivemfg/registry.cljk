(ns adhesivemfg.registry
  "Pure-function domain logic for the ISIC 2029 (manufacture of other
  chemical products n.e.c.) plant-operations coordination actor,
  concretely illustrated by industrial/consumer adhesives and glues
  manufacturing -- equipment/batch verification, shipment-weight
  recompute, product-type validation, viscosity plausibility
  validation, purity plausibility validation, purity-below-spec-floor
  validation, and draft maintenance-schedule/shipment-coordination
  record construction.

  Per docs/adr/0001-architecture.md Decision 1: this vertical has NO
  pre-existing `kotoba-lang/adhesivemfg`-style capability library to
  wrap (verified: no such repo exists). The domain logic therefore
  lives here as pure functions, re-verified INDEPENDENTLY by
  `adhesivemfg.governor` -- the same 'ground truth, not self-report'
  discipline every sibling actor's own registry establishes (e.g.
  `pesticidemfg.registry/shipment-weight-exceeded?` from
  `cloud-itonami-isic-2021`, this actor's closest chemical-process-
  plant analog): never trust a proposal's own self-reported weight/
  status/viscosity/purity when the inputs needed to recompute it
  independently are already on record.

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real plant-operations system, and NO chemical-safety-
  certification authority whatsoever. It builds the DRAFT record a
  plant coordinator would keep (a scheduled maintenance window, a
  coordinated shipment), not the act of actuating a reactor/mixing
  line, dispatching a real freight carrier, or granting/deciding a
  chemical-safety certification (this actor NEVER does any of these --
  see README `What this actor does NOT do`).

  SCOPE note: ISIC 2029 (manufacture of other chemical products n.e.c.)
  is a residual chemicals category covering product families such as
  adhesives/glues, essential oils, matches, and photographic
  chemicals. Rather than model every unrelated family, this build
  picks ONE concrete illustrative product line -- industrial/consumer
  adhesives and glues manufacturing (reaction/mixing via a batch
  reactor or mixing tank, plus filling/packaging lines producing
  hot-melt, solvent-based, water-based, pressure-sensitive, reactive-
  polyurethane, epoxy, cyanoacrylate, and rubber-based adhesive
  products) -- the same 'one concrete plant shape, explicitly scoped'
  discipline `pesticidemfg.registry`'s own ISIC 2021 scope note
  establishes. Every batch's own purity/solids-content is subject to a
  representative minimum spec floor (modeled on typical industrial
  adhesive product-spec minimum active-solids-content ranges per
  product category) that varies by product category --
  `purity-below-spec-floor?` independently re-verifies a batch's own
  declared purity against that closed floor table, never taken on the
  advisor's self-report, the same discipline
  `pesticidemfg.registry/active-ingredient-exceeds-label-limit?`
  applies to its own regulatory disclosure obligation (there, a
  ceiling; here, a floor, since an adhesive batch's efficacy requires
  AT LEAST its product type's own minimum purity/solids content, not a
  maximum).")

;; ----------------------------- constants -----------------------------

(def valid-product-types
  "The closed set of product-type values a production-batch record may
  declare -- the adhesives/glues illustrative product line this build
  picks for ISIC 2029's own residual n.e.c. scope. Anything else is a
  fabricated/unrecognized product type -- the governor HARD-holds
  rather than let an invented product type pass through."
  #{:hot-melt-adhesive :solvent-based-adhesive :water-based-adhesive
    :pressure-sensitive-adhesive :reactive-polyurethane-adhesive
    :epoxy-adhesive :cyanoacrylate-adhesive :rubber-based-adhesive})

(def purity-spec-floor-pct
  "The closed purity/active-solids-content minimum SPEC FLOOR table
  (percent purity/active-solids by weight of the ready-to-ship
  formulated product), one entry per `valid-product-types` member --
  modeled on REPRESENTATIVE typical industrial adhesive product-spec
  minimum active-solids-content ranges per formulation category.
  Illustrative, representative values (not an exhaustive multi-
  vendor/multi-standard specification database -- see README `What
  this actor does NOT do`), the same 'representative subset, not
  exhaustive' scope `pesticidemfg.registry/
  active-ingredient-label-limit-pct` establishes for its own
  regulatory set."
  {:hot-melt-adhesive              95.0
   :solvent-based-adhesive         20.0
   :water-based-adhesive           40.0
   :pressure-sensitive-adhesive    30.0
   :reactive-polyurethane-adhesive 90.0
   :epoxy-adhesive                 95.0
   :cyanoacrylate-adhesive         95.0
   :rubber-based-adhesive          25.0})

(def viscosity-min-cp
  "Physical floor for a batch's own viscosity reading (centipoise, cP)
  -- a real adhesive/glue is never a zero-viscosity fluid."
  0.0)

(def viscosity-max-cp
  "Physical ceiling for a batch's own viscosity reading (centipoise,
  cP) -- generous enough to cover thin water-based adhesives through
  heavily filled hot-melts, but a reading beyond this is implausible
  sensor/QC data, not a real batch."
  1000000.0)

(def purity-min-pct
  "Physical floor for a batch's own purity reading (percent by weight)
  -- a real formulated adhesive batch is never a zero-purity product
  (that would be an inert carrier, not an adhesive batch)."
  0.0)

(def purity-max-pct
  "Physical ceiling for a batch's own purity reading (percent by
  weight) -- 100% is a technical (unformulated) resin/polymer
  concentrate, the physical maximum possible. A reading beyond this is
  implausible sensor/QC data, not a real batch."
  100.0)

;; ----------------------------- equipment checks -----------------------------

(defn equipment-verified?
  "Ground-truth check: has `equipment`'s own record been marked
  verified (i.e. it has actually been inspected/commissioned and
  registered in the SSoT, not merely referenced from an unverified
  maintenance request)? A pure predicate over the equipment's own
  permanent field -- no proposal inspection needed."
  [equipment]
  (true? (:verified? equipment)))

(defn equipment-registered?
  "Ground-truth check: does `equipment`'s own record carry a
  `:registered?` true flag (i.e. it is on file in the plant's
  equipment registry)? Scheduling maintenance against equipment that
  is not on file and registered is the exact scope violation this
  actor's HARD invariant ('plant/batch record must be independently
  verified/registered before any action') exists to block."
  [equipment]
  (true? (:registered? equipment)))

(defn equipment-ready?
  "Combined ground-truth gate: the equipment must be both `verified?`
  AND `registered?` before ANY maintenance may be scheduled against
  it. Two independent facts on the equipment's own permanent record,
  neither inferred from the advisor's own rationale."
  [equipment]
  (and (equipment-verified? equipment) (equipment-registered? equipment)))

;; ----------------------------- batch checks -----------------------------

(defn batch-verified?
  "Ground-truth check: has `batch`'s own record been marked verified
  (i.e. its product-type/weight/viscosity/purity claims have actually
  been QC-inspected, not merely logged from an unverified intake
  patch)?"
  [batch]
  (true? (:verified? batch)))

(defn batch-registered?
  "Ground-truth check: is `batch`'s own record on file in the plant's
  production ledger? Coordinating a shipment against a batch that is
  not on file and registered is the exact scope violation this
  actor's HARD invariant ('plant/batch record must be independently
  verified/registered before any action') exists to block."
  [batch]
  (true? (:registered? batch)))

(defn batch-ready?
  "Combined ground-truth gate: the batch must be both `verified?` AND
  `registered?` before ANY shipment may be coordinated against it."
  [batch]
  (and (batch-verified? batch) (batch-registered? batch)))

(defn shipment-weight-exceeded?
  "Ground-truth check for a `:coordinate-shipment` proposal:
  would `shipped-to-date-kg` + `new-weight-kg` exceed `batch`'s own
  recorded `:weight-kg` (the batch's own logged production weight)?
  Needs no proposal inspection or stored-verdict lookup -- its inputs
  are permanent fields already on the batch's own record, the same
  shape every sibling actor's own cost/total-matching check uses."
  [batch new-weight-kg]
  (let [capacity (:weight-kg batch)
        so-far (:shipped-weight-kg batch 0.0)]
    (and (number? capacity)
         (number? new-weight-kg)
         (number? so-far)
         ;; Compared at 1/10000 of a unit, not on raw doubles. A shipment
         ;; that fills a batch EXACTLY to its recorded capacity is legal,
         ;; and comparing the raw sum flagged such shipments as over
         ;; because the sum is not the double nearest the true total.
         (> (Math/round (* 10000 (+ (double so-far) (double new-weight-kg))))
            (Math/round (* 10000 (double capacity))))))) 

(defn shipment-weight-exceeded-checkable?
  "Can `batch`'s headroom actually be computed for `new-weight-kg`?

  `shipment-weight-exceeded?` answers only `over` / `not over`, and its
  `(and (number? ...) ...)` guard made every un-checkable case fall
  through as `not over` -- a batch with no recorded capacity, or a
  shipment stating no amount, passed the over-capacity check silently.
  Callers must ask this first: un-checkable is not headroom."
  [batch new-weight-kg]
  (boolean (and (map? batch)
                (number? (:weight-kg batch))
                (number? (:shipped-weight-kg batch 0.0))
                (number? new-weight-kg))))

(defn product-type-valid?
  "Is `product-type` one of the closed, known product-type values
  (hot-melt, solvent-based, water-based, pressure-sensitive, reactive-
  polyurethane, epoxy, cyanoacrylate, or rubber-based adhesive)?
  nil/blank is treated as invalid (a production-batch patch must
  declare a real product type, not omit it silently)."
  [product-type]
  (contains? valid-product-types product-type))

(defn viscosity-valid?
  "Is `viscosity-cp` a physically plausible viscosity reading
  (centipoise, cP)? Rejects nil, non-numbers, negative values, and
  values beyond `viscosity-max-cp` -- a fabricated or sensor-error
  reading, never let through as a real batch fact."
  [viscosity-cp]
  (and (number? viscosity-cp)
       (>= (double viscosity-cp) viscosity-min-cp)
       (<= (double viscosity-cp) viscosity-max-cp)))

(defn purity-valid?
  "Is `purity-pct` a physically plausible purity reading (percent by
  weight)? Rejects nil, non-numbers, negative values, and values
  beyond `purity-max-pct` -- a fabricated or sensor-error reading,
  never let through as a real batch fact."
  [purity-pct]
  (and (number? purity-pct)
       (>= (double purity-pct) purity-min-pct)
       (<= (double purity-pct) purity-max-pct)))

;; ----------------------------- purity spec-floor checks -----------------------------

(defn purity-spec-floor-for [product-type]
  (get purity-spec-floor-pct product-type))

(defn purity-below-spec-floor?
  "Ground-truth check for a `:log-production-batch` proposal:
  INDEPENDENTLY re-derive the EFFECTIVE product type (patch's own
  `:product-type`, else the batch's already-recorded type) and check
  whether the patch's own declared `:purity-pct` falls BELOW that
  product type's own closed minimum spec floor
  (`purity-spec-floor-pct`) -- never taken on the advisor's
  self-report that the formulation 'meets spec'. Modeled on
  representative typical industrial adhesive product-spec minimum
  active-solids-content ranges. `effective-product-type` with no known
  floor (not in `valid-product-types`) is NOT independently flagged
  here -- `invalid-product-type-violations` in `adhesivemfg.governor`
  already rejects a fabricated product type on its own. NOTE: this
  re-derives whether a batch's own purity stays within its own product
  spec -- it does not grant, revoke, or decide any chemical-safety
  certification status (see ns docstring, and
  `adhesivemfg.governor`'s `certification-decision-blocked-
  violations`)."
  [effective-product-type purity-pct]
  (boolean
   (and (some? purity-pct)
        (let [floor (purity-spec-floor-for effective-product-type)]
          (and (some? floor) (< (double purity-pct) (double floor)))))))

;; ----------------------------- draft record construction -----------------------------

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is
  the human plant supervisor's/shipping approver's act, not this
  actor's."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(defn register-maintenance
  "Validate + construct the MAINTENANCE-SCHEDULE DRAFT -- a proposed
  reactor/mixing-tank or filling/packaging-line maintenance window
  against a verified, registered piece of equipment. Pure function --
  does not actuate the reactor/mixing/packaging line or execute any
  maintenance; it builds the RECORD a plant coordinator would keep.
  `adhesivemfg.governor` independently re-verifies the equipment's own
  verified/registered ground truth, and permanently blocks any attempt
  to directly actuate the reactor/mixing line (see README
  `Actuation`), before this is ever allowed to commit."
  [maintenance-id equipment-id sequence]
  (when-not (and maintenance-id (not= maintenance-id ""))
    (throw (ex-info "maintenance: maintenance_id required" {})))
  (when-not (and equipment-id (not= equipment-id ""))
    (throw (ex-info "maintenance: equipment_id required" {})))
  (when (< sequence 0)
    (throw (ex-info "maintenance: sequence must be >= 0" {})))
  (let [maintenance-number (str "MNT-" (zero-pad sequence 6))
        record {"record_id" maintenance-number
                "kind" "maintenance-schedule-draft"
                "maintenance_id" maintenance-id
                "equipment_id" equipment-id
                "immutable" true}]
    {"record" record "maintenance_number" maintenance-number
     "certificate" (unsigned-certificate "MaintenanceSchedule" maintenance-number maintenance-number)}))

(defn register-shipment
  "Validate + construct the SHIPMENT-COORDINATION DRAFT -- a proposed
  outbound adhesive/glue-product shipment against a verified,
  registered production batch. Pure function -- does not dispatch any
  real freight carrier; it builds the RECORD a plant coordinator would
  keep. `adhesivemfg.governor` independently re-verifies the
  shipment's own claimed weight against `shipment-weight-exceeded?`,
  before this is ever allowed to commit."
  [shipment-id sequence]
  (when-not (and shipment-id (not= shipment-id ""))
    (throw (ex-info "shipment: shipment_id required" {})))
  (when (< sequence 0)
    (throw (ex-info "shipment: sequence must be >= 0" {})))
  (let [shipment-number (str "SHP-" (zero-pad sequence 6))
        record {"record_id" shipment-number
                "kind" "shipment-coordination-draft"
                "shipment_id" shipment-id
                "immutable" true}]
    {"record" record "shipment_number" shipment-number
     "certificate" (unsigned-certificate "ShipmentCoordination" shipment-number shipment-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
