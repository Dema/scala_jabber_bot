package kz.dema.scalabot

import org.jivesoftware.smack._
import org.jivesoftware.smack.filter._
import org.jivesoftware.smackx.muc._

import packet.{Presence, Message, Packet}
import proxy.ProxyInfo
import scala.actors._
import scala.actors.Actor._
import scala.collection.JavaConversions._
import scala.collection.mutable.HashMap
import java.util.{Timer, Date}
import java.util.TimerTask
import java.util.ArrayList

case class BotConfiguration (serviceName: String,
                             host: Option[String],
                             port: Option[Int],
                             username: String,
                             password: String,
                             rooms: List[String])

sealed abstract class ManagerActorMessage

case class PrivateChatMessage(packet: Packet)
case class GroupChatMessage(packet: Packet)

class MessageType extends Enumeration{
  val GroupChat = Value("groupchat")
  val Private = Value("private")
}

case class JoinMUC(name: String) extends ManagerActorMessage
case class LeaveMUC(name: String) extends ManagerActorMessage
case class SendResponse(originalMsg:Message, body: String) extends ManagerActorMessage
case class IncomingMessage(from: String, body: String) extends ManagerActorMessage
case class UnBan(who:String) extends ManagerActorMessage
case class GetBannedPersons() extends ManagerActorMessage


object App {

  implicit def object2Option[A](o:A) = if(o == null) {None} else {Some(o)}

  def main(args:Array[String]) {
    
    try {
      val jabberUser = System.getProperty("jabberUser")
      val jabberPassword = System.getProperty("jabberPassword")
      val jabberServer = System.getProperty("jabberServer")
      val jabberServerHost = System.getProperty("jabberServerHost")
      val jabberServerPort = (System.getProperty("jabberServerPort"):Option[String]).map(_.toInt)

      var configuration = BotConfiguration(jabberServer,
                                           jabberServerHost,
                                           jabberServerPort,
                                           jabberUser,
                                           jabberPassword,
                                           List("testroom1@conference.jabber.ru"))

      val proxyHost:Option[String] = System.getProperty("proxyHost")
      val proxyPort:Option[String] = System.getProperty("proxyPort")
      val cconf = if(!proxyHost.isEmpty) {
        new ConnectionConfiguration(configuration.host.getOrElse(configuration.serviceName),
                                    configuration.port.getOrElse(5222),
                                    configuration.serviceName,
                                    ProxyInfo.forHttpProxy(proxyHost.get, proxyPort.get.toInt, null, null))
      } else {
        new ConnectionConfiguration(configuration.host.getOrElse(configuration.serviceName),
                                    configuration.port.getOrElse(5222),
                                    configuration.serviceName)
      }

      val connection = new XMPPConnection(cconf);
      SASLAuthentication.supportSASLMechanism("PLAIN", 0);

      connection.connect();
      connection.login(configuration.username, configuration.password);

      JabberManagerActor.start(connection)

      configuration.rooms.foreach(JabberManagerActor ! JoinMUC(_))

      val collector: PacketCollector = connection.createPacketCollector(new PacketFilter() {
          def accept(p: Packet) = true
        })

      while (true) {
        try {
          println("c1")
          val packet = collector.nextResult
          println("c2")
          println(JabberManagerActor.z)
          JabberManagerActor ! packet
          println(JabberManagerActor.z)
          println("c3")
        } catch {
          case e => e.printStackTrace
        }
      }
    } catch {
      case e => {
          e.printStackTrace
        }
    }
  }
}

object MUCEventsListener {
}


object StatusProcessor extends Actor {
  val statuses = HashMap[String, (Date, Int)]() //JID, Дата последней смены статуса, количество смен статуса
  start
  def act = loop {
    react{
      case s=>
    }
  }
}
object JabberManagerActor extends Actor {
  val MAX_MSG_LENGTH = 350
  case class GetBannedPersons
  

  val rooms = HashMap[String, MultiUserChat]()
  var connection: XMPPConnection = null

  def start(connection: XMPPConnection) {
    this.connection = connection
    super.start
  }
  def z = this.mailboxSize
  def act = loop {
    receiveWithin(1000) {

      case GetBannedPersons => {
          for{room <- rooms.keySet} {
            val chat = rooms.get(room)
            (room, chat.map(c=>new ArrayList[Affiliate](c.getOutcasts).toList.map(_.getJid())))
          }
        }

      case JoinMUC(roomName) => if (!rooms.contains(roomName)) {
          val muc = new MultiUserChat(connection, roomName)
          //Не загружаем историю сообщений, во избежанение ложных срабатываний
          val history = new DiscussionHistory();
          history.setMaxStanzas(0);
          muc.join("ScalaBot", "", history, SmackConfiguration.getPacketReplyTimeout())

          rooms.put(roomName, muc)
        }

      case LeaveMUC(room) => rooms.get(room) match {
          case Some(muc) => 
            muc.leave
            rooms.remove(room)
          case _ => ()
        }
      case SendResponse(originalMsg, body) => originalMsg.getType match {
          case Message.Type.groupchat if body.length <= MAX_MSG_LENGTH =>
            try{
              val (roomPre, nickPre) = originalMsg.getFrom.span(_ != '/')
              val (room, nick) = (roomPre, nickPre.tail)

              rooms.get(room).foreach(_.sendMessage(nick+": "+body))
            }catch{
              case x => x.printStackTrace
            }
          case Message.Type.chat =>
        }

      case p: Packet => 
        println("packet:"+p)
        p match {
          case msg: Message =>
            
            MessageProcessor ! msg
          case p: Presence =>
            StatusProcessor ! p
          case m =>
            println("ZZZ: "+m.getClass)
        }
        println("after packet")
      case x =>
        println("Не знаю что пришло: "+x)
    }
  }

}
object MessageProcessor extends Actor {
  def z = this.mailboxSize
  def ignoreMsg:PartialFunction[String,Unit] = {
    case z => //Игнорировать сообщение
      println("Игнорируем сообщение: "+z)
  }
  val plugins:List[Plugin] = List(BibleSearchPlugin)

  start
  
  def act = loop {
    react {
      case msg:Message =>
        println("MessageProcessor:"+msg)
        try{
          plugins.map(_.processIncomingMessage(msg)).foldRight(ignoreMsg)(_ orElse _)(msg.getBody)
        }catch{
          case x => x.printStackTrace
        }
        println("MessageProcessor_after")
      case x => println("unknown: "+x)
    }
  }
}

// vim: set ts=4 sw=4 et:
