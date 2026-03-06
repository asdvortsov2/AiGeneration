
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

suspend inline fun <T> suspendCoroutineWithTimeout(
    timeoutMillis: Long,
    crossinline block: suspend (Continuation<T>) -> Unit
): T? {
    return try {
        withTimeoutOrNull(timeoutMillis) {
            suspendCancellableCoroutine { cont ->
                launch {
                    try {
                        block(cont)
                    } catch (e: Throwable) {
                        if (!cont.isCompleted) {
                            cont.resume(null)
                        }
                    }
                }
            }
        }
    } catch (e: Exception) {
        AiUsageLoggers.exceptions.log(e)
        null
    }
}
