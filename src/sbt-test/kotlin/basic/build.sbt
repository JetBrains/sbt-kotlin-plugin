import kotlin.Keys._

name := "basic"

enablePlugins(KotlinPlugin)
kotlinLib("stdlib")

val listClasses = taskKey[Unit]("listClasses")

listClasses := {
  val classes = (Compile / classDirectory).value.listFiles()
  streams.value.log.info("classes: " + classes)
}
