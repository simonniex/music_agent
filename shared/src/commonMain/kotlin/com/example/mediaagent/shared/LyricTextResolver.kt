package com.example.mediaagent.shared

object LyricTextResolver {
    fun resolve(
        auxiliary: AuxiliaryContext,
        asrResult: TranscriptResult,
        vision: VisionContextBundle,
    ): StandardTextResult {
        val asrText = asrResult.text.trim()
        val userLyrics = auxiliary.favoriteLyrics.trim()
        val visionLyrics = vision.bestLyrics().trim()
        val visionSong = vision.bestSongName().ifBlank { auxiliary.songName }.trim()
        val visionArtist = vision.bestArtistName().ifBlank { auxiliary.artistName }.trim()

        if (userLyrics.isNotBlank()) {
            return StandardTextResult(
                text = userLyrics,
                source = StandardTextSource.UserInput,
                asrText = asrText,
                visionLyrics = visionLyrics,
                songName = visionSong,
                artistName = visionArtist,
            )
        }

        if (visionLyrics.isNotBlank()) {
            val source = when {
                vision.lyricImages.any { it.lyrics.isNotBlank() } -> StandardTextSource.VisionLyricImage
                vision.screenshots.any { it.lyrics.isNotBlank() } -> StandardTextSource.VisionScreenshot
                vision.lyricImages.any { it.success } -> StandardTextSource.VisionLyricImage
                else -> StandardTextSource.VisionScreenshot
            }
            return StandardTextResult(
                text = visionLyrics,
                source = source,
                asrText = asrText,
                visionLyrics = visionLyrics,
                songName = visionSong,
                artistName = visionArtist,
            )
        }

        val songHint = listOf(auxiliary.songName, auxiliary.artistName)
            .filter { it.isNotBlank() }
            .joinToString(" - ")
        if (songHint.isNotBlank() && asrText.isBlank()) {
            return StandardTextResult(
                text = songHint,
                source = StandardTextSource.SongMetadata,
                asrText = asrText,
                songName = auxiliary.songName,
                artistName = auxiliary.artistName,
            )
        }

        if (asrText.isNotBlank()) {
            return StandardTextResult(
                text = asrText,
                source = StandardTextSource.Asr,
                asrText = asrText,
                songName = visionSong,
                artistName = visionArtist,
            )
        }

        return StandardTextResult(
            text = (auxiliary.allLyricImages + auxiliary.allScreenshots)
                .firstOrNull { it.fileName.isNotBlank() }
                ?.fileName
                .orEmpty(),
            source = StandardTextSource.Fallback,
            asrText = asrText,
            songName = visionSong,
            artistName = visionArtist,
        )
    }
}

private fun VisionContextBundle.bestLyrics(): String {
    return (lyricImages + screenshots)
        .asSequence()
        .map { it.lyrics.ifBlank { it.extractedText } }
        .firstOrNull { it.isNotBlank() }
        ?: ""
}

private fun VisionContextBundle.bestSongName(): String {
    return (lyricImages + screenshots)
        .asSequence()
        .map { it.songName }
        .firstOrNull { it.isNotBlank() }
        ?: ""
}

private fun VisionContextBundle.bestArtistName(): String {
    return (lyricImages + screenshots)
        .asSequence()
        .map { it.artistName }
        .firstOrNull { it.isNotBlank() }
        ?: ""
}
