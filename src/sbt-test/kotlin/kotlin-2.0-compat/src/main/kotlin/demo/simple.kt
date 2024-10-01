package demo

fun main(args: Array<String>) {
    // Test some Kotlin 1.9 features
    println(findByRgb("#FF0000"))
    println(Person("John", "Doe"))
}

enum class Color(val colorName: String, val rgb: String) {
    RED("Red", "#FF0000"),
    ORANGE("Orange", "#FF7F00"),
    YELLOW("Yellow", "#FFFF00")
}

// In 1.8.20, the entries property for enum classes was introduced as an Experimental feature.
// The entries property is a modern and performant replacement for the synthetic values() function.
// In 1.9.0, the entries property is Stable.
fun findByRgb(rgb: String): Color? = Color.entries.find { it.rgb == rgb }

@JvmInline
value class Person(private val fullName: String) {
    // Allowed since Kotlin 1.4.30:
    init {
        check(fullName.isNotBlank()) {
            "Full name shouldn't be empty"
        }
    }
    // Allowed by default since Kotlin 1.9.0:
    constructor(name: String, lastName: String) : this("$name $lastName") {
        check(lastName.isNotBlank()) {
            "Last name shouldn't be empty"
        }
    }
}
