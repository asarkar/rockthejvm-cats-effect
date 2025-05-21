My practice code from the online course [cats-effect | Rock the JVM](https://courses.rockthejvm.com/p/cats-effect).

[![](https://github.com/asarkar/rock-the-jvm-cats-effect/workflows/CI/badge.svg)](https://github.com/asarkar/rock-the-jvm-cats-effect/actions)

Official GitHub repo: https://github.com/rockthejvm/cats-effect

## Syllabus

### Introduction
- Welcome, Setup and Tips
- Scala Essentials Recap (optional)
- Contextual Abstractions and Type Classes (Scala 2 version) (optional)
- Contextual Abstractions and Type Classes (Scala 3 version) (optional)
- Cats Type Classes Recap (optional)

### Effects and IO
- [Effects](effects/src/Effects.scala)
- [Effects: Exercises](effects/src/Effects.scala)
- [IO: Introduction](effects/src/IOIntroduction.scala)
- [IO: Exercises](effects/src/IOIntroduction.scala)
- [IO: Error Handling](effects/src/IOErrorHandling.scala)
- [IO Apps](effects/src/IOApps.scala)
- [IO: Parallelism](effects/src/IOParallelism.scala)
- [IO: Traversal](effects/src/IOTraversal.scala)
- IO: Recap

### Cats Effect Concurrency
- Fibers
- Fibers: Exercises
- How Fibers Work
- The Bracket Pattern
- Resources
- IO Concurrency: Racing
- IO Concurrency: Cancellation
- IO Concurrency: Cancellation (exercises)
- IO Concurrency: Blocking
- IO Concurrency: Async

### Cats Effect Concurrent Coordination
- Ref
- Ref: Exercises
- Deferred
- Deferred: Exercises
- Ref + Deferred Exercise: A Purely Functional Mutex
- Ref + Deferred Exercise: A Purely Functional Mutex, Part 2
- Semaphore
- CountDownLatch
- CountDownLatch Exercise: Writing Our Own
- CyclicBarrier

### Polymorphic Effects
- Polymorphic Cancellation: MonadCancel
- Polymorphic Cancellation: Exercise
- Polymorphic Fibers: Spawn
- Polymorphic Coordination: Concurrent
- Polymorphic Coordination: Exercise
- Polymorphic Timeouts: Temporal
- Polymorphic Synchronous Effects: Sync
- Polymorphic Asynchronous Effects: Async

### Errata
- Mutex Locking Bug Fix

## Running tests

```
./.github/run.sh
```

To run all tests from a package:
```
./.github/run.sh <package prefix>
```

