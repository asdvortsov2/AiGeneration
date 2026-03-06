package generation.text

import AiUsageConfig
import AiUsageLoggers
import generation.createText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

//val gptCache = SqliteStorage("gptCache")

abstract class GPTJob(val onError:((Throwable?)->Unit)?=null,val handler:((String)->Unit)?=null){
    abstract suspend fun makeRequest():String

    open suspend fun job(){
        try{
            AiUsageLoggers.tasks.log("start Job:${this::class.java.simpleName}")
            val request = makeRequest()
            withContext(Dispatchers.IO){
                File(AiUsageConfig.storageFolder,"all_requests").mkdirs()
                File(AiUsageConfig.storageFolder,"all_requests/request_${System.currentTimeMillis()}.json").writeText(request)
            }

            createText(request,
                onError = { error ->
                    error?.printStackTrace()
                    println("GPT request failed for Job:${this::class.java.simpleName}")
                    onError?.invoke(error)
                },
                onSuccess = { result ->
                    try{

                        val fixed = resultFix(result)
                        useResult(fixed)
                        //gptCache.save(request, fixed)
                        handler?.invoke(fixed)
                        File(AiUsageConfig.storageFolder,"all_requests/response_${System.currentTimeMillis()}.json").writeText(fixed)
                    }catch (e:Throwable){
                        e.printStackTrace()
                       // gptCache.delete(request)
                        onError?.invoke(e)
                        AiUsageLoggers.tasks.log(e)
                        AiUsageLoggers.tasks.log("Request:${this::class.java.simpleName}\n$request")
                        AiUsageLoggers.tasks.log("Throwable in Job:${this::class.java.simpleName}\n$result")
                    }
                }
            )

        }catch (e:Throwable){
            e.printStackTrace()
            AiUsageLoggers.exceptions.log(e)
            onError?.invoke(e)
        }
    }

    abstract fun resultFix(text:String):String
    abstract fun useResult(result:String)

    init {
        AI_Helper.queryGPT.add(this)
    }
}
