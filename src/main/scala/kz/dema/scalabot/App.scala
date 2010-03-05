package kz.dema.scalabot

import org.jivesoftware.smack._
import org.jivesoftware.smack.filter._
import org.jivesoftware.smackx.muc._

import packet.{Presence, Message, Packet}
import proxy.ProxyInfo
import scala.actors._
import scala.actors.Actor._
import scala.collection.mutable.HashMap
import scala.collection.jcl.Conversions._
import java.util.{Timer, Date}
import java.util.TimerTask
import java.util.ArrayList

case class BotConfiguration(serviceName: String, host: String, port: Option[Int], username: String, password: String, rooms: List[String])

sealed abstract class ManagerActorMessage

case class PrivateChatMessage(packet: Packet)
case class GroupChatMessage(packet: Packet)


case class JoinMUC(name: String) extends ManagerActorMessage
case class LeaveMUC(name: String) extends ManagerActorMessage
case class SendResponse(to: String, body: String) extends ManagerActorMessage
case class IncomingMessage(from: String, body: String) extends ManagerActorMessage
case class UnBan(who:String) extends ManagerActorMessage
case class GetBannedPersons() extends ManagerActorMessage

/**
 * Hello world!
 *
 */
object App extends Application {
    try {
        var configuration = BotConfiguration("jabber.ru",
                                             "77.88.57.181",
                                             Some(58128),
                                             "JABBER_USER",
                                             "PASSWORD",
                                             List("testroom1@conference.jabber.ru"))

        val cconf = new ConnectionConfiguration(configuration.host, configuration.port.getOrElse(5222),
                                                configuration.serviceName,
                                                ProxyInfo.forHttpProxy("10.1.164.13", 5865, null, null));

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
                val packet = collector.nextResult
                JabberManagerActor ! packet
            } catch {
                case _ => ()
            }
        }
    } catch {
        case e => {
                println(e)
            }
    }
}
object MUCEventsListener {
}

object BanListManager extends Actor {
    val timer = new Timer()
    case class CheckForBanExpiration
    def act = loop {
        react {
            case CheckForBanExpiration => {
                    check()
                    //TimerTask({}), 600 * 1000
                    timer.schedule(new TimerTask(){
                            def run(){
                                BanListManager ! CheckForBanExpiration
                            }
                        }, 600 * 1000)
                }
        }
    }

    def check() {
        /*        val banned: List[(String, List[(String, String)])] = JabberManagerActor !? JabberManagerActor.GetBannedPersons
         for{item <- banned
         user <- item._2
         } {
         if (banExpired(user)) {
         JabberManagerActor ! UnBan(item._1, user)
         }
         }*/
    }

}

object StatusProcessor extends Actor {
    val statuses = HashMap[String, (Date, Int)]() //JID, Дата последней смены статуса, количество смен статуса
    def act = loop {
        react{
            case s=>
        }
    }
}
object JabberManagerActor extends Actor {
    case class GetBannedPersons

    val rooms = HashMap[String, MultiUserChat]()
    var connection: XMPPConnection = null

    def start(connection: XMPPConnection) {
        this.connection = connection
        super.start
    }

    def act = loop {
        react {

            case GetBannedPersons => {
                    for{room <- rooms.keySet} {
                        val chat = rooms.get(room)
                        (room, chat.map(c=>new ArrayList[Affiliate](c.getOutcasts).toList.map(_.getJid())))
                    }
                }

            case JoinMUC(roomName) => if (rooms.contains(roomName)) () else {
                    val muc = new MultiUserChat(connection, roomName)
                    muc.join("ScalaBot")

                    rooms.put(roomName, muc)
                }

            case LeaveMUC(room) => rooms.get(room) match {
                    case Some(muc) => muc.leave
                    case _ => ()
                }
            case SendResponse(to, body) => ()

            case IncomingMessage(from, body) => MessageProcessor ! (from, body)

            case p: Packet => processPacket(p)
        }
    }

    def processPacket(p: Packet) = p match {
        case msg: Message => {println(List(msg.getFrom, msg.getType, msg.getBody).mkString(","))}
        case p: Presence => {println(List(p.getFrom, p.getType, p.getMode, p.getStatus).mkString(","))}
        case m => println(m.getClass)
    }

}
object MessageProcessor extends Actor {
    def act = loop {
        react {
            case a => println(a)
        }
    }
}

