package de.paulwoitaschek.chapterreader.ogg

import de.paulwoitaschek.chapterreader.Chapter
import de.paulwoitaschek.chapterreader.misc.readAmountOfBytes
import de.paulwoitaschek.chapterreader.misc.startsWith
import de.paulwoitaschek.chapterreader.ogg.oggReading.OggPageParseException
import de.paulwoitaschek.chapterreader.ogg.oggReading.OggStream
import de.paulwoitaschek.chapterreader.ogg.oggReading.demuxOggStreams
import de.paulwoitaschek.chapterreader.ogg.oggReading.readOggPages
import de.paulwoitaschek.chapterreader.ogg.vorbisComment.OpusStreamParseException
import de.paulwoitaschek.chapterreader.ogg.vorbisComment.VorbisComment
import de.paulwoitaschek.chapterreader.ogg.vorbisComment.VorbisCommentParseException
import de.paulwoitaschek.chapterreader.ogg.vorbisComment.VorbisCommentReader
import de.paulwoitaschek.chapterreader.ogg.vorbisComment.VorbisStreamParseException
import org.slf4j.LoggerFactory
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject

internal class OggChapterReader @Inject constructor() {

  private val logger = LoggerFactory.getLogger(javaClass)

  private val OPUS_HEAD_MAGIC = "OpusHead".toByteArray()
  private val OPUS_TAGS_MAGIC = "OpusTags".toByteArray()
  private val VORBIS_HEAD_MAGIC = "${1.toChar()}vorbis".toByteArray()
  private val VORBIS_TAGS_MAGIC = "${3.toChar()}vorbis".toByteArray()

  fun read(file: File) = file.inputStream().use {
    read(it)
  }

  private fun read(inputStream: InputStream): List<Chapter> {
    try {
      val oggPages = readOggPages(BufferedInputStream(inputStream))
      val streams = demuxOggStreams(oggPages).values

      for (stream in streams) {
        if (stream.peek().startsWith(OPUS_HEAD_MAGIC))
          return readVorbisCommentFromOpusStream(stream).asChapters()
        if (stream.peek().startsWith(VORBIS_HEAD_MAGIC))
          return readVorbisCommentFromVorbisStream(stream).asChapters()
      }
    } catch (ex: IOException) {
      logger.error("Error in read", ex)
    } catch (ex: OggPageParseException) {
      logger.error("Error in read", ex)
    } catch (ex: OpusStreamParseException) {
      logger.error("Error in read", ex)
    } catch (ex: VorbisStreamParseException) {
      logger.error("Error in read", ex)
    } catch (ex: VorbisCommentParseException) {
      logger.error("Error in read", ex)
    }
    return emptyList()
  }

  private fun readVorbisCommentFromOpusStream(stream: OggStream): VorbisComment {
    stream.next()  // skip head packet
    if (!stream.hasNext())
      throw OpusStreamParseException("Opus tags packet not present")
    val tagsPacket = stream.next()
    val packetStream = ByteArrayInputStream(tagsPacket)
    val capturePattern = packetStream.readAmountOfBytes(OPUS_TAGS_MAGIC.size)
    if (!(capturePattern contentEquals OPUS_TAGS_MAGIC))
      throw OpusStreamParseException("Invalid opus tags capture pattern")
    return VorbisCommentReader.readComment(packetStream)
  }

  private fun readVorbisCommentFromVorbisStream(stream: OggStream): VorbisComment {
    stream.next()  // skip head packet
    if (!stream.hasNext())
      throw VorbisStreamParseException("Vorbis comment header packet not present")
    val tagsPacket = stream.next()
    val packetStream = ByteArrayInputStream(tagsPacket)
    val capturePattern = packetStream.readAmountOfBytes(VORBIS_TAGS_MAGIC.size)
    if (!(capturePattern contentEquals VORBIS_TAGS_MAGIC))
      throw VorbisStreamParseException("Invalid vorbis comment header capture pattern")
    return VorbisCommentReader.readComment(packetStream)
  }
}
