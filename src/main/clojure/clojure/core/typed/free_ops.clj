(ns ^:skip-wiki
  ^{:core.typed {:collect-only true}}
  clojure.core.typed.free-ops
  (:require [clojure.core.typed.utils :as u]
            [clojure.core.typed.type-rep :as r]
            [clojure.core.typed :as t :refer [fn>]]
            [clojure.core.typed.tvar-env :as tvar]
            [clojure.core.typed.tvar-bnds :as bnds])
  (:import (clojure.core.typed.type_rep F Bounds)
           (clojure.lang Symbol)))

(alter-meta! *ns* assoc :skip-wiki true
             :core.typed {:collect-only true})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Parse Type syntax

;(t/ann free-with-name [Symbol -> (U nil F)])
;(defn free-with-name
;  "Find the free with the actual name name, as opposed to
;  the alias used for scoping"
;  [name]
;  {:pre [(symbol? name)]
;   :post [((some-fn nil? r/F?) %)]}
;  (some (fn> [[_ {{fname :name :as f} :F}] :- '[Symbol FreeEntry]]
;          (t/ann-form fname Symbol)
;          (when (= name fname)
;            f))
;        *free-scope*))

(t/ann free-with-name-bnds [Symbol -> (U nil Bounds)])
(defn ^Bounds
  free-with-name-bnds
  "Find the bounds for the free with the actual name name, as opposed to
  the alias used for scoping"
  [name]
  {:pre [(symbol? name)]
   :post [((some-fn nil? r/Bounds?) %)]}
  (bnds/lookup-tvar-bnds name))

(t/ann free-in-scope [Symbol -> (U nil F)])
(defn free-in-scope
  "Find the free scoped as name"
  [name]
  {:pre [(symbol? name)]
   :post [((some-fn nil? r/F?) %)]}
  (tvar/*current-tvars* name))

(t/ann free-in-scope-bnds [Symbol -> (U nil Bounds)])
(defn free-in-scope-bnds
  "Find the bounds for the free scoped as name"
  ^Bounds
  [name]
  {:pre [(symbol? name)]
   :post [((some-fn nil? r/Bounds?) %)]}
  (when-let [f (free-in-scope name)]
    (bnds/lookup-tvar-bnds (:name f))))

(def frees-map? (u/hash-c? symbol? (u/hmap-c? :F r/F? :bnds r/Bounds?)))

; we used to have scopes and bounds in the same map. To avoid changing the interface,
; with-free-mappings now handles frees-map to do scoping and bounds in separate bindings.
;
; Once this works we should use a more consistent interface
;
;frees-map :- '{Symbol '{:F F :bnds Bounds}}
(defmacro with-free-mappings
  [frees-map & body]
  `(let [frees-map# ~frees-map
         _# (assert (frees-map? frees-map#)
                    frees-map#)
         scoped-names# (keys frees-map#)
         fresh-names# (map (comp :name :F) (vals frees-map#))
         bndss# (map :bnds (vals frees-map#))]
     (tvar/with-extended-new-tvars scoped-names# fresh-names#
       (bnds/with-extended-bnds fresh-names# bndss#
         ~@body))))

(def bounded-frees? (u/hash-c? r/F? r/Bounds?))

(defmacro with-bounded-frees
  "Scopes bfrees, a map of instances of F to their bounds, inside body."
  [bfrees & body]
  `(let [bfrees# ~bfrees
         _# (assert (bounded-frees? bfrees#)
                    bfrees#)]
     (with-free-mappings (into {} (for [[f# bnds#] bfrees#]
                                    [(:name f#) {:F f# :bnds bnds#}]))
       ~@body)))

(defmacro with-frees
  "Scopes frees, which are instances of F, inside body, with
  default bounds."
  [frees & body]
  `(with-free-mappings (into {} (for [f# ~frees]
                                  [(:name f#) {:F f# :bnds r/no-bounds}]))
     ~@body))

(defmacro with-free-symbols
  "Scopes sfrees, a sequence of symbols, inside body as free variables, with default bounds."
  [sfrees & body]
  `(with-free-mappings (into {} (for [f# ~sfrees]
                                  [f# {:F (r/F-maker f#) :bnds r/no-bounds}]))
     ~@body))
