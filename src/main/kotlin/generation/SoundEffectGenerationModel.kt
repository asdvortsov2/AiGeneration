package generation

enum class SoundEffectGenerationModel(
    val modelNames:Map<ApiProvider,String>,
    val availableDurationMinMs:Float,
    val availableDurationMaxMs:Float,
    val maxTextLength:Int,
) {
    Elevenlabs(
        modelNames = mapOf(
            Kie to "elevenlabs/sound-effect-v2",
            AiGate to ""
        ),
        availableDurationMinMs = 500f,
        availableDurationMaxMs = 22_000f,
        maxTextLength = 5000,
    )
}
