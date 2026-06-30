import org.jetbrains.sbt.kotlin.Keys.*

name := "kotlin-2.2-compat"

enablePlugins(KotlinPlugin)
kotlinLib("stdlib")

kotlinVersion := "2.2.21"
kotlincJvmTarget := "1.8"
