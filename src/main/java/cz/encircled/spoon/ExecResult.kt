package cz.encircled.spoon

/**
 * Allows chaining methods invocations and interrupting the whole chain on error
 */
class ExecResult(private val output: String = "", private val error: String = "") {

    // Git sends everything into error stream for some reason
    private val isError: Boolean = error.isNotEmpty() &&
            (!error.startsWith("Cloning into ") || error.contains("fatal"))

    private var isInterrupted: Boolean = false

    fun onError(callback: (String) -> Unit): ExecResult {
        if (!isInterrupted) {
            if (isError) {
                isInterrupted = true
                callback.invoke(error)
            }
        }

        return this
    }

    fun onSuccess(callback: (String) -> Unit): ExecResult {
        if (!isInterrupted && !isError) callback.invoke(output)

        return this
    }

    fun then(callback: () -> ExecResult): ExecResult =
            if (!isInterrupted && !isError) callback.invoke()
            else this

    /**
     * Exec string as an OS command
     */
    fun thenExec(command: String): ExecResult {
        val exec = Runtime.getRuntime().exec(command)
        return ExecResult(
                exec.inputStream.bufferedReader()
                        .use { it.readText() },
                exec.errorStream.bufferedReader()
                        .use { it.readText() }
        )
    }

}