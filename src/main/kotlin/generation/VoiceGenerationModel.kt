package generation

import generation.AiGate
import generation.ApiProvider
import generation.Kie
import generation.NanoGpt

enum class VoiceGenerationModel(
    val modelNames:Map<ApiProvider,String>,
    val maxTextLength:Int,
) {
   /* ElevenLabsTurbo25(
        modelNames = mapOf(
            Kie to "elevenlabs/text-to-speech-turbo-2-5"
            //Kie to "elevenlabs/text-to-speech-multilingual-v2",

        ),
        maxTextLength = 5000,
    ),*/
    ElevenLabsV3(
        modelNames = mapOf(
            NanoGpt to "Elevenlabs-V3",
           // Kie to "elevenlabs/text-to-dialogue-v3",
            AiGate to ""
        ),
        maxTextLength = 5000,
    ),
   /* FishSpeech2(
        modelNames = mapOf(),
        maxTextLength = 5000,
    ),*/
   /* Gemini2_5Pro(
        modelNames = mapOf(
            NanoGpt to "gemini-2.5-pro-preview-tts",
        ),
        maxTextLength = 4096
    )*/
}
