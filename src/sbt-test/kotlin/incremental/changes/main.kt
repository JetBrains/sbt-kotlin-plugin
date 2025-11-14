package demo

fun main() {
    val person = Person(Foo().toString(), 26)
    val (name, age) = person
    println(name)
    println(age)
}
