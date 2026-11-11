package oauth4s.internal

import java.util.Base64
import java.nio.charset.StandardCharsets

private[oauth4s] object Base64Url:
  private val encoder = Base64.getUrlEncoder.withoutPadding
  private val decoder = Base64.getUrlDecoder

  def encode(bytes: Array[Byte]): String = encoder.encodeToString(bytes)

  def encode(text: String): String = encode(text.getBytes(StandardCharsets.UTF_8))

  def decode(value: String): Option[Array[Byte]] =
    try Some(decoder.decode(value))
    catch case _: IllegalArgumentException => None

  def decodeText(value: String): Option[String] = decode(value).map(String(_, StandardCharsets.UTF_8))
