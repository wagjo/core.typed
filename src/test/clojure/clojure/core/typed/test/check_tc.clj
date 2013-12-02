(ns clojure.core.typed.test.check-tc
  (:require [clojure.test :refer :all]
            [clojure.core.typed :refer [check-ns]]))

(deftest check-tc
  (is (check-ns '[clojure.core.typed.utils
                  clojure.core.typed.type-rep
                  clojure.core.typed.cs-rep
                  clojure.core.typed.name-env
                  clojure.core.typed.type-ctors])))
