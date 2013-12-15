(ns ^:skip-wiki 
  clojure.core.typed.rclass-ancestor-env
  (:require [clojure.core.typed :as t]
            [clojure.core.typed.subst :as subst]
            [clojure.core.typed.type-rep :as r]
            [clojure.core.typed.utils :as u]
            [clojure.core.typed.type-ctors :as c]
            [clojure.set :as set])
  (:import [clojure.core.typed.type_rep RClass]))

(alter-meta! *ns* assoc :skip-wiki true)

(t/def-alias RClassAncestorEnv 
  (t/Map t/Symbol '{:replacements (t/Map t/Symbol r/MaybeScopedType)
                    :ancestors (t/Set r/ScopedType)}))

(t/ann ^:no-check rclass-ancestor-env? (predicate RClassAncestorEnv))
(def rclass-ancestor-env? 
  (u/hash-c? symbol? (u/hmap-c? :replacements (u/hash-c? symbol? r/scoped-Type?)
                                :ancestors (u/set-c? r/scoped-Type?))))

(t/ann initial-class-ancestors-env RClassAncestorEnv)
(def initial-class-ancestors-env {})

(t/ann ^:no-check RCLASS-ANCESTORS-ENV (t/Atom1 RClassAncestorEnv))
(defonce RCLASS-ANCESTORS-ENV (atom initial-class-ancestors-env
                                    :validator 
                                    (fn [e]
                                      (if (rclass-ancestor-env? e)
                                        true
                                        (assert nil (pr-str e))))))

(defn reset-rclass-ancestors-env! []
  (reset! RCLASS-ANCESTORS-ENV initial-class-ancestors-env))

(t/ann ^:no-check rclass-ancestors [RClass -> (t/Seqable r/Type)])
(defn rclass-ancestors [{poly :poly?, rsym :the-class, :as rcls}]
  {:pre [(r/RClass? rcls)]}
  (let [names (repeatedly (count poly) #(gensym "unchecked-ancestor"))
        fs (map r/make-F names)
        abstract-as (get-in @RCLASS-ANCESTORS-ENV [rsym :ancestors])]
    (set
      (for [u abstract-as]
        (let [t (c/instantiate-many names u)
              subst (c/make-simple-substitution names poly)]
          (subst/subst-all subst t))))))

(t/ann ^:no-check rclass-replacements [RClass -> (t/Seqable t/Symbol r/Type)])
(defn rclass-replacements [{poly :poly?, rsym :the-class, :as rcls}]
  {:pre [(r/RClass? rcls)]
   :post [((u/hash-c? symbol? r/Type?) %)]}
  (let [abstract-repls (get-in @RCLASS-ANCESTORS-ENV [rsym :replacements])]
    (into {} (for [[k v] abstract-repls]
               [k (c/inst-and-subst v poly)]))))

(t/ann ^:no-check add-rclass-ancestors [RClass (t/Seqable r/Type) -> nil])
(defn add-rclass-ancestors [rsym names as]
  {:pre [(symbol? rsym)]}
  (let [nas (set
              (for [u as]
                (c/abstract-many names u)))]
    (swap! RCLASS-ANCESTORS-ENV
           (fn [env]
             (if (contains? env rsym)
               (update-in env [rsym :ancestors]
                          #(set/union (or % #{}) nas))
               (assoc env rsym {:ancestors nas
                                :replacements {}}))))
    nil))

(t/ann ^:no-check add-rclass-replacements [RClass (t/Map t/Symbol r/Type) -> nil])
(defn add-rclass-replacements [rsym names as]
  {:pre [(symbol? rsym)]}
  (let [nrp (into {}
                  (for [[k v] as]
                    [k (c/abstract-many names v)]))]
    (swap! RCLASS-ANCESTORS-ENV
           (fn [e]
             (if (contains? e rsym)
               (update-in e [rsym :replacements]
                          merge nrp)
               (assoc e rsym {:ancestors #{}
                              :replacements nrp}))))
    nil))
