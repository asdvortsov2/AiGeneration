package generation

enum class SoundVoiceIsolationModel(
    val modelNames:Map<ApiProvider,String>,
    val availableDurationMaxMs:Float,
) {
    Elevenlabs(
        modelNames = mapOf(
            Kie to "elevenlabs/audio-isolation"
        ),
        availableDurationMaxMs = 22_000f,
    );
}
