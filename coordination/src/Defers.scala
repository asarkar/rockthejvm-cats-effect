package coordination

import cats.effect.Deferred
import cats.effect.FiberIO
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.OutcomeIO
import cats.effect.Ref

import scala.concurrent.duration.DurationInt

object Defers extends IOApp.Simple:

  // deferred is a primitive for waiting for an effect, while some other effect completes with a value

  val aDeferred: IO[Deferred[IO, Int]]    = Deferred[IO, Int]
  val aDeferred_v2: IO[Deferred[IO, Int]] = IO.deferred[Int] // same

  // get blocks the calling fiber (semantically) until some other fiber completes the Deferred with a value
  val reader: IO[Int] = aDeferred.flatMap { signal =>
    signal.get // blocks the fiber
  }

  val writer: IO[Boolean] = aDeferred.flatMap { signal =>
    signal.complete(42)
  }

  def demoDeferred(): IO[Unit] =
    def consumer(signal: Deferred[IO, Int]) =
      for
        _             <- IO("[consumer] waiting for result...").debug()
        meaningOfLife <- signal.get // blocker
        _             <- IO(s"[consumer] got the result: $meaningOfLife").debug()
      yield ()

    def producer(signal: Deferred[IO, Int]) =
      for
        _             <- IO("[producer] crunching numbers...").debug()
        _             <- IO.sleep(1.second)
        _             <- IO("[producer] complete: 42").debug()
        meaningOfLife <- IO(42)
        _             <- signal.complete(meaningOfLife)
      yield ()

    for
      signal      <- Deferred[IO, Int]
      fibConsumer <- consumer(signal).start
      fibProducer <- producer(signal).start
      _           <- fibProducer.join
      _           <- fibConsumer.join
    yield ()

  // simulate downloading some content
  val fileParts: List[String] = List("I ", "love S", "cala", " with Cat", "s Effect!<EOF>")

  def fileNotifierWithRef(): IO[Unit] =
    def downloadFile(contentRef: Ref[IO, String]): IO[Unit] =
      fileParts
        .map { part =>
          IO(s"[downloader] got '$part'").debug() >> IO.sleep(1.second) >> contentRef.update(
            currentContent => currentContent + part
          )
        }
        .sequence
        .void

    def notifyFileComplete(contentRef: Ref[IO, String]): IO[Unit] =
      for
        file <- contentRef.get
        _ <-
          if (file.endsWith("<EOF>")) then IO("[notifier] File download complete").debug()
          else
            IO("[notifier] downloading...").debug() >> IO.sleep(500.millis) >> notifyFileComplete(
              contentRef
            ) // busy wait!
      yield ()

    for
      contentRef    <- Ref[IO].of("")
      fibDownloader <- downloadFile(contentRef).start
      notifier      <- notifyFileComplete(contentRef).start
      _             <- fibDownloader.join
      _             <- notifier.join
    yield ()

  // deferred works miracles for waiting
  def fileNotifierWithDeferred(): IO[Unit] =
    def notifyFileComplete(signal: Deferred[IO, String]): IO[Unit] =
      for
        _ <- IO("[notifier] downloading...").debug()
        _ <- signal.get // blocks until the signal is completed
        _ <- IO("[notifier] File download complete").debug()
      yield ()

    def downloadFilePart(
        part: String,
        contentRef: Ref[IO, String],
        signal: Deferred[IO, String]
    ): IO[Unit] =
      for
        _             <- IO(s"[downloader] got '$part'").debug()
        _             <- IO.sleep(1.second)
        latestContent <- contentRef.updateAndGet(currentContent => currentContent + part)
        _ <- if (latestContent.contains("<EOF>")) then signal.complete(latestContent) else IO.unit
      yield ()

    for
      contentRef  <- Ref[IO].of("")
      signal      <- Deferred[IO, String]
      notifierFib <- notifyFileComplete(signal).start
      fileTasksFib <- fileParts
        .map(part => downloadFilePart(part, contentRef, signal))
        .sequence
        .start
      _ <- notifierFib.join
      _ <- fileTasksFib.join
    yield ()

  override def run: IO[Unit] = alarm()

  /** Exercises:
    *   - (medium) write a small alarm notification with two simultaneous IOs
    *     - one that increments a counter every second (a clock)
    *     - one that waits for the counter to become 10, then prints a message "time's up!"
    *   - (mega hard) implement racePair with Deferred.
    *     - use a Deferred which can hold an Either[outcome for ioa, outcome for iob]
    *     - start two fibers, one for each IO
    *     - on completion (with any status), each IO needs to complete that Deferred (hint: use a
    *       finalizer from the Resources lesson) (hint2: use a guarantee call to make sure the
    *       fibers complete the Deferred)
    *     - what do you do in case of cancellation (the hardest part)?
    */

  // 1
  def alarm(): IO[Unit] =
    def counter(count: Ref[IO, Int], signal: Deferred[IO, Unit]): IO[Unit] =
      for
        cnt <- count.updateAndGet(_ + 1)
        _   <- IO(s"cnt=$cnt").debug()
        _ <-
          if (cnt == 10) then signal.complete(()).void
          else IO.sleep(1.second) >> counter(count, signal)
      yield ()

    def announcer(signal: Deferred[IO, Unit]): IO[Unit] =
      for
        _ <- signal.get
        _ <- IO("time's up!").debug()
      yield ()

    for
      count        <- Ref[IO].of(0)
      signal       <- Deferred[IO, Unit]
      counterFib   <- counter(count, signal).start
      announcerFib <- announcer(signal).start
      _            <- counterFib.join
      _            <- announcerFib.join
    yield ()

  // 2
  type RaceResult[A, B] = Either[
    (OutcomeIO[A], FiberIO[B]), // (winner result, loser fiber)
    (FiberIO[A], OutcomeIO[B])  // (loser fiber, winner result)
  ]

  type EitherOutcome[A, B] = Either[OutcomeIO[A], OutcomeIO[B]]

  def ourRacePair[A, B](ioa: IO[A], iob: IO[B]): IO[RaceResult[A, B]] = IO.uncancelable { poll =>
    for
      signal <- Deferred[IO, EitherOutcome[A, B]]
      fibA   <- ioa.guaranteeCase(outcomeA => signal.complete(Left(outcomeA)).void).start
      fibB   <- iob.guaranteeCase(outcomeB => signal.complete(Right(outcomeB)).void).start
      result <- poll(signal.get).onCancel: // blocking call - should be cancelable
        for
          cancelFibA <- fibA.cancel.start
          cancelFibB <- fibB.cancel.start
          _          <- cancelFibA.join
          _          <- cancelFibB.join
        yield ()
    yield result match
      case Left(outcomeA)  => Left((outcomeA, fibB))
      case Right(outcomeB) => Right((fibA, outcomeB))
  }
