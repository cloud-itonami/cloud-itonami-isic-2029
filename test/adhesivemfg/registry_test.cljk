(ns adhesivemfg.registry-test
  (:require [clojure.test :refer [deftest is]]
            [adhesivemfg.registry :as r]))

;; ----------------------------- equipment-verified? / equipment-registered? / equipment-ready? -----------------------------

(deftest equipment-is-verified-when-flagged
  (is (true? (r/equipment-verified? {:id "e1" :verified? true}))))

(deftest equipment-is-not-verified-when-false-or-missing
  (is (false? (r/equipment-verified? {:id "e1" :verified? false})))
  (is (false? (r/equipment-verified? {:id "e1"}))))

(deftest equipment-is-registered-when-flagged
  (is (true? (r/equipment-registered? {:registered? true}))))

(deftest equipment-is-not-registered-when-false-or-missing
  (is (false? (r/equipment-registered? {:registered? false})))
  (is (false? (r/equipment-registered? {}))))

(deftest equipment-ready-requires-both
  (is (true? (r/equipment-ready? {:verified? true :registered? true})))
  (is (false? (r/equipment-ready? {:verified? true :registered? false})))
  (is (false? (r/equipment-ready? {:verified? false :registered? true})))
  (is (false? (r/equipment-ready? {}))))

;; ----------------------------- batch-verified? / batch-registered? / batch-ready? -----------------------------

(deftest batch-is-verified-when-flagged
  (is (true? (r/batch-verified? {:id "b1" :verified? true}))))

(deftest batch-is-not-verified-when-false-or-missing
  (is (false? (r/batch-verified? {:id "b1" :verified? false})))
  (is (false? (r/batch-verified? {:id "b1"}))))

(deftest batch-is-registered-when-flagged
  (is (true? (r/batch-registered? {:registered? true}))))

(deftest batch-is-not-registered-when-false-or-missing
  (is (false? (r/batch-registered? {:registered? false})))
  (is (false? (r/batch-registered? {}))))

(deftest batch-ready-requires-both
  (is (true? (r/batch-ready? {:verified? true :registered? true})))
  (is (false? (r/batch-ready? {:verified? true :registered? false})))
  (is (false? (r/batch-ready? {:verified? false :registered? true})))
  (is (false? (r/batch-ready? {}))))

;; ----------------------------- shipment-weight-exceeded? -----------------------------

(deftest small-shipment-within-weight-does-not-exceed
  (is (false? (r/shipment-weight-exceeded?
               {:weight-kg 5000.0 :shipped-weight-kg 1000.0} 500.0))))

(deftest shipment-that-pushes-past-weight-exceeds
  (is (true? (r/shipment-weight-exceeded?
              {:weight-kg 8000.0 :shipped-weight-kg 7500.0} 1000.0))))

(deftest shipment-exactly-at-weight-does-not-exceed
  (is (false? (r/shipment-weight-exceeded?
               {:weight-kg 8000.0 :shipped-weight-kg 7500.0} 500.0))
      "exactly at weight is not over, only strictly beyond"))

(deftest missing-weight-is-not-flagged-exceeded
  (is (false? (r/shipment-weight-exceeded? {} 100.0)))
  (is (false? (r/shipment-weight-exceeded? {:weight-kg 800.0} nil))))

;; ----------------------------- product-type-valid? -----------------------------

(deftest known-product-types-are-valid
  (doseq [g [:hot-melt-adhesive :solvent-based-adhesive :water-based-adhesive
             :pressure-sensitive-adhesive :reactive-polyurethane-adhesive
             :epoxy-adhesive :cyanoacrylate-adhesive :rubber-based-adhesive]]
    (is (r/product-type-valid? g))))

(deftest fabricated-product-type-is-invalid
  (is (not (r/product-type-valid? :unobtainium-adhesive)))
  (is (not (r/product-type-valid? nil))))

;; ----------------------------- viscosity-valid? -----------------------------

(deftest typical-viscosity-is-valid
  (is (r/viscosity-valid? 3000.0))
  (is (r/viscosity-valid? 0.0))
  (is (r/viscosity-valid? 45000.0))
  (is (r/viscosity-valid? 1000000.0)))

(deftest negative-viscosity-is-invalid
  (is (not (r/viscosity-valid? -5.0))))

(deftest excessive-viscosity-is-invalid
  (is (not (r/viscosity-valid? 2000000.0)))
  (is (not (r/viscosity-valid? 1000000.01))))

(deftest non-numeric-or-missing-viscosity-is-invalid
  (is (not (r/viscosity-valid? nil)))
  (is (not (r/viscosity-valid? "3000"))))

;; ----------------------------- purity-valid? -----------------------------

(deftest typical-purity-is-valid
  (is (r/purity-valid? 97.0))
  (is (r/purity-valid? 0.0))
  (is (r/purity-valid? 45.0))
  (is (r/purity-valid? 100.0)))

(deftest negative-purity-is-invalid
  (is (not (r/purity-valid? -5.0))))

(deftest excessive-purity-is-invalid
  (is (not (r/purity-valid? 150.0)))
  (is (not (r/purity-valid? 100.01))))

(deftest non-numeric-or-missing-purity-is-invalid
  (is (not (r/purity-valid? nil)))
  (is (not (r/purity-valid? "97.0"))))

;; ----------------------------- purity-spec-floor-for / purity-below-spec-floor? -----------------------------

(deftest every-valid-product-type-has-a-spec-floor
  (doseq [pt r/valid-product-types]
    (is (some? (r/purity-spec-floor-for pt))
        (str pt " must have a purity spec floor"))))

(deftest purity-above-floor-does-not-fall-below
  (is (false? (r/purity-below-spec-floor? :hot-melt-adhesive 97.0))))

(deftest purity-exactly-at-floor-does-not-fall-below
  (is (false? (r/purity-below-spec-floor? :hot-melt-adhesive 95.0))
      "exactly at the floor is not below, only strictly under"))

(deftest purity-under-floor-falls-below
  (is (true? (r/purity-below-spec-floor? :hot-melt-adhesive 40.0))))

(deftest missing-purity-is-never-flagged-below-floor
  (is (false? (r/purity-below-spec-floor? :hot-melt-adhesive nil))))

(deftest unknown-product-type-with-no-floor-is-never-flagged-below-here
  (is (false? (r/purity-below-spec-floor? :unobtainium-adhesive 0.0))
      "invalid-product-type-violations in the governor rejects a fabricated type on its own"))

;; ----------------------------- register-maintenance -----------------------------

(deftest maintenance-is-a-draft-not-a-real-actuation
  (let [result (r/register-maintenance "mnt-1" "reactor-001" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest maintenance-assigns-maintenance-number
  (let [result (r/register-maintenance "mnt-1" "reactor-001" 7)]
    (is (= (get result "maintenance_number") "MNT-000007"))
    (is (= (get-in result ["record" "maintenance_id"]) "mnt-1"))
    (is (= (get-in result ["record" "equipment_id"]) "reactor-001"))
    (is (= (get-in result ["record" "kind"]) "maintenance-schedule-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest maintenance-validation-rules
  (is (thrown? #?(:clj Exception :cljs js/Error) (r/register-maintenance "" "reactor-001" 0)))
  (is (thrown? #?(:clj Exception :cljs js/Error) (r/register-maintenance "mnt-1" "" 0)))
  (is (thrown? #?(:clj Exception :cljs js/Error) (r/register-maintenance "mnt-1" "reactor-001" -1))))

;; ----------------------------- register-shipment -----------------------------

(deftest shipment-is-a-draft-not-a-real-dispatch
  (let [result (r/register-shipment "ship-1" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest shipment-assigns-shipment-number
  (let [result (r/register-shipment "ship-1" 7)]
    (is (= (get result "shipment_number") "SHP-000007"))
    (is (= (get-in result ["record" "shipment_id"]) "ship-1"))
    (is (= (get-in result ["record" "kind"]) "shipment-coordination-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest shipment-validation-rules
  (is (thrown? #?(:clj Exception :cljs js/Error) (r/register-shipment "" 0)))
  (is (thrown? #?(:clj Exception :cljs js/Error) (r/register-shipment "ship-1" -1))))

(deftest history-is-append-only
  (let [c1 (r/register-maintenance "mnt-1" "reactor-001" 0)
        hist (r/append [] c1)
        c2 (r/register-maintenance "mnt-2" "reactor-001" 1)
        hist2 (r/append hist c2)]
    (is (= 2 (count hist2)))
    (is (= "MNT-000000" (get-in hist2 [0 "record_id"])))
    (is (= "MNT-000001" (get-in hist2 [1 "record_id"])))))
