import cats.effect.Concurrent
import cats.effect.Deferred
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Ref
import cats.effect.kernel.Outcome
import cats.effect.syntax.monadCancel.monadCancelOps_
import cats.syntax.flatMap.toFlatMapOps
import cats.syntax.functor.toFunctorOps
import cats.syntax.parallel.catsSyntaxParallelTraverse1

import scala.collection.immutable.Queue
import scala.concurrent.duration.DurationInt
import scala.util.Random

abstract class Mutex:
  def acquire: IO[Unit]
  def release: IO[Unit]

object Mutex:
  type Signal = Deferred[IO, Unit]
  case class State(locked: Boolean, waiting: Queue[Signal])
  val unlocked: State = State(locked = false, Queue())

  def createSignal(): IO[Signal] = Deferred[IO, Unit]

  def create: IO[Mutex] = Ref[IO].of(unlocked).map(createMutexWithCancellation)

  def createMutexWithCancellation(state: Ref[IO, State]): Mutex =
    new Mutex:
      override def acquire: IO[Unit] = IO.uncancelable { poll =>
        createSignal().flatMap { signal =>

          val cleanup = state.modify { case State(locked, queue) =>
            val newQueue = queue.filterNot(_ eq signal)
            State(locked, newQueue) -> release
          }.flatten

          state.modify {
            case State(false, _) => State(locked = true, Queue()) -> IO.unit
            case State(true, queue) =>
              State(locked = true, queue.enqueue(signal)) -> poll(signal.get).onCancel(cleanup)
          }.flatten // modify returns IO[B], our B is IO[Unit], so modify returns IO[IO[Unit]], we need to flatten
        }
      }

      override def release: IO[Unit] = state.modify {
        case State(false, _) => unlocked -> IO.unit
        case State(true, queue) =>
          if (queue.isEmpty) then unlocked -> IO.unit
          else
            val (signal, rest) = queue.dequeue
            State(locked = true, rest) -> signal.complete(()).void
      }.flatten

  def createSimpleMutex(state: Ref[IO, State]): Mutex = new Mutex:
    /*
      Change the state of the Ref:
      - if the mutex is currently unlocked, state becomes (true, [])
      - if the mutex is locked, state becomes (true, queue + new signal) AND WAIT ON THAT SIGNAL.
     */
    override def acquire: IO[Unit] = createSignal().flatMap { signal =>
      state.modify {
        case State(false, _)    => State(locked = true, Queue())               -> IO.unit
        case State(true, queue) => State(locked = true, queue.enqueue(signal)) -> signal.get
      }.flatten // modify returns IO[B], our B is IO[Unit], so modify returns IO[IO[Unit]], we need to flatten
    }
    /*
      Change the state of the Ref:
      - if the mutex is unlocked, leave the state unchanged
      - if the mutex is locked,
        - if the queue is empty, unlock the mutex, i.e. state becomes (false, [])
        - if the queue is not empty, take a signal out of the queue and complete it (thereby unblocking a fiber waiting on it)
     */
    override def release: IO[Unit] = state.modify {
      case State(false, _) => unlocked -> IO.unit
      case State(true, queue) =>
        if (queue.isEmpty) then unlocked -> IO.unit
        else
          val (signal, rest) = queue.dequeue
          State(locked = true, rest) -> signal.complete(()).void
    }.flatten

// generic mutex after the polymorphic concurrent exercise
abstract class MutexV2[F[_]]:
  def acquire: F[Unit]
  def release: F[Unit]

object MutexV2:
  type Signal[F[_]] = Deferred[F, Unit]
  case class State[F[_]](locked: Boolean, waiting: Queue[Signal[F]])

  def unlocked[F[_]]: State[F] = State[F](locked = false, Queue())
  def createSignal[F[_]](using concurrent: Concurrent[F]): F[Signal[F]] = concurrent.deferred[Unit]

  def create[F[_]](using concurrent: Concurrent[F]): F[MutexV2[F]] =
    concurrent.ref(unlocked).map(initialState => createMutexWithCancellation(initialState))

  def createMutexWithCancellation[F[_]](state: Ref[F, State[F]])(using
      concurrent: Concurrent[F]
  ): MutexV2[F] =
    new MutexV2[F]:
      override def acquire: F[Unit] = concurrent.uncancelable { poll =>
        createSignal.flatMap { signal =>

          val cleanup = state.modify { case State(locked, queue) =>
            val newQueue = queue.filterNot(_ eq signal)
            State(locked, newQueue) -> release
          }.flatten

          state.modify {
            case State(false, _) => State[F](locked = true, Queue()) -> concurrent.unit
            case State(true, queue) =>
              State[F](locked = true, queue.enqueue(signal)) -> poll(signal.get).onCancel(cleanup)
          }.flatten
        }
      }

      override def release: F[Unit] = state.modify {
        case State(false, _) => unlocked[F] -> concurrent.unit
        case State(true, queue) =>
          if (queue.isEmpty) then unlocked[F] -> concurrent.unit
          else
            val (signal, rest) = queue.dequeue
            State[F](locked = true, rest) -> signal.complete(()).void
      }.flatten

object MutexPlayground extends IOApp.Simple:

  def criticalTask(): IO[Int] = IO.sleep(1.second) >> IO(Random.nextInt(100))

  def createNonLockingTask(id: Int): IO[Int] =
    for
      _   <- IO(s"[task $id] working...").debug()
      res <- criticalTask()
      _   <- IO(s"[task $id] got result: $res").debug()
    yield res

  def demoNonLockingTasks(): IO[List[Int]] =
    (1 to 10).toList.parTraverse(id => createNonLockingTask(id))

  def createLockingTask(id: Int, mutex: MutexV2[IO]): IO[Int] =
    for
      _ <- IO(s"[task $id] waiting for permission...").debug()
      _ <- mutex.acquire // blocks if the mutex has been acquired by some other fiber
      // critical section
      _   <- IO(s"[task $id] working...").debug()
      res <- criticalTask()
      _   <- IO(s"[task $id] got result: $res").debug()
      // critical section end
      _ <- mutex.release
      _ <- IO(s"[task $id] lock removed.").debug()
    yield res

  def demoLockingTasks(): IO[List[Int]] =
    for
      mutex   <- MutexV2.create[IO]
      results <- (1 to 10).toList.parTraverse(id => createLockingTask(id, mutex))
    yield results
  // only one task will proceed at one time

  def createCancellingTask(id: Int, mutex: MutexV2[IO]): IO[Int] =
    if (id % 2 == 0) then createLockingTask(id, mutex)
    else
      for
        fib <- createLockingTask(id, mutex)
          .onCancel(IO(s"[task $id] received cancellation!").debug().void)
          .start
        _   <- IO.sleep(2.seconds) >> fib.cancel
        out <- fib.join
        result <- out match
          case Outcome.Succeeded(effect) => effect
          case Outcome.Errored(_)        => IO(-1)
          case Outcome.Canceled()        => IO(-2)
      yield result

  def demoCancellingTasks(): IO[List[Int]] =
    for
      mutex   <- MutexV2.create[IO]
      results <- (1 to 10).toList.parTraverse(id => createCancellingTask(id, mutex))
    yield results

  override def run: IO[Unit] = demoCancellingTasks().debug().void
