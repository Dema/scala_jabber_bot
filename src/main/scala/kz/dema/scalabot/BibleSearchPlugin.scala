package kz.dema.scalabot
import scala.actors._
import scala.actors.Actor._
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

import packet.Message


import java.sql.DriverManager.{getConnection => connect};
import RichSQL._

class BibleSearch {
  case class Verse(val id:Long, val text:String)

  implicit val conn = connect("jdbc:hsqldb:file:/home/dema/workspace/scalabot/bibledb/bibledb")
  val index = new RAMDirectory()

  val analyzer = new StandardAnalyzer(Version.LUCENE_CURRENT)
  val w = new IndexWriter(index, analyzer, true, IndexWriter.MaxFieldLength.UNLIMITED);

  for(val verse  <- "SELECT ID, TEXT FROM VERSES" <<! (rs =>Verse(rs,rs))){
    val doc = new Document();
    doc.add(new Field("id", verse.id.toString, Field.Store.YES, Field.Index.NOT_ANALYZED));
    doc.add(new Field("text", verse.text, Field.Store.YES, Field.Index.ANALYZED))
    w.addDocument(doc)
  }
//  w.optimize
  w.close

  val hitsPerPage = 10;

  def search(query:String, originalMsg:Message):String = {
    val searcher = new IndexSearcher(index, true);
    val analyzer = new StandardAnalyzer(Version.LUCENE_CURRENT)
    val q = new QueryParser(Version.LUCENE_CURRENT, "text", analyzer).parse(query);
    val hits = searcher.search(q,hitsPerPage).scoreDocs
    val result = hits.map(hit=>searcher.doc(hit.doc).get("text")).toList.mkString("\n")
    searcher.close
    result
  }
}

object BibleSearchPlugin extends Actor with Plugin {
  case class Search(val originalMsg:Message, val narrow:Boolean, val text:String)
  case class ShowVerse(originalMsg:Message, text:String)

  val bibleSearch = new BibleSearch()

  val SearchCommand = """^!!(.*)$""".r
  val SearchNarrowCommand = """^!(.*)$""".r
  val ShowVerseCommand = """^@(.*)$""".r


  start

  override def processIncomingMessage(originalMsg:Message) = {
    case SearchCommand(text) =>
      println("SearchCommand:"+text)
      this ! Search(originalMsg, false, text)
    case SearchNarrowCommand(text) => 
      this ! Search(originalMsg, true, text)
    case ShowVerseCommand(text) =>
      this ! ShowVerse(originalMsg, text)
  }
  def act = loop{
    react {
      case Search(originalMsg, narrow_?, text) =>
        JabberManagerActor ! SendResponse(originalMsg, bibleSearch.search(text,originalMsg))
      case x => println("!!:"+x)
    }
  }
}

// vim: set ts=4 sw=4 et:
