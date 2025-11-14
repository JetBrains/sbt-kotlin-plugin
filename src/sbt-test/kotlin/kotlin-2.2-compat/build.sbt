import org.jetbrains.sbt.kotlin.Keys._

name := "kotlin-2.2-compat"

enablePlugins(KotlinPlugin)
kotlinLib("stdlib")

kotlinVersion := "2.2.0-RC"
kotlincJvmTarget := "1.8"
