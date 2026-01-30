import org.jetbrains.sbt.kotlin.Keys._

name := "kotlin-2.0-compat"

enablePlugins(KotlinPlugin)
kotlinLib("stdlib")

kotlinVersion := "2.0.21"
kotlincJvmTarget := "1.8"
