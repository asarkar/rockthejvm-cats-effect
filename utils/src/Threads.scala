package utils

import cats.effect.IO

object Threads:
  extension [A](io: IO[A])
    def showThread(): IO[A] =
      for
        a <- io
        t = Thread.currentThread().getName
        _ = println(s"[$t] $a")
      yield a
