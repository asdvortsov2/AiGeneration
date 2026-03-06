
import kotlinx.coroutines.*
import java.util.concurrent.*
import kotlin.math.max


object AiUsageScope {
    var exceptionHandler:((Throwable)->Unit)? = null
    private val handler = CoroutineExceptionHandler { _, exception ->
        exceptionHandler?.invoke(exception)
    }

    val cpuCount = Runtime.getRuntime().availableProcessors()
    private val nThreads = max(cpuCount,8)

    var nPthreads = 0
    private val normalPriorityDispatcher = Executors.newCachedThreadPool{ r ->
        val t = Thread(r)
        t.priority = Thread.NORM_PRIORITY
        t.name = "normalPriorityScope thread $nPthreads"
        nPthreads++
        t
    }.asCoroutineDispatcher()
    var lPthreads = 0
    private val lowPriorityDispatcher = Executors.newCachedThreadPool { r ->
        val t = Thread(r)
        t.priority = Thread.MIN_PRIORITY
        t.name = "lowPriorityScope thread $lPthreads"
        lPthreads++
        t
    }.asCoroutineDispatcher()

    val lowPriorityScope = CoroutineScope(lowPriorityDispatcher + SupervisorJob() + handler)
    val scope = CoroutineScope( normalPriorityDispatcher + SupervisorJob() + handler)
    val singleThreadScope =  CoroutineScope(
        Executors.newSingleThreadExecutor().asCoroutineDispatcher()
                + SupervisorJob() + CoroutineExceptionHandler { _, exception ->
            exception.printStackTrace()
        }
    )
    init {
        println("cpuCount=$cpuCount, nThreads=$nThreads")
        singleThreadScope.launch {
            Thread.currentThread().name = "singleThreadScope"
        }
    }
}
