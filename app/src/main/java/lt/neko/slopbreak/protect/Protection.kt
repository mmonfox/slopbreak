package lt.neko.slopbreak.protect

data class Protection(
    val on: Boolean,
    val askedAt: Long = 0L,
    val askedBoot: Int = -1,
    val armedAt: Long = 0L,
    val armedBoot: Int = -1,
) {
    val armsAt: Long get() = armedAt + ARM_MS

    val offAt: Long get() = askedAt + UNLOCK_WAIT_MS

    fun arming(now: Long, boot: Int) = on && armedBoot == boot && now < armsAt

    fun waiting(now: Long, boot: Int) = asked(boot) && now < offAt

    fun due(now: Long, boot: Int) = asked(boot) && now >= offAt

    fun guarding(now: Long, boot: Int) = on && !arming(now, boot) && !due(now, boot)

    private fun asked(boot: Int) = on && askedAt > 0 && askedBoot == boot
}

const val ARM_MS = 60 * 60 * 1000L
const val UNLOCK_WAIT_MS = 60 * 60 * 1000L
