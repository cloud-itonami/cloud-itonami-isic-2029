(ns adhesiveworks.facts
  "Per-product-class structural-adhesive/sealant bond-standard evidence
  catalog -- the G2-style spec-basis table the Bond-Strength Governor
  checks every `:bond-standard-rules/verify` proposal against.

  SCOPING NOTE (mirrors `automotive.facts`'s own per-jurisdiction
  scoping, adapted): the closest domain analog for a national type-
  approval authority in THIS vertical is not a country -- ASTM
  International, SAE International, IPC and UL Solutions are private
  international standards-development organizations, not national
  regulators, and structural-adhesive bonding has no single national
  homologation authority the way motor-vehicle type-approval does. This
  catalog is therefore keyed by `:product-class`
  (`:automotive-structural` | `:electronics-grade`), not by ISO3
  jurisdiction -- an honest scoping choice for this domain's actual
  standards landscape, not an oversight.

  HONEST CITATION-CONFIDENCE DISCLOSURE (this session's own web-verified
  research, not fabricated):

    - ASTM D1002 (Standard Test Method for Apparent Shear Strength of
      Single-Lap-Joint Adhesively Bonded Metal Specimens by Tension
      Loading, Metal-to-Metal) -- HIGH confidence, web-verified: this is
      the real, standard automotive/aerospace/industrial lap-shear
      bond-strength test this actor's own `adhesiveworks.robotics`
      simulates. Standard specimen geometry (12.7mm/0.5in overlap x
      25.4mm/1.0in width) verified via ASTM/ANSI/MTS listings.
    - SAE J1848 -- the task that scoped this build suggested this
      citation, but this session's own web search could NOT confirm
      SAE J1848 exists as a real, findable automotive adhesive-bonding
      standard. Per this fleet's honesty discipline ('DO NOT invent a
      fake-precise SAE/IPC number'), it is NOT cited here. Instead, web
      search surfaced SAE J1836 ('Overlap Shear Test for Sealant
      Adhesive Bonding of Automotive Glass Encapsulating Material to
      Body Opening') as a REAL, web-verified, MORE PRECISELY APPLICABLE
      SAE standard -- an overlap/lap-shear test standard for automotive
      adhesive/sealant bonding, directly on-topic for this vertical.
      Cited with HIGH confidence (web-verified title + SAE listing).
    - IPC-4552 -- the task suggested this as a candidate electronics-
      adhesive citation, but web search confirms IPC-4552 is actually
      'Performance Specification for Electroless Nickel/Immersion Gold
      (ENIG) Plating for Printed Boards' -- a PCB SURFACE-FINISH
      standard, unrelated to structural adhesives. NOT cited here (would
      be a fabricated-relevance citation).
    - IPC-CC-830 (Qualification and Performance of Electrical Insulating
      Compound for Printed Wiring/Board Assemblies, conformal-coating/
      insulating-compound qualification, successor to MIL-I-46058C) --
      HIGH confidence, web-verified real IPC standard. Disclosed scope
      note: it qualifies conformal-coating/insulating-compound
      materials, not a metal-to-metal structural lap-shear test per se
      -- cited here as the closest REAL IPC reference for electronics-
      grade adhesive/encapsulant material qualification (structural
      lap-shear evidence for the electronics-grade class still comes
      from this actor's own ASTM D1002 simulation, run identically for
      both product classes -- see `adhesiveworks.robotics`).
    - UL 746C (Standard for Polymeric Materials -- Use in Electrical
      Equipment Evaluations) -- HIGH confidence, web-verified real UL
      standard, directly relevant to polymeric structural-adhesive-
      bonded electronic-enclosure applications.

  Coverage is reported HONESTLY: a product class not in this table has
  NO spec-basis. Two entries seeded (the two product classes this
  actor's own scope covers); this is a starting catalog, not a survey
  of every adhesive/sealant application.")

(def catalog
  {:automotive-structural
   {:name "Automotive structural adhesive bonding"
    :owner-authority "ASTM International / SAE International"
    :legal-basis "ASTM D1002 (Standard Test Method for Apparent Shear Strength of Single-Lap-Joint Adhesively Bonded Metal Specimens by Tension Loading, Metal-to-Metal) / SAE J1836 (Overlap Shear Test for Sealant Adhesive Bonding of Automotive Glass Encapsulating Material to Body Opening, reference)"
    :national-spec "Structural bond-line lap-shear qualification for body-panel bonding and battery-pack-sealing adhesive joints"
    :provenance "https://store.astm.org/d1002-10r19.html ; https://www.sae.org/standards/content/j1836_202101/"
    :required-evidence ["ASTM D1002 lap-shear bond-strength test report"
                        "Surface-preparation/pretreatment record"
                        "Bondline-thickness inspection record"
                        "Cure-cycle (time/temperature) log"]}
   :electronics-grade
   {:name "Consumer-electronics-grade structural/encapsulant adhesive bonding"
    :owner-authority "IPC / UL Solutions"
    :legal-basis "IPC-CC-830 (Qualification and Performance of Electrical Insulating Compound for Printed Wiring/Board Assemblies) / UL 746C (Standard for Polymeric Materials -- Use in Electrical Equipment Evaluations)"
    :national-spec "Thin-bondline structural/environmental-seal adhesive qualification for smartphone frame/battery/display-assembly bonding"
    :provenance "https://webstore.ansi.org/standards/ipc/ipccc830c2019 ; https://www.ul.com/services/understanding-ul-746-series-standards"
    :required-evidence ["ASTM D1002 lap-shear bond-strength test report"
                        "IPC-CC-830 qualification test report"
                        "UL 746C polymeric-materials evaluation record"
                        "Bondline-thickness inspection record"
                        "Cure-cycle (time/temperature) log"]}})

(defn spec-basis [product-class] (get catalog product-class))

(defn coverage
  ([] (coverage (keys catalog)))
  ([product-classes]
   (let [have (filter catalog product-classes)
         missing (remove catalog product-classes)]
     {:requested (count product-classes)
      :covered (count have)
      :covered-product-classes (vec (sort have))
      :missing-product-classes (vec (sort missing))
      :note (str "cloud-itonami-isic-2029 R0: " (count catalog)
                 " product classes seeded. Extend `adhesiveworks.facts/catalog`, "
                 "never fabricate a product class's requirements.")})))

(defn required-evidence-satisfied?
  [product-class submitted]
  (when-let [{:keys [required-evidence]} (spec-basis product-class)]
    (let [need (count required-evidence)
          have (count (filter (set submitted) required-evidence))]
      (= need have))))

(defn evidence-checklist [product-class]
  (:required-evidence (spec-basis product-class) []))
