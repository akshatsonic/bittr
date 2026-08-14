package com.bitter.crypto

object IdentityDerivation {

    private val adjectives = listOf(
        "amber", "bold", "calm", "dusk", "eager", "foggy", "glow", "hasty",
        "icy", "jolly", "keen", "lucky", "mellow", "nimble", "odd", "proud",
        "quiet", "rapid", "sunny", "tame", "vivid", "wild", "zesty",
    )

    private val animals = listOf(
        "badger", "crane", "dolphin", "emu", "fox", "gecko", "heron", "ibis",
        "jackal", "koala", "lemur", "mink", "newt", "otter", "panda", "quail",
        "raven", "salmon", "tapir", "urchin", "viper", "wolf", "yak",
    )

    fun deriveUsername(fingerprint: String): String {
        val hash = Sha256.hashUtf8("bitter/identity/$fingerprint")
        val a = (hash[0].toInt() and 0xFF) % adjectives.size
        val b = (hash[1].toInt() and 0xFF) % animals.size
        val n = ((hash[2].toInt() and 0xFF) shl 8) or (hash[3].toInt() and 0xFF)
        return "bitter-${adjectives[a]}-${animals[b]}-$n"
    }
}
