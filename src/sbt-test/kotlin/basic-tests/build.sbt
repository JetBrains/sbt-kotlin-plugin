import org.jetbrains.sbt.kotlin.Keys._

import sbt.complete.Parsers.spaceDelimited

import java.nio.file.{Files, Paths}
import scala.xml.{NodeSeq, XML}

name := "basic-tests"

enablePlugins(KotlinPlugin)
kotlinLib("stdlib")

libraryDependencies ++= Seq(
  "com.novocode" % "junit-interface" % "0.11" % Test
)

lazy val checkTestPass = inputKey[Unit]("Check if a given test-report has one success test")
checkTestPass := {
  val args: Seq[String] = spaceDelimited("<arg>").parsed
  val testName = args.head

  val sbt1Path =
    Option(s"target/test-reports/TEST-$testName.xml")
      .filter(p => Files.exists(Paths.get(p)))

  val converter = fileConverter.value
  val sbt2Path =
    Option(converter.toPath((Test / backendOutput).value).getParent.resolve("test-reports").resolve(s"TEST-$testName.xml"))
      .map(_.toString)
      .filter(p => Files.exists(Paths.get(p)))

  val reportPath = sbt1Path.orElse(sbt2Path).get

  val xml = XML.load(reportPath)
  val totalTests = getInt(xml \\ "testsuite" \ "@tests")
  val failures = getInt(xml \\ "testsuite" \ "@failures")
  val errors = getInt(xml \\ "testsuite" \ "@errors")
  val skipped = getInt(xml \\ "testsuite" \ "@skipped")

  if (totalTests == 0 || failures > 0 || errors > 0 || skipped > 0) {
    sys.error("Tests not passed")
  }
}

def getInt(path: NodeSeq): Int = path.text.toInt
