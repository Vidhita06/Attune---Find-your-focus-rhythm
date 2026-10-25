package com.attune.app.util
import android.content.Context

/** Package -> category is a best-effort lookup; unknown apps fall back to "unknown" and the user's settings decide. */
object AppCategoryMapper {
    private val map=mapOf(
        "com.instagram.android" to "social","com.facebook.katana" to "social",
        "com.twitter.android" to "social","com.snapchat.android" to "social",
        "com.reddit.frontpage" to "social","com.linkedin.android" to "social","com.pinterest" to "social",
        "com.google.android.youtube" to "short_video","com.zhiliaoapp.musically" to "short_video",
        "com.whatsapp" to "messaging","com.whatsapp.w4b" to "messaging",
        "com.facebook.orca" to "messaging","org.telegram.messenger" to "messaging",
        "com.discord" to "messaging","com.google.android.apps.messaging" to "messaging","org.thoughtcrime.securesms" to "messaging",
        "com.google.android.gm" to "email","com.microsoft.office.outlook" to "email",
        "com.amazon.mShop.android.shopping" to "shopping","com.flipkart.android" to "shopping","com.myntra.android" to "shopping",
        "com.netflix.mediaclient" to "entertainment","com.spotify.music" to "entertainment",
        "com.supercell.clashofclans" to "gaming","com.google.android.apps.fitness" to "health",
        "com.slack" to "productivity","com.microsoft.teams" to "productivity","com.google.android.calendar" to "productivity",
        "com.google.android.apps.magazines" to "news","com.inshorts" to "news"
    )
    fun getCategory(pkg:String)=map[pkg] ?: "unknown"

    /** Ordered list shown in settings. */
    val ALL = listOf("messaging","social","short_video","entertainment","gaming","shopping","promotion","news","email","productivity","finance","health","unknown")
    val DEFAULT_QUEUED = setOf("messaging","social","short_video","entertainment","gaming","shopping","promotion","news","email","unknown")

    fun displayName(cat:String)=when(cat){
        "messaging"->"Messaging";"social"->"Social media";"short_video"->"Short video";"entertainment"->"Entertainment"
        "gaming"->"Games";"shopping"->"Shopping";"promotion"->"Promotions";"news"->"News";"email"->"Email"
        "productivity"->"Work tools";"finance"->"Finance";"health"->"Health";else->"Other apps"
    }
}

object AppLabels {
    private val known = mapOf(
        "com.instagram.android" to "Instagram","com.whatsapp" to "WhatsApp","com.google.android.youtube" to "YouTube",
        "com.google.android.gm" to "Gmail","com.amazon.mShop.android.shopping" to "Amazon","com.twitter.android" to "X",
        "com.discord" to "Discord","com.snapchat.android" to "Snapchat","com.linkedin.android" to "LinkedIn",
        "com.inshorts" to "Inshorts","com.flipkart.android" to "Flipkart","com.slack" to "Slack","com.spotify.music" to "Spotify"
    )
    fun resolve(ctx:Context, pkg:String):String = runCatching {
        val pm=ctx.packageManager; pm.getApplicationLabel(pm.getApplicationInfo(pkg,0)).toString()
    }.getOrNull() ?: known[pkg] ?: pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
}
