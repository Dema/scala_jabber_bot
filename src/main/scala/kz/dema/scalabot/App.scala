package kz.dema.scalabot

import org.jivesoftware.smack._
import org.jivesoftware.smack.filter._
import org.jivesoftware.smackx.muc._

import packet.{Presence, Message, Packet}
import proxy.ProxyInfo
import scala.collection.JavaConversions._
import scala.collection.mutable.HashMap
import java.util.{Timer, Date}
import java.util.TimerTask
import java.util.ArrayList
import se.scalablesolutions.akka.actor._
import se.scalablesolutions.akka.config.ScalaConfig._
import se.scalablesolutions.akka.actor.Actor._


case class BotConfiguration (serviceName: String,
                             host: Option[String],
                             port: Option[Int],
                             username: String,
                             password: String,
                             rooms: List[String])

sealed abstract class ManagerActorMessage


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

    var jabberManagerActor:ActorRef = null 
    val statusProcessor = actorOf[StatusProcessor]
    val plugins:List[LocalActorRef] = List(actorOf[BibleSearchPlugin]).map(_.asInstanceOf[LocalActorRef])

  def main(args:Array[String]) {
    
    try {
      val jabberUser = System.getProperty("jabberUser") getOrElse "demabot"
      val jabberPassword = System.getProperty("jabberPassword") getOrElse "5088"
      val jabberServer = System.getProperty("jabberServer") getOrElse "jabber.ru"
      val jabberServerHost = System.getProperty("jabberServerHost") getOrElse "77.88.57.181"
      val jabberServerPort = (System.getProperty("jabberServerPort"):Option[String]).map(_.toInt) getOrElse 80


      var configuration = BotConfiguration(jabberServer,
                                           jabberServerHost,
                                           jabberServerPort,
                                           jabberUser,
                                           jabberPassword,
                                           List("christian@conference.jabber.ru"))

      val proxyHost:Option[String] = System.getProperty("proxyHost")
      val proxyPort:Option[String] = System.getProperty("proxyPort")
      val cconf = if(!proxyHost.isEmpty) {
        println("PROXY!")
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
    
    jabberManagerActor = actorOf(new JabberManagerActor(connection))

    val supervisor = Supervisor(
            SupervisorConfig(RestartStrategy(OneForOne, 1000, 1000, List(classOf[Throwable])),
            (List(jabberManagerActor, statusProcessor) ++ plugins).map(Supervise(_, LifeCycle(Permanent)))))
      SASLAuthentication.supportSASLMechanism("PLAIN", 0);

      connection.connect();
      connection.login(configuration.username, configuration.password);

      configuration.rooms.foreach(jabberManagerActor ! JoinMUC(_))

      val collector: PacketCollector = connection.createPacketCollector(new PacketFilter() {
          def accept(p: Packet) = true
        })

      while (true) {
        try {
          val packet = collector.nextResult
          jabberManagerActor ! packet
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


class StatusProcessor extends Actor {
  val statuses = HashMap[String, (Date, Int)]() //JID, Дата последней смены статуса, количество смен статуса
  def receive = {
      case s=>
    }
}
class JabberManagerActor(val connection:XMPPConnection) extends Actor {
  val MAX_MSG_LENGTH = 350
  case class GetBannedPersons
  

  val rooms = HashMap[String, MultiUserChat]()

  def receive = {

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
          muc.join("BibleBot", "", history, SmackConfiguration.getPacketReplyTimeout())

          rooms.put(roomName, muc)
          println("ROOMS:")
          rooms.keys.foreach(println _)
        }

      case LeaveMUC(room) => rooms.get(room) match {
          case Some(muc) => 
            muc.leave
            rooms.remove(room)
          case _ => ()
        }
      case SendResponse(originalMsg, body) => originalMsg.getType match {
          case Message.Type.groupchat if body.length <= MAX_MSG_LENGTH && body.lines.size <= 2 =>
            try{
              val Array(room, nick) = originalMsg.getFrom.split('/')

              rooms.get(room).foreach(_.sendMessage(nick + ": " + body))
            }catch{
              case x => x.printStackTrace
            }
          case _ => 
            val newMessage = new Message(originalMsg.getFrom,Message.Type.chat)
            newMessage.setBody(body)
            connection.sendPacket(newMessage)
        }

      case p: Packet => 

        p match {
          case msg: Message =>
          if(msg.getFrom.indexOf('/') > -1){
                val Array(roomJID, nickName) = msg.getFrom.split('/')
                println("packet: from: %s, body: %s".format(msg.getFrom,msg.getBody))
                val room = rooms(roomJID)
                if(room.getNickname != nickName){//Ignore messages from myself
                    App.plugins.foreach(_ ! msg)
                }
            }
          case p: Presence =>
            App.statusProcessor ! p
          case m =>
            println("ZZZ: "+m.getClass)
        }
        println("after packet")
      case x =>
        println("Не знаю что пришло: "+x)
    }
}

// vim: set ts=4 sw=4 et:
