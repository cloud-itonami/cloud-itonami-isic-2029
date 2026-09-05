(ns adhesivemfg.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 for this repo: it previously had NO
  operator console and no generator at all. This namespace drives the
  REAL actor stack -- `adhesivemfg.operation` (a langgraph-clj
  StateGraph, run via `langgraph.graph/run*`) -> `adhesivemfg.governor`
  -> `adhesivemfg.store` -- and renders whatever that run actually
  produced. Every id, number, disposition, hold reason and hold detail
  string on the page is read back out of the live governor verdict or
  the live store at build time. Nothing on the page is hand-typed
  domain content: there are no invented operators, customers,
  companies or figures, and the only prose is the description of this
  actor's own closed contract, whose op/phase/spec-floor tables are
  themselves derived from `adhesivemfg.governor`, `adhesivemfg.phase`
  and `adhesivemfg.registry` rather than transcribed.

  Scenario provenance: `adhesivemfg.sim` (`clojure -M:dev:run`) was run
  first and its subject ids checked against `adhesivemfg.store`'s own
  `sample-data!` seed -- `batch-001`/`batch-002`/`batch-003` and
  `reactor-001`/`mixer-002` all DO exist in the seed, so the sim's
  scenario is sound and this file adapts it rather than replacing it.
  (Some sibling repos' sims reference ids absent from their own seed;
  this one does not.) `mnt-*`, `concern-*` and `ship-*` are not seeded
  entities -- they are the record ids the ops themselves create.

  Determinism: no timestamp, no random, no map-iteration order in page
  content. Every collection rendered is either an append-only vector
  (`store/ledger`, `store/maintenance-history`, `store/shipment-history`,
  `store/safety-concerns`) or explicitly sorted (`store/all-batches` and
  `store/all-equipment` sort by `:id`; the contract tables sort their
  keyword keys by `name`). Two consecutive runs are byte-identical.

  Build-time invariant: `-main` counts the governor holds it rendered
  and THROWS if that count is zero, or if no HARD (never-overridable)
  hold is among them. A future regression that quietly lets every
  proposal through would therefore fail this build instead of shipping
  a console that looks clean.

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [clojure.string :as str]
            [jp-go-dds.skin]
            [langgraph.graph :as g]
            [adhesivemfg.governor :as governor]
            [adhesivemfg.operation :as op]
            [adhesivemfg.phase :as phase]
            [adhesivemfg.registry :as registry]
            [adhesivemfg.store :as store]))

(def ^:private coordinator
  "The same demo context `adhesivemfg.sim` uses -- phase 3
  (supervised-auto), the highest phase this actor has."
  {:actor-id "coord-1" :actor-role :plant-coordinator :phase 3})

(def ^:private permanent-rules
  "The two governor rules whose own docstrings state they are PERMANENT
  and unconditional -- no phase and no human approval can ever release
  them (`adhesivemfg.governor`, checks 4 and 5). Every other HARD rule
  is equally un-overridable on this run, but these two are structural
  scope boundaries rather than facts that could change if the store
  changed."
  #{:line-actuate-blocked :certification-decision-blocked})

;; ----------------------------- driving the real actor -----------------------------

(defn- exec!
  "One coordination request through the compiled actor graph."
  [actor tid request]
  (g/run* actor {:request request :context coordinator} {:thread-id tid}))

(defn- approve!
  "Resume a thread paused by `interrupt-before #{:request-approval}`
  with a human plant supervisor's / shipping approver's approval."
  [actor tid by]
  (g/run* actor {:approval {:status :approved :by by}}
          {:thread-id tid :resume? true}))

(defn run-demo!
  "Runs a fresh seeded store through a scenario mixing every
  disposition this actor can reach, then returns
  `{:db <store> :runs [{:thread .. :label .. :request .. :audit [..]}]}`.

  Clean lifecycle (all four ops, all four propose-shaped effects):
    - `batch-001` `:log-production-batch` with a clean patch --
      phase-3 AUTO-commit (the only op in phase 3's `:auto` set).
    - `mnt-1` `:schedule-maintenance` on `reactor-001` (verified AND
      registered in the seed) -- `:schedule-maintenance` is never in any
      phase's `:auto` set, so it escalates even though the governor is
      clean; a human approves and it commits.
    - `concern-1` `:flag-safety-concern` on `reactor-001` -- ALWAYS
      escalates (`:coordination/safety-concern` is in
      `governor/high-stakes`); approved.
    - `ship-1` `:coordinate-shipment` on `batch-001` for 500 kg (within
      the batch's own logged 5000 kg less its 1000 kg already shipped)
      -- escalates, approved, commits.

  HARD holds, none of which ever reaches a human (the graph routes
  `:hold` straight to the ledger, never to `:request-approval`):
    - `mnt-3` maintenance on `reactor-001` declaring
      `:actuate-line? true` -- `:line-actuate-blocked`, PERMANENT.
    - `batch-001` batch patch declaring `:decide-certification? true`
      -- `:certification-decision-blocked`, PERMANENT.
    - `mnt-2` maintenance on `mixer-002` (UNVERIFIED and unregistered
      in the seed) -- `:equipment-not-verified`.
    - `ship-2` shipment on `batch-003` (UNVERIFIED and unregistered)
      -- `:batch-not-verified`.
    - `ship-3` shipment on `batch-002` for 1000 kg, against a batch
      whose own record is 8000 kg produced / 7500 kg already shipped
      -- `:shipment-weight-exceeded`, recomputed by the governor from
      the batch's own fields, never from the proposal's claim.
    - `batch-001` batch patch declaring 40.0% purity, below the
      `:hot-melt-adhesive` spec floor -- `:purity-below-spec-floor`.
    - `mnt-1` scheduled a SECOND time -- `:already-scheduled`.
    - a caller whose own request `:effect` is not `:propose` --
      `:not-propose-effect`, evaluated before anything else."
  []
  (let [db (-> (store/mem-store) (store/sample-data!))
        actor (op/build db)
        runs (volatile! [])
        record! (fn [thread label request result]
                  (vswap! runs conj {:thread thread :label label :request request
                                     :audit (vec (get-in result [:state :audit]))})
                  result)
        clean! (fn [thread label request]
                 (record! thread label request (exec! actor thread request)))
        approved! (fn [thread label request by]
                    (exec! actor thread request)
                    (record! thread label request (approve! actor thread by)))]

    ;; ---- clean lifecycle ----
    (clean! "t1" "生産バッチ記録更新 (governor-clean, phase-3 auto-commit)"
            {:op :log-production-batch :effect :propose :subject "batch-001"
             :patch {:product-type :hot-melt-adhesive :last-assessed "2026-07-14"}})

    (approved! "t2" "保守作業予定 (検証済み設備, 人手承認)"
               {:op :schedule-maintenance :effect :propose :subject "mnt-1"
                :value {:equipment-id "reactor-001" :maintenance-type :seal-inspection
                        :scheduled-date "2026-08-01" :actuate-line? false}}
               "coord-1")

    (approved! "t3" "安全懸念報告 (常に人手承認)"
               {:op :flag-safety-concern :effect :propose :subject "concern-1"
                :value {:equipment-id "reactor-001" :severity :moderate
                        :description "反応工程周辺の溶剤蒸気曝露リスク上昇"}}
               "coord-1")

    (approved! "t4" "出荷調整 (空き容量内, 人手承認)"
               {:op :coordinate-shipment :effect :propose :subject "ship-1"
                :value {:batch-id "batch-001" :weight-kg 500.0
                        :destination "distributor-yard-north"}}
               "coord-1")

    ;; ---- HARD holds ----
    (clean! "t5" "ライン直接操作(actuate)提案"
            {:op :schedule-maintenance :effect :propose :subject "mnt-3"
             :value {:equipment-id "reactor-001" :maintenance-type :force-run
                     :scheduled-date "2026-09-01" :actuate-line? true}})

    (clean! "t6" "化学品安全認証の可否判断の代行"
            {:op :log-production-batch :effect :propose :subject "batch-001"
             :patch {:decide-certification? true}})

    (clean! "t7" "未検証・未登録の混合タンクへの保守作業予定"
            {:op :schedule-maintenance :effect :propose :subject "mnt-2"
             :value {:equipment-id "mixer-002" :maintenance-type :blade-inspection
                     :scheduled-date "2026-08-01" :actuate-line? false}})

    (clean! "t8" "未検証・未登録バッチからの出荷調整"
            {:op :coordinate-shipment :effect :propose :subject "ship-2"
             :value {:batch-id "batch-003" :weight-kg 1000.0
                     :destination "distributor-yard-south"}})

    (clean! "t9" "記録済み生産量を超える出荷申請"
            {:op :coordinate-shipment :effect :propose :subject "ship-3"
             :value {:batch-id "batch-002" :weight-kg 1000.0
                     :destination "distributor-yard-east"}})

    (clean! "t10" "スペック下限を下回る純度の記録"
            {:op :log-production-batch :effect :propose :subject "batch-001"
             :patch {:purity-pct 40.0}})

    (clean! "t11" "同一保守作業の二重予定"
            {:op :schedule-maintenance :effect :propose :subject "mnt-1"
             :value {:equipment-id "reactor-001" :maintenance-type :seal-inspection
                     :scheduled-date "2026-08-01" :actuate-line? false}})

    (clean! "t12" "request :effect が :propose ではない呼び出し元"
            {:op :log-production-batch :effect :direct-write :subject "batch-001"
             :patch {:product-type :hot-melt-adhesive}})

    {:db db :runs @runs}))

;; ----------------------------- derived facts (no hand-typed content) -----------------------------

(defn- hold-facts
  "Governor HOLD facts, in ledger order. `:violations` non-empty means
  the governor itself rejected the proposal (a HARD hold); an empty
  `:violations` would mean the phase gate disabled the op."
  [ledger]
  (filterv #(= :governor-hold (:t %)) ledger))

(defn- hard-hold? [f] (boolean (seq (:violations f))))

(defn- permanent-hold? [f]
  (boolean (some #(permanent-rules (:rule %)) (:violations f))))

(defn- audit-of [{:keys [runs]} thread]
  (:audit (first (filter #(= thread (:thread %)) runs))))

(defn- facts-of-type [audit t] (filterv #(= t (:t %)) audit))

(defn- holds-that-reached-a-human
  "Real invariant check, recomputed from the run audits: a HARD governor
  hold must never have produced an approval request on the same thread.
  Returns the offending thread ids (expected: none)."
  [{:keys [runs]}]
  (vec (for [{:keys [thread audit]} runs
             :when (and (some #(and (= :governor-hold (:t %)) (seq (:violations %))) audit)
                        (seq (facts-of-type audit :approval-requested)))]
         thread)))

(defn- stored-record-for
  "The record the store actually holds for a committed op, so the page
  can check what really landed rather than assume."
  [db op subject]
  (case op
    :schedule-maintenance (store/maintenance db subject)
    :coordinate-shipment  (store/shipment db subject)
    :flag-safety-concern  (first (filter #(= subject (:id %)) (store/safety-concerns db)))
    :log-production-batch (store/batch db subject)
    nil))

(defn- approvals
  "Every `:approval-granted` audit fact, joined back to the record the
  store actually holds. `:on-record?` is the honest answer to 'does the
  SSoT carry the approver?'.

  KNOWN SCAFFOLD DEFECT (fleet-wide, present in this repo):
  `adhesivemfg.operation`'s `:request-approval` node attaches the
  approver as `(assoc record :payload (assoc (:value proposal)
  :approved-by (:by approval)))`, but `adhesivemfg.store`'s
  `commit-record!` destructures `{:keys [effect path value]}` and never
  reads `:payload` -- so the approver never reaches the store. The page
  therefore prints the approver as an AUDIT-ledger join and states
  plainly that it is not on the record, instead of printing a name the
  store does not hold."
  [{:keys [db runs]}]
  (vec (for [{:keys [thread audit]} runs
             f (facts-of-type audit :approval-granted)
             :let [rec (stored-record-for db (:op f) (:subject f))]]
         {:thread thread :op (:op f) :subject (:subject f) :by (:by f)
          :on-record? (contains? rec :approved-by)})))

;; ----------------------------- html -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- kw [v] (if (keyword? v) (name v) (str v)))

(defn- yn [b] (if b "<span class=\"ok\">yes</span>" "<span class=\"critical\">no</span>"))

(defn- num [v] (str "<span class=\"num\">" (esc v) "</span>"))

(defn- rows [xs] (str/join "\n" xs))

(defn- last-fact-for [ledger subject]
  (last (filter #(= subject (:subject %)) ledger)))

(defn- status-cell [ledger subject]
  (let [f (last-fact-for ledger subject)]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (= :committed (:t f)) "<span class=\"ok\">committed</span>"
      (= :governor-hold (:t f))
      (str "<span class=\"critical\">HARD hold &middot; "
           (esc (kw (or (-> f :violations first :rule) :phase-disabled)))
           "</span>")
      (= :approval-rejected (:t f)) "<span class=\"critical\">approval rejected</span>"
      :else "<span class=\"muted\">in progress</span>")))

(defn- batch-row [ledger {:keys [id material product-type weight-kg shipped-weight-kg
                                 purity-pct viscosity-cp verified? registered?]}]
  (let [floor (registry/purity-spec-floor-for product-type)]
    (format (str "        <tr><td><code>%s</code></td><td>%s</td><td><code>%s</code></td>"
                 "<td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>")
            (esc id) (esc material) (esc (kw product-type))
            (num weight-kg) (num shipped-weight-kg) (num viscosity-cp)
            (str (num purity-pct) " <span class=\"muted\">/ floor " (esc floor) "</span>")
            (yn verified?) (yn registered?)
            (status-cell ledger id))))

(defn- equipment-row [ledger {:keys [id kind verified? registered?
                                     last-maintenance-date last-scheduled-maintenance-date]}]
  (format (str "        <tr><td><code>%s</code></td><td><code>%s</code></td>"
               "<td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>")
          (esc id) (esc (kw kind)) (yn verified?) (yn registered?)
          (if last-maintenance-date (esc last-maintenance-date)
              "<span class=\"muted\">none on record</span>")
          (if last-scheduled-maintenance-date
            (str "<span class=\"ok\">" (esc last-scheduled-maintenance-date) "</span>")
            "<span class=\"muted\">none</span>")))

(defn- op-gate-row [op]
  (let [ph phase/default-phase
        {:keys [writes auto]} (get phase/phases ph)]
    (format "        <tr><td><code>:%s</code></td><td>%s</td><td>%s</td></tr>"
            (esc (kw op))
            (if (contains? writes op)
              "<span class=\"ok\">enabled</span>"
              "<span class=\"critical\">disabled &middot; HOLD</span>")
            (if (contains? auto op)
              "<span class=\"ok\">auto-commit when governor-clean</span>"
              "<span class=\"warn\">ALWAYS human approval &middot; never auto at any phase</span>"))))

(defn- phase-row [[n {:keys [label writes auto]}]]
  (format "        <tr><td>%s</td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc n) (esc label)
          (if (seq writes) (str/join ", " (map #(str "<code>:" (esc (kw %)) "</code>")
                                               (sort-by kw writes)))
              "<span class=\"muted\">none (read-only)</span>")
          (if (seq auto) (str/join ", " (map #(str "<code>:" (esc (kw %)) "</code>")
                                             (sort-by kw auto)))
              "<span class=\"muted\">none &middot; every write needs a human</span>")))

(defn- spec-floor-row [[product-type floor]]
  (format "        <tr><td><code>:%s</code></td><td>%s</td></tr>"
          (esc (kw product-type)) (num (str floor " %"))))

(defn- hold-row [f]
  (let [v (first (:violations f))]
    (format (str "        <tr><td><code>:%s</code></td><td><code>%s</code></td>"
                 "<td><code>:%s</code></td><td>%s</td><td>%s</td><td>%s</td></tr>")
            (esc (kw (:rule v))) (esc (:subject f)) (esc (kw (:op f)))
            (if (permanent-hold? f)
              "<span class=\"critical\">PERMANENT</span>"
              "<span class=\"critical\">HARD</span>")
            (num (:confidence f))
            (esc (:detail v)))))

(defn- ledger-row [{:keys [t op subject basis disposition]}]
  (format (str "        <tr><td>%s</td><td><code>:%s</code></td><td><code>%s</code></td>"
               "<td>%s</td><td>%s</td></tr>")
          (case t
            :committed "<span class=\"ok\">committed</span>"
            :governor-hold "<span class=\"critical\">governor-hold</span>"
            :approval-rejected "<span class=\"critical\">approval-rejected</span>"
            (str "<span class=\"muted\">" (esc (kw t)) "</span>"))
          (esc (kw op)) (esc subject)
          (esc (kw (or disposition "")))
          (if (seq basis)
            (str/join ", " (map #(str "<code>" (esc (kw %)) "</code>") basis))
            "<span class=\"muted\">&mdash;</span>")))

(defn- approval-row [{:keys [op subject by on-record?]}]
  (format (str "        <tr><td><code>%s</code></td><td><code>:%s</code></td>"
               "<td><code>%s</code></td><td>%s</td></tr>")
          (esc subject) (esc (kw op)) (esc by)
          (if on-record?
            "<span class=\"ok\">yes &middot; :approved-by on the stored record</span>"
            (str "<span class=\"warn\">no &middot; audit-ledger join only</span>"))))

(defn- draft-row [r]
  (format "        <tr><td><code>%s</code></td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc (get r "record_id")) (esc (get r "kind"))
          (esc (or (get r "maintenance_id") (get r "shipment_id")))
          (esc (or (get r "equipment_id") "&mdash;"))))

(defn- concern-row [{:keys [id equipment-id severity description]}]
  (format "        <tr><td><code>%s</code></td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc id) (esc equipment-id)
          (str "<span class=\"warn\">" (esc (kw severity)) "</span>")
          (esc description)))

(defn render
  "Renders the whole operator-console document from the result of
  `run-demo!` (or any other real run of this actor)."
  [{:keys [db] :as result}]
  (let [ledger (vec (store/ledger db))
        holds (hold-facts ledger)
        hard (filterv hard-hold? holds)
        perm (filterv permanent-hold? holds)
        commits (filterv #(= :committed (:t %)) ledger)
        appr (approvals result)
        leaked (holds-that-reached-a-human result)]
    (str
     "<!doctype html>\n<html lang=\"ja\"><head><meta charset=\"utf-8\">"
     "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
     "<title>cloud-itonami-isic-2029 &middot; adhesive &amp; glue plant operations</title><style>\n"
     (jp-go-dds.skin/dds+skin)
     "\n</style></head><body>\n"

     "<header class=\"bar\">\n"
     "  <h1>Manufacture of other chemical products n.e.c. (ISIC 2029) &mdash; Adhesive plant operator console</h1>\n"
     "</header>\n"
     "<p class=\"subtitle\"><span class=\"badge\">read-only sample</span> "
     "<span class=\"badge\">governor-gated</span> "
     "<span class=\"badge\">propose-only &middot; never actuates a reactor / mixing / packaging line</span> "
     "<span class=\"badge\">never decides a chemical-safety certification</span></p>\n"

     "<main>\n"

     "  <section class=\"card\">\n"
     "    <h2>This run</h2>\n"
     "    <p class=\"muted\">Build-time snapshot generated by <code>adhesivemfg.render-html</code> "
     "(<code>clojure -M:dev:render-html</code>) by driving the real actor graph "
     "<code>adhesivemfg.operation</code> &rarr; <code>adhesivemfg.governor</code> &rarr; "
     "<code>adhesivemfg.store</code> through <code>langgraph.graph/run*</code>. "
     "Every figure below was read back out of that run &mdash; none is hand-written.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Measure</th><th>Value</th></tr></thead>\n"
     "      <tbody>\n"
     (rows
      [(format "        <tr><td>Coordination requests routed</td><td>%s</td></tr>"
               (num (count (:runs result))))
       (format "        <tr><td>Commits reaching the SSoT</td><td>%s</td></tr>"
               (num (count commits)))
       (format "        <tr><td>Governor holds</td><td>%s</td></tr>"
               (str "<span class=\"critical\">" (num (count holds)) "</span>"))
       (format "        <tr><td>&hellip; of which HARD (no human override)</td><td>%s</td></tr>"
               (str "<span class=\"critical\">" (num (count hard)) "</span>"))
       (format "        <tr><td>&hellip; of which PERMANENT scope boundaries</td><td>%s</td></tr>"
               (str "<span class=\"critical\">" (num (count perm)) "</span>"))
       (format "        <tr><td>Human approvals granted</td><td>%s</td></tr>"
               (num (count appr)))
       (format "        <tr><td>HARD holds that reached a human</td><td>%s</td></tr>"
               (if (seq leaked)
                 (str "<span class=\"critical\">" (num (str/join ", " leaked)) "</span>")
                 (str (num 0) " <span class=\"ok\">&mdash; invariant holds</span>")))])
     "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Production batches</h2>\n"
     "    <p class=\"muted\">The SSoT after this run. <code>verified?</code> and <code>registered?</code> "
     "are ground-truth fields on the batch's own record &mdash; the governor re-derives them itself and "
     "never takes the advisor's word for them. The purity floor column is this product type's own entry in "
     "<code>adhesivemfg.registry/purity-spec-floor-pct</code>.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Batch</th><th>Material</th><th>Product type</th><th>Produced (kg)</th>"
     "<th>Shipped (kg)</th><th>Viscosity (cP)</th><th>Purity (%)</th><th>Verified?</th>"
     "<th>Registered?</th><th>Last op</th></tr></thead>\n"
     "      <tbody>\n"
     (rows (map (partial batch-row ledger) (store/all-batches db))) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Equipment</h2>\n"
     "    <p class=\"muted\">Maintenance may only ever be scheduled against a unit that is both verified "
     "and registered. The scheduled column is written by <code>commit-record!</code> on a real commit.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Unit</th><th>Kind</th><th>Verified?</th><th>Registered?</th>"
     "<th>Last maintenance</th><th>Scheduled by this run</th></tr></thead>\n"
     "      <tbody>\n"
     (rows (map (partial equipment-row ledger) (store/all-equipment db))) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Governor holds &mdash; what this actor refused</h2>\n"
     "    <p class=\"muted\">Read straight out of the audit ledger this run produced. A HARD hold is routed "
     "from <code>:decide</code> directly to the ledger and never reaches <code>:request-approval</code>, so "
     "no human can release it. PERMANENT marks the two rules "
     "(<code>:line-actuate-blocked</code>, <code>:certification-decision-blocked</code>) that are structural "
     "scope boundaries rather than facts that could change if the store changed. The detail column is the "
     "governor's own message.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Rule</th><th>Subject</th><th>Op</th><th>Class</th><th>Advisor confidence</th>"
     "<th>Governor detail</th></tr></thead>\n"
     "      <tbody>\n"
     (rows (map hold-row holds)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Action gate (Adhesive Plant Operations Governor)</h2>\n"
     "    <p class=\"muted\">Derived from <code>adhesivemfg.governor/allowed-ops</code> and "
     "<code>adhesivemfg.phase/phases</code> at phase " (esc phase/default-phase)
     " (<code>" (esc (:label (get phase/phases phase/default-phase))) "</code>), the highest phase this "
     "actor has. An op outside this closed allowlist is a HARD hold on sight.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Writes at this phase</th><th>Auto-commit</th></tr></thead>\n"
     "      <tbody>\n"
     (rows (map op-gate-row (sort-by kw governor/allowed-ops))) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "    <h3>Staged rollout</h3>\n"
     "    <table>\n"
     "      <thead><tr><th>Phase</th><th>Label</th><th>May write</th><th>May auto-commit</th></tr></thead>\n"
     "      <tbody>\n"
     (rows (map phase-row (sort-by key phase/phases))) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "    <h3>Purity spec floors</h3>\n"
     "    <p class=\"muted\">The closed minimum active-solids/purity table the governor re-derives "
     "<code>:purity-below-spec-floor</code> against. Representative values, not an exhaustive "
     "multi-vendor specification database.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Product type</th><th>Minimum purity</th></tr></thead>\n"
     "      <tbody>\n"
     (rows (map spec-floor-row (sort-by (comp kw key) registry/purity-spec-floor-pct))) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Human approvals this run</h2>\n"
     "    <p class=\"muted\">Joined from the run's own <code>:approval-granted</code> audit facts. "
     "The last column is checked against the store, not assumed: it reports whether the committed record "
     "itself carries <code>:approved-by</code>. It does not &mdash; "
     "<code>adhesivemfg.operation</code>'s <code>:request-approval</code> node attaches the approver under "
     "the record's <code>:payload</code> key, while <code>adhesivemfg.store/commit-record!</code> "
     "destructures <code>:value</code> and never reads <code>:payload</code>. The approver is therefore "
     "shown here as an audit-ledger attribution only, rather than printing onto the record a name the "
     "SSoT does not hold.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Subject</th><th>Op</th><th>Approved by (audit)</th>"
     "<th>Approver on the stored record?</th></tr></thead>\n"
     "      <tbody>\n"
     (rows (map approval-row appr)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Safety concerns raised</h2>\n"
     "    <p class=\"muted\">A safety concern always escalates to a human regardless of confidence "
     "(<code>:coordination/safety-concern</code> is in <code>governor/high-stakes</code>) and is never "
     "gated on the referenced equipment being verified.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Concern</th><th>Equipment</th><th>Severity</th><th>Description</th></tr></thead>\n"
     "      <tbody>\n"
     (rows (map concern-row (store/safety-concerns db))) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Draft records produced</h2>\n"
     "    <p class=\"muted\">Every certificate this actor produces is unsigned "
     "(<code>status: draft-unsigned</code>, <code>issued_by_registry: false</code>) &mdash; signing is the "
     "human supervisor's act, never this actor's. No freight carrier is dispatched and no line is actuated.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Record</th><th>Kind</th><th>Subject</th><th>Equipment</th></tr></thead>\n"
     "      <tbody>\n"
     (rows (map draft-row (concat (store/maintenance-history db)
                                  (store/shipment-history db)))) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (this run)</h2>\n"
     "    <p class=\"muted\">Append-only decision-fact log, in the order this run wrote it. Every commit "
     "and every hold is here; nothing mutates the SSoT without a fact.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Fact</th><th>Op</th><th>Subject</th><th>Disposition</th><th>Basis</th></tr></thead>\n"
     "      <tbody>\n"
     (rows (map ledger-row ledger)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "</main>\n"
     "<footer>\n"
     "  <p>cloud-itonami-isic-2029 &middot; adhesive &amp; glue manufacturing plant-operations coordination "
     "actor. Generated at build time from a real actor run; contains no live plant data and no invented "
     "figures. Styled with <code>jp-go-dds</code> (デジタル庁デザインシステム).</p>\n"
     "</footer>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        {:keys [db] :as result} (run-demo!)
        ledger (vec (store/ledger db))
        holds (hold-facts ledger)
        hard (filterv hard-hold? holds)
        perm (filterv permanent-hold? holds)
        leaked (holds-that-reached-a-human result)]

    ;; Build-time invariants. These run BEFORE the file is written, so a
    ;; regression that stops the governor from refusing anything fails the
    ;; build instead of shipping a console that merely looks clean.
    (when (zero? (count holds))
      (throw (ex-info (str "render-html: refusing to write an operator console with ZERO governor "
                           "holds -- the scenario is supposed to exercise refusals, so either the "
                           "governor stopped refusing or the scenario stopped provoking it")
                      {:ledger-facts (count ledger) :holds 0})))
    (when (zero? (count hard))
      (throw (ex-info (str "render-html: refusing to write an operator console with no HARD governor "
                           "hold -- a hold a human could release is not evidence of a scope boundary")
                      {:holds (count holds) :hard 0})))
    (when (zero? (count perm))
      (throw (ex-info (str "render-html: refusing to write an operator console with no PERMANENT "
                           "hold -- :line-actuate-blocked / :certification-decision-blocked are this "
                           "actor's structural scope boundaries and must be demonstrated")
                      {:hard (count hard) :permanent 0 :permanent-rules permanent-rules})))
    (when (seq leaked)
      (throw (ex-info "render-html: a HARD governor hold reached an approval request"
                      {:threads leaked})))

    (spit out (render result))
    (println "wrote" out
             (str "(" (count ledger) " ledger facts, "
                  (count holds) " governor holds, "
                  (count hard) " HARD, "
                  (count perm) " PERMANENT, "
                  (count (approvals result)) " human approvals)"))))
