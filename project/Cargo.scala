import ch.jodersky.sbt.jni.build._
import sbt._

import java.io.File
import scala.sys.process._

private final class Cargo private (release: Boolean = true) extends BuildTool {

  def name: String = "cargo"

  def detect(baseDirectory: File): Boolean =
    baseDirectory.list().contains("Cargo.toml")

  protected def templateMappings: Seq[(String, String)] = List()

  def getInstance(baseDirectory: File, buildDirectory: File, logger: sbt.Logger): Instance =
    new Instance {

      // IntelliJ friendly logger, it doesn't start tests a line is printed as "error"
      private val log = new ProcessLogger {
        def out(s: => String): Unit = logger.info(s)
        def err(s: => String): Unit = logger.warn(s)
        def buffer[T](f: => T): T = f
      }

      def clean(): Unit =
        Process("cargo clean", baseDirectory) ! log

      def library(targetDirectory: File): File = {
        val releaseFlag = if (release) "--release " else ""
        val ev =
          Process(
            s"cargo build $releaseFlag--target-dir ${targetDirectory.getAbsolutePath}",
            baseDirectory,
          ) ! log
        if (ev != 0) sys.error(s"Building native library failed. Exit code: $ev")

        val subdir = if (release) "release" else "debug"
        val products: List[File] =
          (targetDirectory / subdir * ("*.so" | "*.dylib")).get.filter(_.isFile).toList

        // only one produced library is expected
        products match {
          case Nil =>
            sys.error(
              s"No files were created during compilation, " +
                s"something went wrong with the $name configuration.",
            )
          case head :: Nil =>
            head
          case head :: _ =>
            logger.warn(
              "More than one file was created during compilation, " +
                s"only the first one (${head.getAbsolutePath}) will be used.",
            )
            head
        }
      }

    }
}

object Cargo {
  def apply(release: Boolean = true): BuildTool = new Cargo(release)
}
