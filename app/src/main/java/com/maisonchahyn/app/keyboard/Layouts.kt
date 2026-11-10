package com.maisonchahyn.app.keyboard

import android.graphics.RectF

enum class KType { CHAR, SHIFT, DELETE, SPACE, ENTER, NUM, SYM, ABC, EMOJI, EMOJI_PREV, EMOJI_NEXT }

enum class Mode { LETTERS, NUMBERS, SYMBOLS, EMOJI }

class Key(val label: String, val type: KType = KType.CHAR, val weight: Float = 1f) {
    val rect = RectF()
    val isSpecial get() = type != KType.CHAR && type != KType.SPACE
}

object Layouts {
    private fun row(s: String, w: Float = 1f) = s.map { Key(it.toString(), KType.CHAR, w) }
    private fun row(vararg s: String, w: Float = 1f) = s.map { Key(it, KType.CHAR, w) }

    private fun bottom(switchLabel: String, switchType: KType) = listOf(
        Key(switchLabel, switchType, 1.25f),
        Key("😊", KType.EMOJI, 1.25f),
        Key("espace", KType.SPACE, 5.3f),
        Key("retour", KType.ENTER, 2.2f),
    )

    fun letters(numberRow: Boolean): List<List<Key>> {
        val rows = mutableListOf<List<Key>>()
        if (numberRow) rows += row("1234567890")
        rows += row("azertyuiop")
        rows += row("qsdfghjklm")
        rows += listOf(Key("⇧", KType.SHIFT, 1.4f)) + row("wxcvbn") + listOf(Key("'"), Key("⌫", KType.DELETE, 1.4f))
        rows += bottom("123", KType.NUM)
        return rows
    }

    fun numbers(): List<List<Key>> = listOf(
        row("1234567890"),
        row("-", "/", ":", ";", "(", ")", "€", "&", "@", "\""),
        listOf(Key("#+=", KType.SYM, 1.4f)) + row(".", ",", "?", "!", "'", w = 1.44f) + listOf(Key("⌫", KType.DELETE, 1.4f)),
        bottom("ABC", KType.ABC),
    )

    fun symbols(): List<List<Key>> = listOf(
        row("[", "]", "{", "}", "#", "%", "^", "*", "+", "="),
        row("_", "\\", "|", "~", "<", ">", "$", "£", "¥", "•"),
        listOf(Key("123", KType.NUM, 1.4f)) + row(".", ",", "?", "!", "'", w = 1.44f) + listOf(Key("⌫", KType.DELETE, 1.4f)),
        bottom("ABC", KType.ABC),
    )

    private const val EMOJI_PER_PAGE = 27

    val emojis: List<String> = (
        "😀 😃 😄 😁 😆 😅 😂 🤣 🥲 😊 😇 🙂 🙃 😉 😌 😍 🥰 😘 😗 😋 😛 😜 🤪 😎 🤩 🥳 😏 " +
        "😒 😞 😔 😟 😕 🙁 😣 😖 😫 😩 🥺 😢 😭 😤 😠 😡 🤯 😳 🥵 🥶 😱 😨 😰 😥 😓 🤗 🤔 " +
        "🫡 🤭 🤫 🤥 😶 😐 😑 😬 🙄 😯 😦 😮 😲 🥱 😴 🤤 😪 😵 🤐 🥴 🤢 🤮 🤧 😷 🤒 🤕 🤑 " +
        "❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 💯 💢 💥 💫 💦 💨 🕊️ 💬 💤 " +
        "👍 👎 👌 ✌️ 🤞 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ ✋ 🤚 🖐️ 🖖 👋 🤝 🙏 💪 👏 🙌 👐 🫶 ✍️ 💅 " +
        "🔥 ✨ ⭐ 🌟 🎉 🎊 🎁 🎂 🍾 🥂 ☕ 🍕 🍔 🍟 🌮 🍣 🍩 🍪 🍫 🍓 🍉 🍑 🥑 🥐 🍷 🍹 🧁 " +
        "⚽ 🏀 🎮 🎧 🎵 📱 💻 📷 ✈️ 🚗 🏠 🌙 ☀️ 🌈 🌸 🌹 🌺 🍀 🐶 🐱 🦋 🐻 🐼 🦁 🌍 💎 👑"
        ).split(" ").filter { it.isNotBlank() }

    val emojiPages: Int get() = (emojis.size + EMOJI_PER_PAGE - 1) / EMOJI_PER_PAGE

    fun emoji(page: Int): List<List<Key>> {
        val start = page * EMOJI_PER_PAGE
        val list = emojis.drop(start).take(EMOJI_PER_PAGE)
        val rows = list.chunked(9).map { r -> r.map { Key(it, KType.CHAR, 10f / 9f) } }.toMutableList()
        while (rows.size < 3) rows += emptyList<Key>()
        rows += listOf(
            Key("ABC", KType.ABC, 1.5f),
            Key("◀", KType.EMOJI_PREV, 1.2f),
            Key("espace", KType.SPACE, 4.4f),
            Key("▶", KType.EMOJI_NEXT, 1.2f),
            Key("⌫", KType.DELETE, 1.7f),
        )
        return rows
    }

    val accents: Map<String, List<String>> = mapOf(
        "e" to listOf("e", "é", "è", "ê", "ë", "€"),
        "a" to listOf("a", "à", "â", "æ", "ä", "á", "ã"),
        "u" to listOf("u", "ù", "û", "ü", "ú"),
        "i" to listOf("i", "î", "ï", "ì", "í"),
        "o" to listOf("o", "ô", "œ", "ö", "ò", "ó", "ø"),
        "c" to listOf("c", "ç", "ć"),
        "y" to listOf("y", "ÿ"),
        "n" to listOf("n", "ñ"),
        "s" to listOf("s", "ß", "$"),
        "'" to listOf("'", "’", "\"", "«", "»"),
        "\"" to listOf("\"", "«", "»", "“", "”"),
        "." to listOf(".", "…"),
        "-" to listOf("-", "–", "—", "•"),
        "?" to listOf("?", "¿"),
        "!" to listOf("!", "¡"),
        "€" to listOf("€", "$", "£", "¥", "₩", "¢"),
        "0" to listOf("0", "°"),
        "&" to listOf("&", "§"),
        "%" to listOf("%", "‰"),
    )
}
