package kz.dema.scalabot

/*
 * RichSQL.scala
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

import java.sql.ResultSet
import java.sql.PreparedStatement
import java.sql.Date
import java.sql.Statement
import java.sql.Connection
import java.sql.Types
import scala.runtime.RichBoolean

object RichSQL {
    private def strm[X](f: RichResultSet => X, rs: ResultSet): Stream[X] =
    if (rs.next) Stream.cons(f(new RichResultSet(rs)), strm(f, rs))
    else { rs.close(); Stream.empty };

    implicit def query[X](s: String, f: RichResultSet => X)(implicit stat: Statement) = {
        strm(f,stat.executeQuery(s));
    }

    implicit def conn2Statement(conn: Connection): Statement = conn.createStatement;

    implicit def rrs2BooleanOption(rs: RichResultSet) = rs.nextBooleanOption;
    implicit def rrs2ByteOption(rs: RichResultSet) = rs.nextByteOption;
    implicit def rrs2IntOption(rs: RichResultSet) = rs.nextIntOption;
    implicit def rrs2LongOption(rs: RichResultSet) = rs.nextLongOption;
    implicit def rrs2FloatOption(rs: RichResultSet) = rs.nextFloatOption;
    implicit def rrs2DoubleOption(rs: RichResultSet) = rs.nextDoubleOption;
    implicit def rrs2StringOption(rs: RichResultSet) = rs.nextStringOption;
    implicit def rrs2DateOption(rs: RichResultSet) = rs.nextDateOption;

    implicit def rrs2Boolean(rs: RichResultSet) = rs.nextBoolean;
    implicit def rrs2Byte(rs: RichResultSet) = rs.nextByte;
    implicit def rrs2Int(rs: RichResultSet) = rs.nextInt;
    implicit def rrs2Long(rs: RichResultSet) = rs.nextLong;
    implicit def rrs2Float(rs: RichResultSet) = rs.nextFloat;
    implicit def rrs2Double(rs: RichResultSet) = rs.nextDouble;
    implicit def rrs2String(rs: RichResultSet) = rs.nextString;
    implicit def rrs2Date(rs: RichResultSet) = rs.nextDate;


	implicit def resultSet2Rich(rs: ResultSet) = new RichResultSet(rs);
    implicit def rich2ResultSet(r: RichResultSet) = r.rs;

    class RichResultSet(val rs: ResultSet) {

        var pos = 1
        def apply(i: Int) = { pos = i; this }
        
		def nextBoolean: Boolean = {val ret = rs.getBoolean(pos); pos = pos + 1; ret}
        def nextByte: Byte = { val ret = rs.getByte(pos); pos = pos + 1; ret}
        def nextInt: Int = { val ret = rs.getInt(pos); pos = pos + 1; ret}
        def nextLong: Long = { val ret = rs.getLong(pos); pos = pos + 1; ret}
        def nextFloat: Float = { val ret = rs.getFloat(pos); pos = pos + 1; ret}
        def nextDouble: Double = { val ret = rs.getDouble(pos); pos = pos + 1; ret}
        def nextString: String = { val ret = rs.getString(pos); pos = pos + 1; ret}
        def nextDate: Date = { val ret = rs.getDate(pos); pos = pos + 1; ret}

        def nextBooleanOption: Option[Boolean] = {val ret = rs.getBoolean(pos); pos = pos + 1; if(rs.wasNull) None else Some(ret) }
        def nextByteOption: Option[Byte] = { val ret = rs.getByte(pos); pos = pos + 1;  if(rs.wasNull) None else Some(ret) }
        def nextIntOption: Option[Int] = { val ret = rs.getInt(pos); pos = pos + 1; if(rs.wasNull) None else Some(ret) }
        def nextLongOption: Option[Long] = { val ret = rs.getLong(pos); pos = pos + 1; if(rs.wasNull) None else Some(ret) }
        def nextFloatOption: Option[Float] = { val ret = rs.getFloat(pos); pos = pos + 1;  if(rs.wasNull) None else Some(ret) }
        def nextDoubleOption: Option[Double] = { val ret = rs.getDouble(pos); pos = pos + 1;  if(rs.wasNull) None else Some(ret) }
        def nextStringOption: Option[String] = { val ret = rs.getString(pos); pos = pos + 1;  if(rs.wasNull) None else Some(ret) }
        def nextDateOption: Option[Date] = { val ret = rs.getDate(pos); pos = pos + 1;  if(rs.wasNull) None else Some(ret) }

        def foldLeft[X](init: X)(f: (ResultSet, X) => X): X = rs.next match {
            case false => init
            case true => foldLeft(f(rs, init))(f)
        }
        def map[X](f: ResultSet => X) = {
            var ret = List[X]()
            while (rs.next())
            ret = f(rs) :: ret
            ret.reverse; // ret should be in the same order as the ResultSet
        }
    }


    implicit def ps2Rich(ps: PreparedStatement) = new RichPreparedStatement(ps);
    implicit def rich2PS(r: RichPreparedStatement) = r.ps;

    implicit def str2RichPrepared(s: String)(implicit conn: Connection): RichPreparedStatement = conn prepareStatement(s);
    implicit def conn2Rich(conn: Connection) = new RichConnection(conn);

    implicit def st2Rich(s: Statement) = new RichStatement(s);
    implicit def rich2St(rs: RichStatement) = rs.s;

    class RichPreparedStatement(val ps: PreparedStatement) {
        var pos = 1;
        private def inc = { pos = pos + 1; this }

        def execute[X](f: RichResultSet => X): Stream[X] = {
            pos = 1; strm(f, ps.executeQuery)
        }
        def <<![X](f: RichResultSet => X): Stream[X] = execute(f);

        def execute = { pos = 1; ps.execute }
        def <<! = execute;

        def <<(x: Option[Any]):RichPreparedStatement = {
            x match {
                case None =>
                    ps.setNull(pos,Types.NULL)
                    inc
                case Some(y) => (this << y)
            }
        }
        def <<(x:Any):RichPreparedStatement ={
            x match {
                case z:Boolean =>
                    ps.setBoolean(pos, z)
                case z:Byte =>
                    ps.setByte(pos, z)
                case z:Int =>
                    ps.setInt(pos, z)
                case z:Long =>
                    ps.setLong(pos, z)
                case z:Float =>
                    ps.setFloat(pos, z)
                case z:Double =>
                    ps.setDouble(pos, z)
                case z:String =>
                    ps.setString(pos, z)
                case z:Date =>
                    ps.setDate(pos, z)
                case z => ps.setObject(pos,z)
            }
            inc
        }
    }


    class RichConnection(val conn: Connection) {
        def <<(sql: String) = new RichStatement(conn.createStatement) << sql;
        def <<(sql: Seq[String]) = new RichStatement(conn.createStatement) << sql;
    }


    class RichStatement(val s: Statement) {
        def <<(sql: String) = { s.execute(sql); this }
        def <<(sql: Seq[String]) = { for (val x <- sql) s.execute(x); this }
    }
}
