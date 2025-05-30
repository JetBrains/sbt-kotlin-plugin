import kotlin.Keys._

name := "kotlin-1.3-compat"

enablePlugins(KotlinPlugin)
kotlinLib("stdlib")

kotlinVersion := "1.3.41"

val listClasses = taskKey[Unit]("listClasses")

listClasses := {
  val classes = (Compile / classDirectory).value.listFiles()
  streams.value.log.info("classes: " + classes)
}
