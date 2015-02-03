(ns jepsen.system.qpid-test
  (:use jepsen.system.qpid
        jepsen.core
        jepsen.tests
        clojure.test
        clojure.pprint)
  (:require [clojure.string   :as str]
            [jepsen.util      :as util]
            [jepsen.os.debian :as debian]
            [jepsen.checker   :as checker]
            [jepsen.checker.timeline :as timeline]
            [jepsen.model     :as model]
            [jepsen.generator :as gen]
            [jepsen.nemesis   :as nemesis]
            [jepsen.store     :as store]
            [jepsen.report    :as report]))

(deftest qpid-test
  (let [test (run!
               (assoc
                 noop-test
                 :name       "qpid-simple-partition"
                 :os         debian/os
                 :db         db
                 :client     (queue-client)
                 :nemesis    (nemesis/partition-random-halves)
                 :model      (model/unordered-queue)
                 :checker    (checker/compose
                               {:queue       checker/queue
                                :total-queue checker/total-queue})
                 :generator  (gen/phases
                               (->> (gen/queue)
                                    (gen/delay 1/10)
                                    (gen/nemesis
                                      (gen/seq
                                        (cycle [(gen/sleep 60)
                                                {:type :info :f :start}
                                                (gen/sleep 60)
                                                {:type :info :f :stop}])))
                                    (gen/time-limit 360))
                               (gen/nemesis
                                 (gen/once {:type :info, :f :stop}))
                               (gen/log "waiting for recovery")
                               (gen/sleep 60)
                               (gen/clients
                                 (gen/each
                                   (gen/once {:type :invoke
                                              :f    :drain}))))))]
    (is (:valid? (:results test)))
    (pprint (:results test))))
