package com.attune.app.util
data class Quote(val text:String, val author:String)
object Quotes {
    val all=listOf(
        Quote("Where focus goes, energy flows.","Tony Robbins"),
        Quote("Deep work is the ability to focus without distraction on a cognitively demanding task.","Cal Newport"),
        Quote("Almost everything will work again if you unplug it for a few minutes, including you.","Anne Lamott"),
        Quote("The successful warrior is the average man, with laser-like focus.","Bruce Lee"),
        Quote("It is not enough to be busy. What are we busy about?","Henry David Thoreau"),
        Quote("Do one thing at a time, and while doing it put your whole soul into it.","Swami Vivekananda"),
        Quote("You do not need more time. You need to decide.","Seth Godin"),
        Quote("Starve your distractions. Feed your focus.","Anonymous"),
        Quote("What information consumes is rather obvious: it consumes the attention of its recipients.","Herbert A. Simon"),
        Quote("The key is not to prioritize what's on your schedule, but to schedule your priorities.","Stephen Covey")
    )
    fun forHome()=all[0]; fun forFocus()=all[1]; fun forBreak()=all[2]
    fun forPrediction()=all[3]; fun forInsights()=all[4]; fun forOnboarding()=all[5]
    /** Stable per notification so the quote doesn't change while the screen recomposes. */
    fun forNotification(id:String)=all[Math.floorMod(id.hashCode(), all.size)]
}
