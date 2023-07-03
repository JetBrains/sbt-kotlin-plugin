package demo

fun main() {
    // Test some Kotlin 1.8 features
    inspectHQVehicle(HighQuality(Vehicle(4)))
}

data class Vehicle(val wheels: Int)

// generic inline classes
@JvmInline
value class HighQuality<T>(val thing: T)

fun inspectHQVehicle(v: HighQuality<Vehicle>) {
    println(v.thing)
}
