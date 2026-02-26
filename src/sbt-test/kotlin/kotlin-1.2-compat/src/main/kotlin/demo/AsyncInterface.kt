package demo

interface AsyncInterface {
    suspend fun asyncNum(n: Int): Int

    suspend fun asyncString(n: Int): String = asyncNum(n).toString()
}
