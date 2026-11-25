package com.cleberleonheart.hyperglasskeyboard

import java.util.Locale

object Dictionary {
    private val portuguese = listOf(
        "a", "aí", "ainda", "aquelas", "aquele", "aquilo", "as", "até", "atrasado", "bem",
        "boa", "bom", "casa", "cidade", "como", "com", "conseguir", "coisa", "coração", "de",
        "desse", "dentro", "dia", "difícil", "dizer", "do", "da", "das", "dos", "e", "ela",
        "ele", "em", "entre", "era", "esses", "esta", "este", "eu", "fazer", "falta", "feliz",
        "filho", "final", "foi", "fora", "frente", "gente", "grande", "havia", "isso", "isto",
        "já", "lugar", "mais", "muito", "meu", "minha", "mundo", "não", "nada", "nome",
        "nos", "nós", "numa", "nunca", "para", "pelo", "pela", "pessoa", "pois", "por",
        "porque", "primeiro", "principal", "quando", "que", "quem", "saber", "se", "sem",
        "ser", "seu", "sua", "sim", "sobre", "sozinho", "talvez", "também", "tempo", "tenho",
        "tudo", "um", "uma", "umas", "uns", "vez", "você", "vos", "água", "ação", "às",
        "acontece", "agora", "ajudar", "amigo", "amor", "anos", "aprender", "beijo", "cabeça",
        "caminho", "carro", "certo", "chamar", "chegar", "conhecer", "contar", "cuidar",
        "deixar", "deus", "dever", "escolher", "esperar", "estado", "estar", "estou", "falar",
        "família", "ficar", "filha", "fluxo", "força", "futuro", "ganhar", "gosto", "guardar",
        "ideia", "igual", "irmão", "levar", "ler", "livro", "lutar", "manhã", "mãe", "melhor",
        "menos", "mesmo", "momento", "morar", "música", "necessário", "noite", "novo", "obrigado",
        "olhar", "onde", "ontem", "palavra", "pai", "passar", "pegar", "pensar", "perto",
        "poder", "ponto", "precisar", "procurar", "próximo", "querer", "quero", "resposta",
        "ritmo", "sair", "saúde", "sentir", "silêncio", "sistema", "sonho", "tarde",
        "tecnologia", "teclado", "tem", "ter", "trabalho", "trocado", "último", "verdade", "voltar"
    ).distinct()

    private val english = listOf(
        "the", "and", "you", "for", "are", "with", "this", "that", "have", "from", "not",
        "but", "they", "what", "all", "when", "your", "can", "said", "there", "use", "how",
        "one", "two", "three", "my", "we", "our", "out", "up", "down", "go", "going", "get",
        "got", "make", "made", "take", "come", "know", "think", "want", "need", "good", "bad",
        "best", "better", "hello", "world", "yes", "no", "please", "thanks", "sorry", "love",
        "time", "day", "night", "work", "play", "music", "keyboard", "phone", "computer",
        "friend", "family", "house", "car", "water", "food", "coffee", "beautiful", "happy",
        "life", "people", "thing", "something", "nothing", "everything", "here", "where",
        "why", "because", "maybe", "today", "tomorrow", "yesterday", "right", "left", "open"
    ).distinct()

    fun suggest(prefix: String, learned: Set<String>, language: String = "PT"): List<String> {
        if (prefix.isEmpty()) {
            return if (language == "EN") listOf("the", "and", "you") else listOf("de", "que", "não")
        }
        val p = prefix.lowercase(Locale.ROOT)
        val words = if (language == "EN") english else portuguese
        val matches = (words + learned).distinct().filter { it.startsWith(p) }
        val ranked = matches.sortedWith(
            compareBy<String> { if (it in learned) 0 else 1 }
                .thenBy { it.length }
                .thenBy { it }
        )
        val result = ranked.take(3)
        return if (prefix.first().isUpperCase()) result.map { it.replaceFirstChar { c -> c.uppercase(Locale.ROOT) } } else result
    }
}
