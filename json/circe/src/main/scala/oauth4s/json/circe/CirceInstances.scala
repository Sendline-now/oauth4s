package oauth4s.json.circe

trait CirceInstances extends CodecInstance, DecoderInstance, TreeInstances

object CirceInstances extends CirceInstances
