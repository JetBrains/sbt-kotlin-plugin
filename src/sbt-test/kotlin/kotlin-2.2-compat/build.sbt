import kotlin.Keys._

name := "kotlin-2.2-compat"

enablePlugins(KotlinPlugin)
kotlinLib("stdlib")

kotlinVersion := "2.2.0-RC"
kotlincJvmTarget := "1.8"

val listClasses = taskKey[Unit]("listClasses")

listClasses := {
  val classes = (Compile / classDirectory).value.listFiles()
  streams.value.log.info("classes: " + classes.mkString("Array(", ", ", ")"))
}
