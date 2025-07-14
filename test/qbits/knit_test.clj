(ns qbits.knit-test
  (:refer-clojure :exclude [future future-call])
  (:require [clojure.test :refer [deftest is]]
            [qbits.knit :as k])
  (:import [java.util.concurrent ExecutorService ScheduledExecutorService]))

(deftest test-executors
  (is (instance? ExecutorService (k/executor :single)))
  (is (instance? ExecutorService (k/executor :cached)))
  (is (instance? ExecutorService (k/executor :fixed)))
  (is (instance? ExecutorService (k/executor :thread-per-task)))
  (is (instance? ExecutorService (k/executor :virtual)))
  (is (instance? ScheduledExecutorService (k/executor :scheduled)))
  (is (instance? ScheduledExecutorService (k/executor :scheduled-single))))

;; TODO: Add `(deftest test-submit ...)` case.

(deftest test-schedule
  (let [r (atom {:with-fixed-delay 0 :at-fixed-rate 0 :once 0})]
    (k/schedule :once 1000 #(swap! r update-in [:once] inc))
    (k/schedule :with-fixed-delay 1000 #(swap! r update-in [:with-fixed-delay] inc))
    (k/schedule :at-fixed-rate 1000 #(swap! r update-in [:at-fixed-rate] inc))

    (Thread/sleep 4500)

    (is (= 1 (:once @r)))
    (is (= 5 (:with-fixed-delay @r)))
    (is (= 5 (:at-fixed-rate @r)))))

(deftest test-futures
  (let [x (k/executor :single)]
    (is (= 1 @(k/future 1 {:executor x})))
    (is (= 1 @(k/future-call (constantly 1) {:executor x})))))
