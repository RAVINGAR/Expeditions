package com.ravingarinc.expeditions.play;

class TimeScheduler(private val handler: PlayHandler, private val scheduledTimes: List<Pair<Int, Int>>, private val duration: Long) : BukkitRunnable() {
    private var lastCycle = -1L // a value of -1 means there is nothing running, anything greater is the time since when the last cycle started.

    override fun run() {
        val time = LocalDateTime.now()
        for(scheduledTime in scheduledTimes) {
            if(time.getHour() == scheduledTime.first && time.getMinute() == scheduledTime.second) {
                if(lastCycle != -1L) {
                    warn("Scheduled time of '${scheduledTime.first}${scheduledTime.second}' is overlapping with existing cycle! Please consider increasing the difference between scheduled times or decreasing the scheduled duration!")
                } else {
                    lastCycle = System.currentTimeMillis()
                    handler.lockExpeditions(false)
                }
                break
            }
        }
        if(lastCycle == -1L) return
        if(System.currentTimeMillis() - lastCycle > duration) {
            lastCycle = -1L
            handler.lockExpeditions(true)
        }
    }
}