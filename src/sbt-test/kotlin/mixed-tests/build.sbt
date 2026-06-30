import org.jetbrains.sbt.kotlin.Keys.*
import sbt.complete.Parsers.spaceDelimited

import java.nio.file.{Files, Paths}
import scala.xml.{NodeSeq, XML}

name := "mixed-tests"

enablePlugins(KotlinPlugin)
kotlinLib("stdlib")

libraryDependencies ++= Seq(
  "com.novocode" % "junit-interface" % "0.11" % Test
)

lazy val checkTestPass = inputKey[Unit]("Check if a given test-report has one success test")
checkTestPass := {
  val args: Seq[String] = spaceDelimited("<arg>").parsed
  val testName = args.head

  val testReportPath = {
    val xmlFileName = s"TEST-$testName.xml"
    val sbt1 = Option(Paths.get("target", "test-reports", xmlFileName)).filter(Files.exists(_))
    val converter = fileConverter.value
    val sbt2 = Option(converter.toPath((Test / backendOutput).value).getParent.resolve("test-reports").resolve(xmlFileName)).filter(Files.exists(_))
    sbt1.orElse(sbt2).getOrElse(sys.error("Could not find test report xml file")).toAbsolutePath.normalize().toString
  }

  val xml = XML.load(testReportPath)
  val totalTests = getInt(xml \\ "testsuite" \ "@tests")
  val failures = getInt(xml \\ "testsuite" \ "@failures")
  val errors = getInt(xml \\ "testsuite" \ "@errors")
  val skipped = getInt(xml \\ "testsuite" \ "@skipped")

  if (totalTests == 0 || failures > 0 || errors > 0 || skipped > 0) {
    sys.error("Tests not passed")
  }
}

def getInt(path: NodeSeq): Int = path.text.toInt
