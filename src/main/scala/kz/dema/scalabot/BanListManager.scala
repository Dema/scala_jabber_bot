package kz.dema.scalabot

import scala.actors._
import scala.actors.Actor._
import java.util.{Timer, Date}
import java.util.TimerTask

object BanListManager extends Actor {
  case class CheckForBanExpiration
  
  val timer = new Timer()

  this ! CheckForBanExpiration
  
  def act = loop {
    react {
      case CheckForBanExpiration => {
          timer.schedule(new TimerTask(){
              def run(){
                BanListManager ! CheckForBanExpiration
              }
            }, 600 * 1000)
          check()
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


// vim: set ts=4 sw=4 et:
