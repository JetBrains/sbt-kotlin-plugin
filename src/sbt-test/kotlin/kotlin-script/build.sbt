import org.jetbrains.sbt.kotlin.Keys._

name := "kotlin-script"

enablePlugins(KotlinPlugin)
kotlinLib("stdlib")

kotlinVersion := "1.3.21"
