(ns adhesivemfg.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 for this repo (com-junkawasaki/root
  ADR-2608090800, industry-stack-wave Wave 7): this repo previously had
  NO demo page and no generator at all.

  This namespace drives the REAL actor stack
  (`adhesivemfg.operation` -> `adhesivemfg.advisor` ->
  `adhesivemfg.governor` -> `adhesivemfg.phase` -> `adhesivemfg.store`)
  through the same langgraph-clj `g/run*` entry point the repo's own
  demo driver uses, and renders the RESULTING store. Nothing on the
  page is hand-typed domain data: every batch, equipment unit,
  maintenance draft, shipment draft, safety concern, record number,
  hold rule and hold detail string below is read back out of the store
  (or out of the run's own returned state) after the graph has actually
  executed.

  Entity ids were verified against `adhesivemfg.store/sample-data!`
  BEFORE this file was written, by running `clojure -M:dev:run` and
  reading its audit ledger -- this repo's own `adhesivemfg.sim` uses
  ids that DO match the seed (`batch-001`..`batch-003`,
  `reactor-001`, `mixer-002`), so the scenario below reuses them
  rather than inventing new ones.

  Determinism: no timestamp, no random value and no environment value
  reaches the page. `adhesivemfg.registry`'s draft record numbers are
  pure sequence functions (`MNT-000000`, `SHP-000000`), the store sorts
  its directories by id, and the ledger is append-only in scenario
  order -- so two consecutive runs are byte-identical.

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [clojure.java.io :as io]
            [kotoba.lang.text :as str]
            [jp-go-dds.skin]
            [langgraph.graph :as g]
            [adhesivemfg.operation :as op]
            [adhesivemfg.registry :as registry]
            [adhesivemfg.store :as store]))

(def ^:private coordinator
  "The same actor context this repo's own demo driver uses."
  {:actor-id "coord-1" :actor-role :plant-coordinator :phase 3})

(def ^:private approver "coord-1")

;; ----------------------------- driving the real actor -----------------------------

(defn- audit-fact
  "First audit fact of type `t` in a run's returned state."
  [state t]
  (first (filter #(= t (:t %)) (:audit state))))

(defn- summarize
  "One row of `Coordination requests (this run)` -- assembled ONLY from
  the request we sent and the state the graph actually returned."
  [tid request first-state final-state]
  (let [verdict (or (:verdict final-state) (:verdict first-state))
        granted (audit-fact final-state :approval-granted)]
    {:thread      tid
     :op          (:op request)
     :subject     (:subject request)
     :target      (or (:equipment-id (:value request))
                      (:batch-id (:value request)))
     :disposition (:disposition final-state)
     :escalated?  (boolean (audit-fact first-state :approval-requested))
     :approved-by (:by granted)
     :confidence  (:confidence verdict)
     :violations  (:violations verdict)}))

(defn- exec!
  "One coordination request. Returns the summarized run."
  [actor tid request]
  (let [{state :state} (g/run* actor {:request request :context coordinator}
                               {:thread-id tid})]
    (summarize tid request state state)))

(defn- exec-approve!
  "One coordination request that the phase gate escalates, followed by a
  real human approval resuming the interrupted graph."
  [actor tid request]
  (let [{first-state :state} (g/run* actor {:request request :context coordinator}
                                     {:thread-id tid})
        {final-state :state} (g/run* actor {:approval {:status :approved :by approver}}
                                     {:thread-id tid :resume? true})]
    (summarize tid request first-state final-state)))

(defn run-demo!
  "Runs a freshly seeded store through a scenario covering the full
  range of dispositions this actor can reach:

    - `batch-001` production-batch logging: governor-clean, no physical
      or financial risk -> phase-3 AUTO-COMMIT with no human involved.
    - `mnt-1` maintenance scheduling on the verified+registered
      `reactor-001`, `concern-1` safety-concern flagging, and `ship-1`
      shipment coordination on `batch-001`: all three ESCALATE (no
      phase ever makes them auto-eligible) and are then APPROVED by a
      human, which commits them.
    - eleven HARD holds that NEVER reach a human, one per governor
      rule this actor enforces: a caller whose request `:effect` is not
      `:propose`, an op outside the closed allowlist (which also trips
      the proposal-effect allowlist), maintenance against the
      UNVERIFIED/unregistered `mixer-002`, a shipment against the
      UNVERIFIED/unregistered `batch-003`, a shipment that would blow
      through `batch-002`'s own logged production weight, a
      maintenance proposal that tries to ACTUATE the line, a batch
      patch that tries to DECIDE a chemical-safety certification, a
      double-schedule of `mnt-1`, and three fabricated batch readings
      (product type, viscosity, purity) plus a purity below the
      hot-melt spec floor.

  Returns {:db store :runs [summarized runs]}."
  []
  (let [db (-> (store/mem-store) (store/sample-data!))
        actor (op/build db)
        runs
        [;; clean, no risk -> auto-commit at phase 3
         (exec! actor "r1"
                {:op :log-production-batch :effect :propose :subject "batch-001"
                 :patch {:product-type :hot-melt-adhesive :last-assessed "2026-07-14"}})

         ;; escalate -> human approves -> commit
         (exec-approve! actor "r2"
                        {:op :schedule-maintenance :effect :propose :subject "mnt-1"
                         :value {:equipment-id "reactor-001" :maintenance-type :seal-inspection
                                 :scheduled-date "2026-08-01" :actuate-line? false}})
         (exec-approve! actor "r3"
                        {:op :flag-safety-concern :effect :propose :subject "concern-1"
                         :value {:equipment-id "reactor-001" :severity :moderate
                                 :description "反応工程周辺の溶剤蒸気曝露リスク上昇"}})
         (exec-approve! actor "r4"
                        {:op :coordinate-shipment :effect :propose :subject "ship-1"
                         :value {:batch-id "batch-001" :weight-kg 500.0
                                 :destination "distributor-yard-north"}})

         ;; HARD holds -- never reach a human
         (exec! actor "r5"
                {:op :log-production-batch :effect :direct-write :subject "batch-001"
                 :patch {:product-type :hot-melt-adhesive}})
         (exec! actor "r6"
                {:op :actuate-reactor :effect :propose :subject "batch-001"})
         (exec! actor "r7"
                {:op :schedule-maintenance :effect :propose :subject "mnt-2"
                 :value {:equipment-id "mixer-002" :maintenance-type :blade-inspection
                         :scheduled-date "2026-08-01" :actuate-line? false}})
         (exec! actor "r8"
                {:op :coordinate-shipment :effect :propose :subject "ship-2"
                 :value {:batch-id "batch-003" :weight-kg 1000.0
                         :destination "distributor-yard-south"}})
         (exec! actor "r9"
                {:op :coordinate-shipment :effect :propose :subject "ship-3"
                 :value {:batch-id "batch-002" :weight-kg 1000.0
                         :destination "distributor-yard-east"}})
         (exec! actor "r10"
                {:op :schedule-maintenance :effect :propose :subject "mnt-3"
                 :value {:equipment-id "reactor-001" :maintenance-type :force-run
                         :scheduled-date "2026-09-01" :actuate-line? true}})
         (exec! actor "r11"
                {:op :log-production-batch :effect :propose :subject "batch-001"
                 :patch {:decide-certification? true}})
         (exec! actor "r12"
                {:op :schedule-maintenance :effect :propose :subject "mnt-1"
                 :value {:equipment-id "reactor-001" :maintenance-type :seal-inspection
                         :scheduled-date "2026-08-01" :actuate-line? false}})
         (exec! actor "r13"
                {:op :log-production-batch :effect :propose :subject "batch-001"
                 :patch {:product-type :unobtainium-adhesive}})
         (exec! actor "r14"
                {:op :log-production-batch :effect :propose :subject "batch-001"
                 :patch {:viscosity-cp -5.0}})
         (exec! actor "r15"
                {:op :log-production-batch :effect :propose :subject "batch-001"
                 :patch {:purity-pct 150.0}})
         (exec! actor "r16"
                {:op :log-production-batch :effect :propose :subject "batch-001"
                 :patch {:purity-pct 40.0}})]]
    {:db db :runs runs}))

;; ----------------------------- rendering helpers -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- nm [v] (if (keyword? v) (name v) (str v)))

(defn- num-cell [v]
  (if (nil? v) "<span class=\"muted\">—</span>"
      (str "<span class=\"num\">" (esc v) "</span>")))

(defn- yes-no [v]
  (if (true? v) "<span class=\"ok\">yes</span>" "<span class=\"critical\">no</span>"))

(defn- rules-str [violations]
  (str/join ", " (map #(nm (:rule %)) violations)))

(defn- details-str [violations]
  (str/join " / " (map :detail violations)))

;; ----------------------------- sections -----------------------------

(defn- batch-row [{:keys [id material product-type weight-kg viscosity-cp
                          purity-pct verified? registered? shipped-weight-kg
                          last-assessed]}]
  (let [floor (registry/purity-spec-floor-for product-type)
        headroom (when (and (number? weight-kg) (number? shipped-weight-kg))
                   (- (double weight-kg) (double shipped-weight-kg)))]
    (format (str "        <tr><td><code>%s</code></td><td>%s</td><td><code>%s</code></td>"
                 "<td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td>"
                 "<td>%s</td><td>%s</td></tr>")
            (esc id) (esc material) (esc (nm product-type))
            (num-cell weight-kg) (num-cell viscosity-cp)
            (num-cell purity-pct) (num-cell floor)
            (yes-no verified?) (yes-no registered?)
            (num-cell shipped-weight-kg)
            (str (num-cell headroom)
                 (when (some? last-assessed)
                   (str " <span class=\"muted\">/ assessed " (esc last-assessed) "</span>"))
                 (when (and (some? floor) (number? purity-pct)
                            (registry/purity-below-spec-floor? product-type purity-pct))
                   " <span class=\"critical\">below spec floor</span>")))))

(defn- equipment-row [{:keys [id kind verified? registered? last-maintenance-date
                              last-scheduled-maintenance-date]}]
  (format "        <tr><td><code>%s</code></td><td><code>%s</code></td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc id) (esc (nm kind)) (yes-no verified?) (yes-no registered?)
          (if last-maintenance-date (esc last-maintenance-date)
              "<span class=\"muted\">never</span>")
          (if last-scheduled-maintenance-date (esc last-scheduled-maintenance-date)
              "<span class=\"muted\">—</span>")))

(defn- maintenance-row [{:keys [id equipment-id maintenance-type scheduled-date
                                scheduled? maintenance-number approved-by]}]
  (format "        <tr><td><code>%s</code></td><td><code>%s</code></td><td><code>%s</code></td><td>%s</td><td>%s</td><td><code>%s</code></td><td>%s</td></tr>"
          (esc id) (esc equipment-id) (esc (nm maintenance-type))
          (esc scheduled-date)
          (if (true? scheduled?) "<span class=\"ok\">scheduled (draft)</span>"
              "<span class=\"muted\">not scheduled</span>")
          (esc maintenance-number)
          (if approved-by (esc approved-by)
              "<span class=\"critical\">not on record</span>")))

(defn- shipment-row [{:keys [id batch-id weight-kg destination shipment-number approved-by]}]
  (format "        <tr><td><code>%s</code></td><td><code>%s</code></td><td>%s</td><td>%s</td><td><code>%s</code></td><td>%s</td></tr>"
          (esc id) (esc batch-id) (num-cell weight-kg) (esc destination)
          (esc shipment-number)
          (if approved-by (esc approved-by)
              "<span class=\"critical\">not on record</span>")))

(defn- concern-row [{:keys [id equipment-id severity description approved-by]}]
  (format "        <tr><td><code>%s</code></td><td><code>%s</code></td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc id) (esc equipment-id) (esc (nm severity)) (esc description)
          (if approved-by (esc approved-by)
              "<span class=\"critical\">not on record</span>")))

(defn- disposition-cell [{:keys [disposition escalated? approved-by violations]}]
  (cond
    (seq violations)
    (str "<span class=\"critical\">HARD hold · never reaches a human</span>"
         "<br><span class=\"muted\">" (esc (rules-str violations)) "</span>")

    (and (= :commit disposition) escalated?)
    (str "<span class=\"ok\">approved &amp; committed</span>"
         "<br><span class=\"muted\">human approver in run audit: "
         (esc (or approved-by "—")) "</span>")

    (= :commit disposition)
    "<span class=\"ok\">auto-committed · phase 3, governor-clean</span>"

    (= :hold disposition) "<span class=\"critical\">hold</span>"
    :else (str "<span class=\"warn\">" (esc (nm disposition)) "</span>")))

(defn- run-row [{:keys [thread op subject target confidence violations] :as r}]
  (format "        <tr><td><code>%s</code></td><td><code>%s</code></td><td><code>%s</code></td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc thread) (esc (nm op)) (esc subject)
          (if target (str "<code>" (esc target) "</code>") "<span class=\"muted\">—</span>")
          (num-cell confidence)
          (disposition-cell r)
          (if (seq violations)
            (esc (details-str violations))
            "<span class=\"muted\">—</span>")))

(defn- ledger-row [{:keys [t op subject disposition basis violations]}]
  (format "        <tr><td>%s</td><td><code>%s</code></td><td><code>%s</code></td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (case t
            :committed "<span class=\"ok\">committed</span>"
            :governor-hold "<span class=\"critical\">governor-hold</span>"
            (str "<span class=\"warn\">" (esc (nm t)) "</span>"))
          (esc (nm op)) (esc subject)
          (esc (nm (or disposition :n-a)))
          (if (seq basis) (esc (str/join ", " (map nm basis)))
              "<span class=\"muted\">—</span>")
          (if (seq violations) (esc (details-str violations))
              "<span class=\"muted\">—</span>")))

(def ^:private action-gate-rows
  ;; Static description of this actor's own CLOSED op contract, quoting
  ;; `adhesivemfg.phase/phases` and `adhesivemfg.governor`'s rule set.
  ;; Fixed behavior documented in prose, not runtime telemetry -- every
  ;; runtime number on this page comes from the store instead.
  ["        <tr><td><code>:log-production-batch</code></td><td><span class=\"ok\">phase-3 auto-commit when governor-clean</span> · product type / viscosity / purity / purity-vs-spec-floor independently re-derived</td></tr>"
   "        <tr><td><code>:schedule-maintenance</code></td><td><span class=\"warn\">ALWAYS human approval · never in any phase's <code>:auto</code> set</span> · equipment verified?+registered? independently re-derived · double-schedule refused · <span class=\"critical\">line actuation permanently blocked</span></td></tr>"
   "        <tr><td><code>:flag-safety-concern</code></td><td><span class=\"warn\">ALWAYS human approval · <code>:coordination/safety-concern</code> is high-stakes at every confidence</span> · never gated on the equipment being verified (safety reporting is not blocked on an administrative technicality)</td></tr>"
   "        <tr><td><code>:coordinate-shipment</code></td><td><span class=\"warn\">ALWAYS human approval · never in any phase's <code>:auto</code> set</span> · batch verified?+registered? independently re-derived · shipment weight recomputed against the batch's own logged production weight, never the claim</td></tr>"])

;; ----------------------------- approver attribution -----------------------------

(defn- ssot-record
  "The record the store ACTUALLY holds for an approved run."
  [db {:keys [op subject]}]
  (case op
    :schedule-maintenance (store/maintenance db subject)
    :coordinate-shipment  (store/shipment db subject)
    :flag-safety-concern  (first (filter #(= subject (:id %)) (store/safety-concerns db)))
    :log-production-batch (store/batch db subject)
    nil))

(defn- attribution
  "For every run a human approved, compare the approver the RUN AUDIT
  carries against what the SSoT record actually holds.

  This repo carries the fleet-wide scaffold defect:
  `adhesivemfg.operation/commit-record` writes the approver under the
  record's `:payload` key, but `adhesivemfg.store/commit-record!`
  destructures `{:keys [effect path value]}` and never reads
  `:payload`, so `:approved-by` never reaches the SSoT. The store
  ledger does not carry it either -- the `:approval-granted` fact is
  appended to the run's `:audit` channel only, while the `:commit` node
  appends just the `:committed` fact to `store/ledger`.

  Both columns below are computed, not asserted: `on-record?` is
  `(contains? <the record the store returned> :approved-by)`."
  [db runs]
  (for [r runs
        :when (:approved-by r)
        :let [rec (ssot-record db r)]]
    (assoc r
           :ssot-approved-by (get rec :approved-by)
           :on-record? (boolean (and (map? rec) (contains? rec :approved-by))))))

(defn- attribution-row [{:keys [op subject approved-by ssot-approved-by on-record?]}]
  (format "        <tr><td><code>%s</code></td><td><code>%s</code></td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc (nm op)) (esc subject) (esc approved-by)
          (if on-record? (esc ssot-approved-by)
              "<span class=\"critical\">absent</span>")
          (if on-record?
            "<span class=\"ok\">approver reached the SSoT</span>"
            "<span class=\"critical\">approver NOT on the record — run audit only</span>")))

;; ----------------------------- document -----------------------------

(defn render
  "Renders operator-console.html from a store `db` that has already run
  `run-demo!`, plus that run's own summarized results."
  [{:keys [db runs]}]
  (let [ledger (vec (store/ledger db))
        holds (filterv #(= :governor-hold (:t %)) ledger)
        commits (filterv #(= :committed (:t %)) ledger)
        approvals-in-ledger (filterv #(= :approval-granted (:t %)) ledger)
        attrib (vec (attribution db runs))
        maintenance (vec (store/all-maintenance db))
        shipments (keep #(store/shipment db (get % "shipment_id"))
                        (store/shipment-history db))
        concerns (vec (store/safety-concerns db))]
    (str
     "<!DOCTYPE html>\n"
     "<html lang=\"en\"><head><meta charset=\"utf-8\">"
     "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, viewport-fit=cover\">"
     "<title>cloud-itonami-isic-2029 · adhesives &amp; glues plant operations — Operator Console</title><style>"
     (jp-go-dds.skin/dds+skin)
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Manufacture of other chemical products n.e.c. (ISIC 2029) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · maintenance / safety-concern / shipment always human-approved · line actuation permanently blocked</span>\n"
     "</header>\n"
     "<main class=\"container\">\n"

     "  <section class=\"card\">\n"
     "    <h2>How this page was produced</h2>\n"
     "    <p class=\"muted\">Build-time generated by <code>adhesivemfg.render-html</code> (<code>clojure -M:dev:render-html</code>). It seeds <code>adhesivemfg.store/sample-data!</code>, compiles the real <code>adhesivemfg.operation</code> langgraph StateGraph and executes "
     (esc (count runs))
     " coordination requests through it. Every number, id, record number, rule name and hold reason below is read back out of the store (or out of the run's own returned state) after the graph ran — none of it is hand-written.</p>\n"
     "    <p class=\"muted\">This run produced <span class=\"num\">" (esc (count commits))
     "</span> commits and <span class=\"num\">" (esc (count holds))
     "</span> HARD governor holds. The generator throws if the HARD-hold count is zero, so a build that silently stopped refusing anything cannot ship this page.</p>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Coordination requests (this run)</h2>\n"
     "    <p class=\"muted\">One row per graph run. <strong>HARD holds never reach a human</strong> — no phase and no approver can override them.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Run</th><th>Op</th><th>Subject</th><th>Target</th><th>Advisor confidence</th><th>Disposition</th><th>Governor detail</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map run-row runs)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Production batches</h2>\n"
     "    <p class=\"muted\">Live SSoT after the run. Purity is checked against <code>adhesivemfg.registry/purity-spec-floor-pct</code> — a representative minimum active-solids floor per product family, independently re-derived, never taken from the advisor's self-report. Headroom is the batch's own logged production weight minus its own cumulative shipped weight.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Batch</th><th>Material</th><th>Product type</th><th>Weight (kg)</th><th>Viscosity (cP)</th><th>Purity (%)</th><th>Spec floor (%)</th><th>Verified?</th><th>Registered?</th><th>Shipped (kg)</th><th>Headroom (kg)</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map batch-row (store/all-batches db))) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Equipment</h2>\n"
     "    <p class=\"muted\">Maintenance may only ever be <em>drafted</em> against a unit that is independently both verified and registered.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Unit</th><th>Kind</th><th>Verified?</th><th>Registered?</th><th>Last maintenance</th><th>Last scheduled window</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map equipment-row (store/all-equipment db))) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Maintenance windows (drafts)</h2>\n"
     "    <p class=\"muted\">Draft records only — this actor never actuates a reactor, mixing tank or filling/packaging line. Record numbers come from <code>adhesivemfg.registry/register-maintenance</code>.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Maintenance</th><th>Equipment</th><th>Type</th><th>Scheduled date</th><th>State</th><th>Record no.</th><th>Approver on record</th></tr></thead>\n"
     "      <tbody>\n"
     (if (seq maintenance)
       (str (str/join "\n" (map maintenance-row maintenance)) "\n")
       "        <tr><td colspan=\"7\"><span class=\"muted\">none</span></td></tr>\n")
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Shipment coordination (drafts)</h2>\n"
     "    <p class=\"muted\">Draft records only — no freight carrier is ever dispatched. Record numbers come from <code>adhesivemfg.registry/register-shipment</code>.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Shipment</th><th>Batch</th><th>Weight (kg)</th><th>Destination</th><th>Record no.</th><th>Approver on record</th></tr></thead>\n"
     "      <tbody>\n"
     (if (seq shipments)
       (str (str/join "\n" (map shipment-row shipments)) "\n")
       "        <tr><td colspan=\"6\"><span class=\"muted\">none</span></td></tr>\n")
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Safety concerns</h2>\n"
     "    <p class=\"muted\">A safety concern is always high-stakes and always needs a human, at any confidence — and is never blocked on the referenced equipment being verified.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Concern</th><th>Equipment</th><th>Severity</th><th>Description</th><th>Approver on record</th></tr></thead>\n"
     "      <tbody>\n"
     (if (seq concerns)
       (str (str/join "\n" (map concern-row concerns)) "\n")
       "        <tr><td colspan=\"5\"><span class=\"muted\">none</span></td></tr>\n")
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Approver attribution — a known scaffold defect, rendered honestly</h2>\n"
     "    <p class=\"muted\"><code>adhesivemfg.operation</code>'s approval node attaches the approver under the record's <code>:payload</code> key, but <code>adhesivemfg.store/commit-record!</code> destructures <code>{:keys [effect path value]}</code> and never reads <code>:payload</code>. The store ledger does not carry it either: the <code>:approval-granted</code> fact goes to the run's <code>:audit</code> channel, while only the <code>:committed</code> fact is appended to <code>store/ledger</code> — this run's ledger holds <span class=\"num\">"
     (esc (count approvals-in-ledger))
     "</span> <code>:approval-granted</code> facts. So the approver exists in the transient run audit and <strong>nowhere in the SSoT</strong>. The right-hand columns are computed with <code>(contains? &lt;the record the store returned&gt; :approved-by)</code>, not asserted — this page prints no name the store does not actually hold.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Subject</th><th>Approver (run audit)</th><th>Approver (SSoT record)</th><th>Verdict</th></tr></thead>\n"
     "      <tbody>\n"
     (if (seq attrib)
       (str (str/join "\n" (map attribution-row attrib)) "\n")
       "        <tr><td colspan=\"5\"><span class=\"muted\">no approved runs</span></td></tr>\n")
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Action gate (Adhesive Plant Operations Governor)</h2>\n"
     "    <p class=\"muted\">HARD holds cannot be overridden by any phase or any human. Directly actuating the reactor / mixing tank / filling / packaging line, and deciding or granting a chemical-safety certification, are permanent scope boundaries — not rollout milestones still to come.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Gate</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" action-gate-rows) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (this run)</h2>\n"
     "    <p class=\"muted\">Append-only decision-fact log read straight out of <code>adhesivemfg.store/ledger</code> — every commit and every refusal this scenario produced, in order.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Fact</th><th>Op</th><th>Subject</th><th>Disposition</th><th>Basis</th><th>Detail</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map ledger-row ledger)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "</main>\n"
     "<footer class=\"container\">\n"
     "  <p class=\"muted\">cloud-itonami-isic-2029 · generated by <code>adhesivemfg.render-html</code> from the real actor stack. Regenerate with <code>clojure -M:dev:render-html</code>.</p>\n"
     "</footer>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        {:keys [db runs] :as result} (run-demo!)
        ledger (vec (store/ledger db))
        holds (filterv #(= :governor-hold (:t %)) ledger)
        commits (filterv #(= :committed (:t %)) ledger)]
    ;; Build-time invariant: a console that shows no HARD refusal is not
    ;; a governed-actor console. If the governor ever stops refusing,
    ;; this build fails rather than shipping a page that looks clean.
    (when (zero? (count holds))
      (throw (ex-info "operator-console: zero HARD governor holds in the rendered run -- refusing to ship a console that shows no refusal"
                      {:ledger-facts (count ledger)
                       :commits (count commits)
                       :hard-holds 0})))
    (io/make-parents out)
    (spit out (render result) :encoding "UTF-8")
    (println "wrote" out
             (str "(" (count runs) " runs, " (count ledger) " ledger facts, "
                  (count commits) " commits, " (count holds) " HARD holds, "
                  (count (store/maintenance-history db)) " maintenance drafts, "
                  (count (store/shipment-history db)) " shipment drafts, "
                  (count (store/safety-concerns db)) " safety concerns)"))))
