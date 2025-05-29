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
- [Fibers](concurrency/src/Fibers.scala)
- [Fibers: Exercises](concurrency/src/Fibers.scala)
- How Fibers Work
- [The Bracket Pattern](concurrency/src/Resources.scala)
- [Resources](concurrency/src/Resources.scala)
- [IO Concurrency: Racing](concurrency/src/RacingIOs.scala)
- [IO Concurrency: Cancellation](concurrency/src/CancellingIOs.scala)
- [IO Concurrency: Cancellation (exercises)](concurrency/src/CancellingIOs.scala)
- [IO Concurrency: Blocking](concurrency/src/BlockingIOs.scala)
- [IO Concurrency: Async](concurrency/src/AsyncIOs.scala)

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

