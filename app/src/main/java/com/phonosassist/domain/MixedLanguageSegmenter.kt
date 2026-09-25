package com.phonosassist.domain

/** One contiguous run of text tagged with the language it should be spoken in. */
data class LanguageSegment(val text: String, val isFinnish: Boolean)

/**
 * Splits mixed English/Finnish text into language segments so TTS can switch
 * voices per segment. Pure Kotlin (no Android), which keeps it unit-testable.
 *
 * Pass 1: words containing ä/ö/å are Finnish.
 * Pass 2: a small dictionary catches common Finnish words without those chars.
 */
object MixedLanguageSegmenter {

    fun segments(text: String): List<LanguageSegment> {
        if (text.isBlank()) return emptyList()
        val words = text.split(Regex("(?=\\s)|(?<=\\s)"))
        if (words.isEmpty()) return emptyList()

        val tagged = words.map { word ->
            val trimmed = word.trim()
            when {
                trimmed.isEmpty() -> LanguageSegment(word, false)
                hasFinnishChars(trimmed) -> LanguageSegment(word, true)
                isFinnishWord(trimmed.lowercase()) -> LanguageSegment(word, true)
                else -> LanguageSegment(word, false)
            }
        }

        val merged = mutableListOf<LanguageSegment>()
        var current = tagged[0]
        for (i in 1 until tagged.size) {
            val next = tagged[i]
            current = if (next.isFinnish == current.isFinnish) {
                LanguageSegment(current.text + next.text, current.isFinnish)
            } else {
                merged.add(current)
                next
            }
        }
        merged.add(current)
        return merged
    }

    private fun hasFinnishChars(word: String): Boolean =
        word.contains('ä', ignoreCase = true) ||
            word.contains('ö', ignoreCase = true) ||
            word.contains('å', ignoreCase = true)

    private fun isFinnishWord(word: String): Boolean {
        val clean = word.replace(Regex("[^a-zäöå]"), "")
        return clean.isNotEmpty() && clean in FINNISH_WORDS
    }

    // ~300 common Finnish words (pronouns, verbs, numbers, greetings, nouns).
    private val FINNISH_WORDS: Set<String> = setOf(
        // Pronouns
        "minä", "sinä", "hän", "me", "te", "he", "tämä", "tämän", "tätä",
        "tuo", "tuon", "tuota", "se", "sen", "sitä", "ne", "niiden", "niitä",
        "minun", "sinun", "hänen", "meidän", "teidän", "heidän",
        "minua", "sinua", "häntä", "meitä", "teitä", "heitä",
        "minulle", "sinulle", "hänelle", "meille", "teille", "heille",
        "minusta", "sinusta", "hänestä", "meistä", "teistä", "heistä",
        "minussa", "sinussa", "hänessä", "meissä", "teissä", "heissä",
        "minulta", "sinulta", "häneltä", "meiltä", "teiltä", "heiltä",
        "minuun", "sinuun", "häneen", "meihin", "teihin", "heihin",
        "minuna", "sinuna", "hänenä", "meinä", "teinä", "heinä",
        "minuksi", "sinuksi", "häneksi", "meiksi", "teiksi", "heiksi",

        // Common verbs (infinitives and common conjugated forms)
        "olla", "olen", "olet", "on", "olemme", "olette", "ovat",
        "olisin", "olisit", "olisi", "olisimme", "olisitte", "olisivat",
        "olin", "olit", "oli", "olimme", "olitte", "olivat",
        "ole", "olkaa", "olkoon", "olkoot",
        "ei", "en", "et", "emme", "ette", "eivät",
        "tulla", "tulen", "tulet", "tulee", "tulemme", "tulette", "tulevat",
        "mennä", "menen", "menet", "menee", "menemme", "menette", "menevät",
        "nähdä", "näen", "näet", "näkee", "näemme", "näette", "näkevät",
        "tehdä", "teen", "teet", "tekee", "teemme", "teette", "tekevät",
        "antaa", "annan", "annat", "annamme", "annatte", "antavat",
        "pitää", "pidän", "pidät", "pidämme", "pidätte", "pitävät",
        "haluta", "haluan", "haluat", "haluaa", "haluamme", "haluatte", "haluavat",
        "voida", "voin", "voit", "voi", "voimme", "voitte", "voivat",
        "pystyä", "pystyn", "pystyt", "pystyy", "pystymme", "pystytte", "pystyvät",
        "syödä", "syön", "syöt", "syö", "syömme", "syötte", "syövät",
        "juoda", "juon", "juot", "juo", "juomme", "juotte", "juovat",
        "käydä", "käyn", "käyt", "käy", "käymme", "käytte", "käyvät",
        "puhua", "puhun", "puhut", "puhuu", "puhumme", "puhutte", "puhuvat",
        "kirjoittaa", "kirjoitan", "kirjoitat", "kirjoitamme", "kirjoitatte", "kirjoittavat",
        "lukea", "luen", "luet", "lukee", "luemme", "luette", "lukevat",
        "oppia", "opin", "opit", "oppii", "opimme", "opitte", "oppivat",
        "ymmärtää", "ymmärrän", "ymmärrät", "ymmärrämme", "ymmärrätte", "ymmärtävät",
        "auttaa", "autan", "autat", "autamme", "autatte", "auttavat",
        "alkaa", "alan", "alat", "alamme", "alatte", "alkavat",
        "muistaa", "muistan", "muistat", "muistamme", "muistatte", "muistavat",
        "tietää", "tiedän", "tiedät", "tiedämme", "tiedätte", "tietävät",
        "uskoa", "uskon", "uskot", "uskoo", "uskomme", "uskotte", "uskovat",
        "toivoa", "toivon", "toivot", "toivoo", "toivomme", "toivotte", "toivoavat",
        "rakastaa", "rakastan", "rakastat", "rakastamme", "rakastatte", "rakastavat",
        "työskennellä", "työskentelen", "työskentelet", "työskentelee", "työskentelemme", "työskentelette", "työskentelevät",
        "elää", "elän", "elät", "elämme", "elätte", "elävät",
        "kuolla", "kuolen", "kuolet", "kuolee", "kuolemme", "kuolette", "kuolevat",
        "kasvaa", "kasvan", "kasvat", "kasvamme", "kasvatte", "kasvavat",

        // Numbers
        "yksi", "yhdessä", "yhteen", "yhtä",
        "kaksi", "kahdessa", "kahteen", "kahta",
        "kolme", "kolmessa", "kolmeen", "kolmea",
        "neljä", "neljässä", "neljään", "neljää",
        "viisi", "viidessä", "viiteen", "viittä",
        "kuusi", "kuudessa", "kuuteen", "kuutta",
        "seitsemän", "kahdeksan", "yhdeksän", "kymmenen",
        "kaksikymmentä", "kolmekymmentä", "neljäkymmentä",
        "viisikymmentä", "kuusikymmentä", "seitsemänkymmentä",
        "kahdeksankymmentä", "yhdeksänkymmentä", "sata",
        "tuhat", "tuhatta", "miljoona", "miljoonaa",

        // Greetings and common phrases
        "moi", "moikka", "terve", "hei", "hallå",
        "kiitos", "kiitän", "kiität", "kiittää",
        "anteeksi", "anteeksiantaa",
        "hyvästi", "näkemiin", "nähdään",
        "päivää", "huomenta", "iltapäivää", "yötä",

        // Common nouns
        "talo", "talon", "taloa", "talossa", "talosta", "taloon",
        "kissa", "kissan", "kissaa", "kissassa", "kissasta",
        "koira", "koiran", "koiraa", "koirassa", "koirasta",
        "ihminen", "ihmisen", "ihmistä", "ihmisessä", "ihmisestä",
        "nainen", "naisen", "naista", "naisessa", "naisesta",
        "mies", "miehen", "miestä", "miehessä", "miehestä",
        "lapsi", "lapsen", "lasta", "lapsessa", "lapsesta",
        "työ", "työn", "työtä", "työssä", "työstä", "työhön",
        "aika", "ajan", "aikaa", "ajassa", "ajasta", "aikaan",
        "päivä", "päivän", "päivässä", "päivästä", "päivään",
        "vuosi", "vuoden", "vuotta", "vuodessa", "vuodesta", "vuoteen",
        "tie", "tien", "tietä", "tiellä", "tieltä", "tielle",
        "käsi", "käden", "kättä", "kädessä", "kädestä",
        "silmä", "silmän", "silmää", "silmässä", "silmästä",
        "vesi", "veden", "vettä", "vedessä", "vedestä",
        "ruoka", "ruoan", "ruokaa", "ruoassa", "ruoasta",
    )
}
