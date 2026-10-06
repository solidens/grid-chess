package com.gridchess.online

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The wire format for online play: one JSON object per WebSocket text frame,
 * told apart by "type". The server keeps the same classes in its own
 * repository; bump [PROTOCOL] on both sides when an old client could misread
 * a new shape.
 */
const val PROTOCOL = 1

val Wire = Json {
    classDiscriminator = "type"
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

@Serializable
enum class Color {
    @SerialName("white") WHITE,
    @SerialName("black") BLACK;

    val other: Color get() = if (this == WHITE) BLACK else WHITE
}

/** The colour the host asks for when opening a room. */
@Serializable
enum class SideChoice {
    @SerialName("white") WHITE,
    @SerialName("black") BLACK,
    @SerialName("random") RANDOM,
}

@Serializable
enum class Status {
    @SerialName("waiting") WAITING,
    @SerialName("playing") PLAYING,
    @SerialName("over") OVER,
}

@Serializable
enum class Result {
    @SerialName("white") WHITE,
    @SerialName("black") BLACK,
    @SerialName("draw") DRAW,
}

@Serializable
sealed interface ClientMsg {
    /** Open a room. Only this needs a licence: the friend who joins plays free. */
    @Serializable @SerialName("create")
    data class Create(
        val game: String,
        val license: String,
        val side: SideChoice = SideChoice.RANDOM,
        val protocol: Int = PROTOCOL,
    ) : ClientMsg

    @Serializable @SerialName("join")
    data class Join(val code: String, val protocol: Int = PROTOCOL) : ClientMsg

    /** Take a seat back after a dropped connection. */
    @Serializable @SerialName("resume")
    data class Resume(val token: String, val protocol: Int = PROTOCOL) : ClientMsg

    /** [ply] is how many moves the game had before this one, so a stale or repeated send is refused. */
    @Serializable @SerialName("move")
    data class Move(val ply: Int, val move: String) : ClientMsg

    @Serializable @SerialName("resign")
    data object Resign : ClientMsg

    /** Offers a draw, or accepts the one on the table. */
    @Serializable @SerialName("draw")
    data object Draw : ClientMsg

    @Serializable @SerialName("rematch")
    data object Rematch : ClientMsg

    /** Gone for good. During a game this resigns it. */
    @Serializable @SerialName("leave")
    data object Leave : ClientMsg
}

@Serializable
sealed interface ServerMsg {
    /** Sent once a seat is taken; [token] is what [ClientMsg.Resume] needs. */
    @Serializable @SerialName("seat")
    data class Seat(val code: String, val token: String, val color: Color) : ServerMsg

    /** The whole room, every time anything in it changes. A game is short enough to resend. */
    @Serializable @SerialName("state")
    data class State(
        val code: String,
        val color: Color,
        val status: Status,
        val moves: List<String>,
        val result: Result? = null,
        val reason: String? = null,
        val opponentOnline: Boolean = false,
        val drawOffer: Color? = null,
        val rematch: List<Color> = emptyList(),
    ) : ServerMsg

    @Serializable @SerialName("error")
    data class Error(val code: ErrorCode, val message: String) : ServerMsg
}

@Serializable
enum class ErrorCode {
    /** The key is unknown, refunded or charged back. */
    @SerialName("license") LICENSE,

    /** Gumroad could not be reached and the key was never seen before. */
    @SerialName("unavailable") UNAVAILABLE,

    /** One key already has as many rooms open as it may. */
    @SerialName("busy") BUSY,
    @SerialName("no_room") NO_ROOM,
    @SerialName("room_full") ROOM_FULL,
    @SerialName("illegal") ILLEGAL,
    @SerialName("protocol") PROTOCOL,
    @SerialName("bad_request") BAD_REQUEST,
}
