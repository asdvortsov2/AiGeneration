Library to use ai.

Usage examples:

text generation:

<code>fun main() {
    AiUsageConfig.aigateToken = "your-api-key" //https://api.ai-gate.one/en/account
    AiGate.createText("2+2?",{ println("taskId=$it") },{it?.printStackTrace()}){
        println("Result:\n$it")
    }     
}
</code>



Logs:

✅ Received response, status: 200

✅ Received taskId: Text_790a7ca3-8b37-4624-9429-dec526331fbb

taskId=Text_790a7ca3-8b37-4624-9429-dec526331fbb

Result:

4


Other methods

AiGate.createImageWithImageFiles

AiGate.createVideoWithImageFiles

AiGate.createSoundEffect

AiGate.voiceGeneration



Api providers: 
api.ai-gate.one
api.kie.ai
openrouter.ai
aimlapi.com
nano-gpt.com

