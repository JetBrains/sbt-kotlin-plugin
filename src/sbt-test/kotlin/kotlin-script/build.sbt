import kotlin.Keys._

enablePlugins(KotlinPlugin)
kotlinLib("stdlib")

kotlinVersion := "1.3.21"

val listClasses = taskKey[Unit]("listClasses")

listClasses := {
  val classes = (Compile / classDirectory).value.listFiles()
  streams.value.log.info("classes: " + classes)
}
