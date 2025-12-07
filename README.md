# knit 

Thin wrapper around Java Executors/Threads, including executors aware versions of `future` and `future-call`.

## Installation

[![Clojars Project](https://img.shields.io/clojars/v/com.github.marksto/knit.svg?include_prereleases)](https://clojars.org/com.github.marksto/knit)

## Changelog

### 2.2.0

* **BREAKING**: Streamline the `schedule` fn contract (move `delay` under `opts`)

### 2.1.0

* Fix the `:once` scheduling so that it returns the fn result
* **BREAKING**: Drop the deprecated `execute` fn
* Improve on docstrings and default parameter values
* Fix return types disposition + import all used types

### 1.0.0

* **BREAKING**: There are no longer a single arg versions of `knit/future` and `knit/thread`, just use `clojure.core` equivalents in these cases. Also, the multi arg version of these 2 macros now takes the option map as **last** argument instead of first.

## Usage

```Clojure
(use 'qbits.knit)
```

### Executors

Executor can be `:fixed` `:cached` `:single` `:scheduled` `:scheduled-single` `:thread-per-task` `:virtual`, matching the corresponding Java instances.

```Clojure
(def x (executor :fixed))
```

With all options:
```clojure
(def x (executor :fixed {:thread-factory a-thread-factory
                         :num-threads    3}))
```

Submit a task to executor:
```clojure
(submit x #(println "Hello World"))
```

### ThreadFactory

```clojure
(def tf (thread-factory))
```

With all options:
```clojure
(def tf (thread-factory {:fmt      "knit-group-%s"
                         :priority Thread/NORM_PRIORITY
                         :daemon   false}))
```

### ThreadGroup

Identical to the Java version:

```clojure
(thread-group "name")
(thread-group parent-group "name")
```

### ScheduledFuture

```clojure
(schedule :once #(println "hello world") :executor x)
(schedule :with-fixed-delay #(println "hello world") :delay 200)
```

Supports `:once` `:at-fixed-rate` `:with-fixed-delay`, matching the corresponding [Java methods](http://docs.oracle.com/javase/6/docs/api/java/util/concurrent/ScheduledExecutorService.html).

With all options:
```clojure
(schedule :at-fixed-rate
          #(println "hello world")
          {:executor      (executor :scheduled
                                    :num-threads 3
                                    :thread-factory a-thread-factory)
           :delay         5
           :initial-delay 1
           :unit          :minutes})
```

Time units are `:days` `:hours` `:minutes` `:seconds` `:milliseconds` `:microseconds` `:nanoseconds`.

### Future

The Clojure like `future` with configurable execution context.

```clojure
(qbits.knit/future (System/currentTimeMillis) {:executor x})
(qbits.knit/future-call #(System/currentTimeMillis) {:executor x})
```

## License

Copyright © 2015 Max Penet

Distributed under the Eclipse Public License, the same as Clojure.
