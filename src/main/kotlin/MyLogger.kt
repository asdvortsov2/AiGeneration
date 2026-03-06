import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

class GenerationLogger (folder:File, val scope:CoroutineScope){
    companion object{
        private val executor = Executors.newSingleThreadExecutor()
    }
    val file = File("$folder/log.txt")
    private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
    init {
        if(!folder.exists()){
            folder.mkdirs()
        }
        if(!file.exists()){
            log("first init")
        }
    }

    private val limit = 10_000_000//1mb
    fun log(e:Throwable){
        scope.launch {
            log("$e \n${e.stackTraceToString()}")
        }
    }

    fun log(text:String){
        scope.launch {
            val dateTime = LocalDateTime.now().format(formatter)
            executor.submit {
                file.appendText(
                    """
$dateTime: \n
$text   \n
""",Charsets.UTF_8)
                if(file.length()>limit){
                    file.zip(file.parentFile,30)
                }
            }
        }

    }
}
