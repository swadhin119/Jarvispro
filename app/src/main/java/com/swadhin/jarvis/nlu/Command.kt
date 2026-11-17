package com.swadhin.jarvis.nlu

enum class AppId(val label: String, val packages: List<String>) {
    YOUTUBE("YouTube", listOf("com.google.android.youtube")),
    YT_MUSIC("YouTube Music", listOf("com.google.android.apps.youtube.music")),
    WHATSAPP("WhatsApp", listOf("com.whatsapp", "com.whatsapp.w4b")),
    INSTAGRAM("Instagram", listOf("com.instagram.android")),
    CHROME("Chrome", listOf("com.android.chrome")),
    MAPS("Google Maps", listOf("com.google.android.apps.maps")),
    CAMERA("Camera", emptyList()),
    GALLERY("Gallery", listOf("com.google.android.apps.photos")),
    SPOTIFY("Spotify", listOf("com.spotify.music")),
    PHONE("Phone", emptyList()),
    SETTINGS("Settings", emptyList()),
    GOOGLE("Google", listOf("com.google.android.googlequicksearchbox"))
}

enum class MusicApp(val app: AppId) { YOUTUBE(AppId.YOUTUBE), YT_MUSIC(AppId.YT_MUSIC), SPOTIFY(AppId.SPOTIFY) }

enum class MusicKind { SONG, SONGS, VIDEO, PLAYLIST, ANY }

enum class MediaAction { PLAY, PAUSE, RESUME, NEXT, PREVIOUS, STOP, SEEK, REPLAY }

enum class VolOp { UP, DOWN, SET, MUTE, UNMUTE, MAX }

enum class InfoType { TIME, DATE, BATTERY }

enum class Talk { GREETING, WHAT_DOING, HOW_ARE_YOU, THANKS, WHO_ARE_YOU, WAKE_ONLY }

/** A request Jarvis can execute locally (no Gemini call needed), or [Unknown] for the AI fallback. */
sealed class Command {
    data class OpenApp(val app: AppId?, val name: String) : Command()

    /** app == null means "user's default music app". */
    data class PlayMusic(
        val query: String,
        val app: MusicApp?,
        val kind: MusicKind,
        val artist: String? = null,
        val title: String? = null
    ) : Command()

    data class SearchYouTube(val query: String) : Command()
    data class AskSong(val app: MusicApp?) : Command()
    data class Media(val action: MediaAction, val seconds: Int = 0) : Command()
    object MoreBySinger : Command() { override fun toString() = "MoreBySinger" }
    object PlayFavourite : Command() { override fun toString() = "PlayFavourite" }
    object SetFavourite : Command() { override fun toString() = "SetFavourite" }
    data class Volume(val op: VolOp, val percent: Int = -1) : Command()
    data class Brightness(val op: VolOp, val percent: Int = -1) : Command()
    data class Torch(val on: Boolean) : Command()
    data class Call(val who: String) : Command()
    data class WhatsApp(val to: String?, val message: String?) : Command()
    data class Alarm(val hour: Int?, val minute: Int, val label: String = "") : Command()
    data class Timer(val seconds: Int?) : Command()
    data class Weather(val city: String?, val tomorrow: Boolean) : Command()
    data class WebSearch(val query: String, val inChrome: Boolean) : Command()
    data class Maps(val query: String, val navigate: Boolean) : Command()
    data class Info(val what: InfoType) : Command()
    data class SmallTalk(val kind: Talk) : Command()
    object Cancel : Command() { override fun toString() = "Cancel" }
    data class Unknown(val text: String) : Command()
}
