package generation.text


import AiUsageLoggers
import AiUsageScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Semaphore

object AI_Helper {
    val queryGPT = ConcurrentLinkedQueue<GPTJob>()
    val gptSemaphore = Semaphore(2)
    init {
        AiUsageScope.lowPriorityScope.launch {
            while (true) {
                delay(3000)
                queryGPT.poll()?.let { gptJob->
                    gptSemaphore.acquire()
                    AiUsageScope.lowPriorityScope.launch {

                        try{
                            withTimeout(600_000){
                                gptJob.job()
                            }

                        }catch (e:Throwable){
                            AiUsageLoggers.exceptions.log(e)
                        }finally {
                            gptSemaphore.release()
                        }
                    }
                }


            }
        }
    }
}
