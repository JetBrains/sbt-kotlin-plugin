import kotlin.Keys._

name := "kotlin-1.8-compat"

enablePlugins(KotlinPlugin)
kotlinLib("stdlib")

kotlinVersion := "1.8.22"
kotlincJvmTarget := "1.8"

val listClasses = taskKey[Unit]("listClasses")

listClasses := {
  val classes = (Compile / classDirectory).value.listFiles()
  streams.value.log.info("classes: " + classes.mkString("Array(", ", ", ")"))
}
