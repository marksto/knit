(ns qbits.knit
  (:refer-clojure :exclude [future future-call])
  (:require [qbits.commons.enum :as qc])
  (:import (clojure.lang Agent IBlockingDeref IDeref IPending)
           (java.lang ThreadBuilders$VirtualThreadFactory)
           (java.util.concurrent Executors
                                 ExecutorService
                                 Future
                                 ScheduledExecutorService
                                 ScheduledFuture
                                 ThreadFactory
                                 TimeUnit)
           (java.util.concurrent.atomic AtomicLong)))

(set! *warn-on-reflection* true)

(def time-units (qc/enum->map TimeUnit))

(defn thread-group
  "Returns a new `ThreadGroup` instance to be used in the `thread-factory`."
  (^ThreadGroup [^String name]
   (ThreadGroup. name))
  (^ThreadGroup [^ThreadGroup parent ^String name]
   (ThreadGroup. parent name)))

(defn thread-factory
  "Returns a new `ThreadFactory` instance to be used in the `executor`."
  ^ThreadFactory [& {:keys [fmt priority daemon]}]
  (let [thread-cnt (AtomicLong. 0)]
    (reify ThreadFactory
      (newThread [_ f]
        (let [thread (Thread. ^Runnable f)]
          (when (some? daemon)
            (.setDaemon thread (boolean daemon)))
          (when fmt
            (.setName thread (format fmt (.getAndIncrement thread-cnt))))
          (when priority
            (.setPriority thread (int priority)))
          thread)))))

(defn executor
  "Returns an instances of an `ExecutorService` of the corresponding type.

   Parameters:
   - `type` — the executor type, which can be `:single`, `:cached`, `:fixed`,
              `:scheduled`, `:scheduled-single`, `:thread-per-task`, `:virtual`;
   - `opts` — an options map that may include:
     - `:thread-factory` — a custom `ThreadFactory` instance to use for threads
                           creation, otherwise a default one is used;
     - `:num-threads`    — for `:fixed` or `:scheduled` executor, 1 by default."
  (^ExecutorService [type]
   (executor type nil))
  (^ExecutorService [type & {:keys [thread-factory num-threads]
                             :or   {num-threads 1}
                             :as   _opts}]
   (if (= :virtual type)
     (if (some? thread-factory)
       (do (assert (= ThreadBuilders$VirtualThreadFactory (class thread-factory)))
           (Executors/newThreadPerTaskExecutor thread-factory))
       (Executors/newVirtualThreadPerTaskExecutor))
     (let [thread-factory (or thread-factory (Executors/defaultThreadFactory))]
       (case type
         :single (Executors/newSingleThreadExecutor thread-factory)
         :cached (Executors/newCachedThreadPool thread-factory)
         :fixed (Executors/newFixedThreadPool (int num-threads) thread-factory)
         :scheduled (Executors/newScheduledThreadPool (int num-threads) thread-factory)
         :scheduled-single (Executors/newSingleThreadScheduledExecutor thread-factory)
         :thread-per-task (Executors/newThreadPerTaskExecutor thread-factory))))))

(defn submit
  "Submits the given fn `f` to the specified `executor` and returns a `Future`."
  ^Future [^ExecutorService executor f]
  (assert (fn? f) "The `f` must be a regular no-arg function")
  (.submit executor ^Callable f))

(defn schedule
  "Schedules the given fn `f` for execution and returns a `ScheduledFuture`.

   Parameters:
   - `f`     — a no-arg function to be scheduled for execution;
   - `type`  — can be `:once` (default), `:with-fixed-delay`, `:at-fixed-rate`;
   - `delay` — for `:with-fixed-delay` — the delay between the termination of
               one execution and the commencement of the next;
               for `:at-fixed-rate` — the period between successive executions;
               0 by default, i.e. no delay;
   - `opts`  — an options map that may include:
     - `:executor`      — a `ScheduledExecutorService` to schedule the task on;
     - `:initial-delay` — a time to delay 1st execution, in the specified unit;
     - `:unit`          — a keywordized name of the `TimeUnit` enum value,
                          `:milliseconds` by default."
  (^ScheduledFuture [f]
   (schedule :once 0 f nil))
  (^ScheduledFuture [type f]
   (schedule type 0 f nil))
  (^ScheduledFuture [type delay f]
   (schedule type delay f nil))
  (^ScheduledFuture [type delay f & {:keys [executor initial-delay unit]
                                     :or   {initial-delay 0
                                            unit          :milliseconds}
                                     :as   _opts}]
   (assert (fn? f) "The `f` must be a regular no-arg function")
   (let [^ScheduledExecutorService executor (or executor
                                                (qbits.knit/executor :scheduled))
         ^TimeUnit time-unit (time-units unit)]
     (case type
       :with-fixed-delay
       (.scheduleWithFixedDelay executor
                                ^Runnable f
                                ^long initial-delay
                                ^long delay
                                time-unit)
       :at-fixed-rate
       (.scheduleAtFixedRate executor
                             ^Runnable f
                             ^long initial-delay
                             ^long delay
                             time-unit)
       :once
       (.schedule executor
                  ^Callable f
                  ^long delay
                  time-unit)))))

(def ^:private binding-conveyor-fn
  (var-get #'clojure.core/binding-conveyor-fn))

(def ^:private deref-future
  (var-get #'clojure.core/deref-future))

(defn future-call
  "A variant of the `clojure.core/future-call` aux fn that supports `options`."
  [f {:keys [preserve-bindings? executor]
      :or   {preserve-bindings? true
             executor           Agent/soloExecutor}
      :as   _options}]
  (let [f (if preserve-bindings?
            (binding-conveyor-fn f)
            f)
        fut (submit executor f)]
    (reify
      IDeref
      (deref [_] (deref-future fut))
      IBlockingDeref
      (deref
        [_ timeout-ms timeout-val]
        (deref-future fut timeout-ms timeout-val))
      IPending
      (isRealized [_] (.isDone fut))
      Future
      (get [_] (.get fut))
      (get [_ timeout unit] (.get fut timeout unit))
      (isCancelled [_] (.isCancelled fut))
      (isDone [_] (.isDone fut))
      (cancel [_ interrupt?] (.cancel fut interrupt?)))))

(defmacro future
  "Takes a body of expressions with the map of `options` and yields a future
   object that will invoke the body in another (executor's) thread, and will
   cache the result and return it on all subsequent calls to `deref`/`@`.

   If the computation has not yet finished, calls to `deref`/`@` will block,
   unless the variant of deref with timeout is used. See also — `realized?`.

   A variant of the `clojure.core/future` that supports following `options`:
   - `:preserve-bindings?` — `true` by default; if `false`, won't convey the
                             current thread bindings to the executor thread;
   - `:executor`           — the `Agent/soloExecutor` by default."
  {:arglists '([body-expr+ ?options])}
  [& args]
  (assert (and (>= (count args) 2)
               (map? (last args))))
  `(future-call (^{:once true} fn* [] ~@(butlast args))
                ~(last args)))
