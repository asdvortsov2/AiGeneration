package generation

enum class VoiceGenerationModel(
    val modelNames:Map<ApiProvider,String>,
    val maxTextLength:Int,
) {

    ElevenLabsV3(
        modelNames = mapOf(
            NanoGpt to "Elevenlabs-V3",
           // Kie to "elevenlabs/text-to-dialogue-v3",
            AiGate to ""
        ),
        maxTextLength = 5000,
    ),

}
