package effects

import cats.effect.IO

import scala.util.Try

object IOErrorHandling:
  // 1 - construct potentially failed IOs from standard data types (Option, Try, Either)
  def option2IO[A](oa: Option[A])(ifEmpty: Throwable): IO[A] =
    either2IO(oa.toRight(ifEmpty))

  def try2IO[A](ot: Try[A]): IO[A] =
    either2IO(ot.toEither)

  def either2IO[A](oe: Either[Throwable, A]): IO[A] =
    oe match
      case Left(error)  => IO.raiseError(error)
      case Right(value) => IO(value)

  // 2 - handleError, handleErrorWith
  def handleIOError[A](io: IO[A])(handler: Throwable => A): IO[A] =
    io.redeem(handler, identity)

  def handleIOErrorWith[A](io: IO[A])(handler: Throwable => IO[A]): IO[A] =
    io.redeemWith(handler, IO.apply)
