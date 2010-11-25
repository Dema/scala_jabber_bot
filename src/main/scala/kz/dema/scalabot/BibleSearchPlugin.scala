package kz.dema.scalabot
import org.apache.lucene.analysis.standard.StandardAnalyzer; 
import org.apache.lucene.document.Document; 
import org.apache.lucene.document.Field; 
import org.apache.lucene.index.IndexWriter; 
import org.apache.lucene.queryParser.ParseException; 
import org.apache.lucene.queryParser.QueryParser; 
import org.apache.lucene.search._; 
import org.apache.lucene.store.Directory; 
import org.apache.lucene.store.RAMDirectory; 
import org.apache.lucene.util.Version;
import org.jivesoftware.smack._
import org.jivesoftware.smack.filter._
import org.jivesoftware.smackx.muc._

import scala.collection.JavaConversions._

import se.scalablesolutions.akka.actor.Actor
import se.scalablesolutions.akka.actor._


import packet.Message


import java.sql.DriverManager.{getConnection => connect};
import RichSQL._

class BibleSearch {

  implicit val conn = connect("jdbc:h2:db/bibledb;AUTO_SERVER=TRUE","sa","")
  val index = new RAMDirectory()

  val analyzer = new StandardAnalyzer(Version.LUCENE_CURRENT)
  val w = new IndexWriter(index, analyzer, true, IndexWriter.MaxFieldLength.UNLIMITED);

  case class Verse(bookName:String, chapterIdx:Int, verseIdx:Int, text:String)

  val sql = """
select 
(select ab.abbreviation from book_names_abbr ab where id = (SELECT min(a.id) FROM  BOOK_NAMES_ABBR a where a.book_id = c.book_id)) as book,
c.chapter_index,
v.verse_index,
text
from verses v join chapters c on c.id = v.chapter_id
join books books on books.id = c.book_id
order by books.id, c.chapter_index, v.verse_index
"""


  for(val verse  <- sql <<! (rs =>Verse(rs,rs,rs,rs))){
    val doc = new Document();
    doc.add(new Field("bookName", verse.bookName, Field.Store.YES, Field.Index.NOT_ANALYZED));
    doc.add(new Field("chapterIdx", verse.chapterIdx.toString, Field.Store.YES, Field.Index.NOT_ANALYZED));
    doc.add(new Field("verseIdx", verse.verseIdx.toString, Field.Store.YES, Field.Index.NOT_ANALYZED));
    doc.add(new Field("text", verse.text, Field.Store.YES, Field.Index.ANALYZED))
    w.addDocument(doc)
  }
  w.optimize
  w.close

  val hitsPerPage = 5;

  def search(query:String, originalMsg:Message):String = {
    val searcher = new IndexSearcher(index, true);
    val analyzer = new StandardAnalyzer(Version.LUCENE_CURRENT)
    val q = new QueryParser(Version.LUCENE_CURRENT, "text", analyzer).parse(query);
    val hits = searcher.search(q,hitsPerPage).scoreDocs

    val result = hits.map{hit =>
      val doc = searcher.doc(hit.doc)
      "%s %s:%s %s".format(doc.get("bookName"), doc.get("chapterIdx"), doc.get("verseIdx"), doc.get("text"))
    }.toList.mkString("\n")

    searcher.close
    result
  }
}

class BibleSearchPlugin extends Actor {
  case class Search(val originalMsg:Message, val narrow:Boolean, val text:String)
  case class ShowVerse(originalMsg:Message, text:String)

  val bibleSearch = new BibleSearch()

  val SearchCommand = """^!!(.*)$""".r
  val SearchNarrowCommand = """^!(.*)$""".r
  val ShowVerseCommand = """^%(.*)$""".r

  val ShowVerseChapter = """^(.+?)[.:;, ]+\s*(\d+)$""".r
  val ShowVerseSingle = """^(.+?)[.:;, ]+\s*(\d+)[;:, ](\d+)$""".r
  val ShowVerseRange = """^(.+?)[.:;, ]+\s*(\d+)[;:, ](\d+)?-(\d+)?$""".r


  def receive = {
    case originalMsg:Message => originalMsg.getBody match {
        case SearchCommand(text) =>
          println("SearchCommand:"+text)
          self ! Search(originalMsg, false, text)
        case SearchNarrowCommand(text) =>
          self ! Search(originalMsg, true, text)
        case ShowVerseCommand(text) =>
          self ! ShowVerse(originalMsg, text)
        case xx =>println("XXX:"+xx)
      }
    case Search(originalMsg, narrow_?, text) =>
      App.jabberManagerActor ! SendResponse(originalMsg, bibleSearch.search(text,originalMsg))

    case ShowVerse(originalMsg, text) =>
      implicit def str2Option(s:String) = if(s == null || s.trim.length == 0) None else Some(s.trim)

      val command:Option[(String,String,Option[String],Option[String])] = text match {
        case ShowVerseRange(bookName, chapterIdx, verseStart, verseEnd) => println(1); Some((bookName.trim, chapterIdx.trim, verseStart,verseEnd))
        case ShowVerseSingle(bookName, chapterIdx, verseIdx) => println(2); Some((bookName.trim, chapterIdx.trim, verseIdx.trim, verseIdx.trim))
        case ShowVerseChapter(bookName, chapterIdx) => println(3); Some((bookName.trim, chapterIdx.trim, None, None))
        case x => None
      }
            
      command match {
        case Some((bookName, chapterIdx, verseStart, verseEnd)) =>
          println((bookName, chapterIdx, verseStart, verseEnd))
          implicit val conn = bibleSearch.conn

        case class ListVerse(idx:Int, text:String)
        val sql = """
                         select v.verse_index, text from verses v
                         join chapters c on c.id = v.chapter_id 
                         join books b on b.id  = c.book_id
                         where c.chapter_index = ? 
                             and b.id in (select ab.book_id from book_names_abbr ab where UPPER(ab.abbreviation) = ?)
                             and verse_index between ? and ?
                         order by v.verse_index"""
        val result = for{ verse <- sql << chapterIdx << bookName.toUpperCase << verseStart.getOrElse(0) << verseEnd.getOrElse(99999) <<! (rs=>ListVerse(rs,rs))
        } yield {"%d %s".format(verse.idx,verse.text)}
        println("!!!!!!!!!@@@@")
        result.print
        App.jabberManagerActor ! SendResponse(originalMsg, result.mkString("\n"))

        case None =>
          val error = """
                    Неизвестный формат запроса. Допустимые варианты:
                    Мф.1
                    Мф.1:1
                    Мф.1:1-10
                    Мф.1:1-
                    Мф.1:-5"""
          App.jabberManagerActor ! SendResponse(originalMsg, error)
      }



    case x => println("!!:"+x)
  }
}

// vim: set ts=4 sw=4 et:
