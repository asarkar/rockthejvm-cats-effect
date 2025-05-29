package concurrency

import cats.effect.IO
import cats.effect.IOApp
import cats.effect.kernel.Outcome
import cats.effect.kernel.Resource

import java.io.InputStreamReader
import java.util.Scanner
import scala.concurrent.duration.DurationInt

object Resources extends IOApp.Simple:
  // use-case: manage a connection lifecycle
  class Connection(url: String):
    def open(): IO[String] = IO(s"opening connection to $url").debug()

    def close(): IO[String] = IO(s"closing connection to $url").debug()

  val asyncFetchUrl: IO[Unit] = for {
    fib <- (new Connection("rockthejvm.com").open() *> IO.sleep((Int.MaxValue).seconds)).start
    _   <- IO.sleep(1.second) *> fib.cancel
  } yield ()
  // problem: leaking resources

  val correctAsyncFetchUrl: IO[Unit] = for {
    conn <- IO(new Connection("rockthejvm.com"))
    fib  <- (conn.open() *> IO.sleep((Int.MaxValue).seconds)).onCancel(conn.close().void).start
    _    <- IO.sleep(1.second) *> fib.cancel
  } yield ()

  /*
    bracket pattern: someIO.bracket(useResourceCb)(releaseResourceCb)
    bracket is equivalent to try-catches (pure FP)
   */
  val bracketFetchUrl: IO[Unit] = IO(new Connection("rockthejvm.com"))
    .bracket(conn => conn.open() *> IO.sleep(Int.MaxValue.seconds))(conn => conn.close().void)

  val bracketProgram: IO[Unit] = for {
    fib <- bracketFetchUrl.start
    _   <- IO.sleep(1.second) *> fib.cancel
  } yield ()

  override def run: IO[Unit] = bracketReadFile("/connection.txt")

  /*
  Exercise: read the file with the bracket pattern
  - open a Scanner
  - read the file line by line, every 100 millis
  - close the scanner
  - if cancelled/throws error, close the scanner
   */
  def openFile(resource: String): IO[Scanner] = IO(
    new Scanner(new InputStreamReader(getClass.getResourceAsStream(resource)))
  )

  def readLines(scanner: Scanner): IO[Unit] =
    if scanner.hasNextLine then
      IO(scanner.nextLine()).debug()() >> IO.sleep(100.millis) >> readLines(scanner)
    else IO.unit

  def bracketReadFile(resource: String): IO[Unit] =
    openFile(resource).bracket(readLines)(sc => IO(sc.close()))

  /** Resources
    */
  def connFromConfig(path: String): IO[Unit] =
    openFile(path)
      .bracket { scanner =>
        // acquire a connection based on the file
        IO(new Connection(scanner.nextLine())).bracket { conn =>
          conn.open() >> IO.never
        }(conn => conn.close().void)
      }(scanner => IO("closing file").debug() >> IO(scanner.close()))
  // nesting resources are tedious

  val connectionResource: Resource[IO, Connection] =
    Resource.make(IO(new Connection("rockthejvm.com")))(conn => conn.close().void)
  // ... at a later part of your code

  val resourceFetchUrl: IO[Unit] = for {
    fib <- connectionResource.use(conn => conn.open() >> IO.never).start
    _   <- IO.sleep(1.second) >> fib.cancel
  } yield ()

  // resources are equivalent to brackets
  val simpleResource: IO[String]          = IO("some resource")
  val usingResource: String => IO[String] = string => IO(s"using the string: $string").debug()
  val releaseResource: String => IO[Unit] = string =>
    IO(s"finalizing the string: $string").debug().void

  val usingResourceWithBracket: IO[String] = simpleResource.bracket(usingResource)(releaseResource)
  val usingResourceWithResource: IO[String] =
    Resource.make(simpleResource)(releaseResource).use(usingResource)

  /** Exercise: read a text file with one line every 100 millis, using Resource (refactor the
    * bracket exercise to use Resource)
    */
  def getResourceFromFile(path: String): Resource[IO, Scanner] = Resource.make(openFile(path)):
    scanner => IO(s"closing file at $path").debug() >> IO(scanner.close())

  def resourceReadFile(path: String): IO[Unit] =
    IO(s"opening file at $path") >>
      getResourceFromFile(path).use { scanner =>
        readLines(scanner)
      }

  def cancelReadFile(path: String): IO[Unit] = for {
    fib <- resourceReadFile(path).start
    _   <- IO.sleep(2.seconds) >> fib.cancel
  } yield ()

  // nested resources
  def connFromConfResource(path: String): Resource[IO, Connection] =
    Resource
      .make(IO("opening file").debug() >> openFile(path))(scanner =>
        IO("closing file").debug() >> IO(scanner.close())
      )
      .flatMap(scanner =>
        Resource.make(IO(new Connection(scanner.nextLine())))(conn => conn.close().void)
      )

  // equivalent
  def connFromConfResourceClean(path: String): Resource[IO, Connection] = for {
    scanner <- Resource.make(IO("opening file").debug() >> openFile(path))(scanner =>
      IO("closing file").debug() >> IO(scanner.close())
    )
    conn <- Resource.make(IO(new Connection(scanner.nextLine())))(conn => conn.close().void)
  } yield conn

  val openConnection: IO[Nothing] = connFromConfResourceClean(
    "cats-effect/src/main/resources/connection.txt"
  ).use(conn => conn.open() >> IO.never)
  val canceledConnection: IO[Unit] = for {
    fib <- openConnection.start
    _   <- IO.sleep(1.second) >> IO("cancelling!").debug() >> fib.cancel
  } yield ()

  // connection + file will close automatically

  // finalizers to regular IOs
  val ioWithFinalizer: IO[String] =
    IO("some resource").debug().guarantee(IO("freeing resource").debug().void)
  val ioWithFinalizer_v2: IO[String] = IO("some resource")
    .debug()
    .guaranteeCase:
      case Outcome.Succeeded(fa) =>
        fa.flatMap(result => IO(s"releasing resource: $result").debug()).void
      case Outcome.Errored(e) => IO("nothing to release").debug().void
      case Outcome.Canceled() => IO("resource got canceled, releasing what's left").debug().void
