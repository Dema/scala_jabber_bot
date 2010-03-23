package kz.dema.scalabot
import org.jivesoftware.smack._
import org.jivesoftware.smack.filter._
import org.jivesoftware.smackx.muc._

import packet.Message

trait Plugin{
    self =>
    
    def processIncomingMessage(msg:Message):PartialFunction[String,Unit]
}

// vim: set ts=4 sw=4 et:
