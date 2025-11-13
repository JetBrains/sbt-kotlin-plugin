import org.jetbrains.sbt.kotlin.Keys._

name := "kotlin-1.2-compat"

enablePlugins(KotlinPlugin)
kotlinLib("stdlib")

kotlinVersion := "1.2.71"
