/*
 * 
 * Copyright (c) CSIRO Astronomy and Space Science
 * Author Arkadi.Kosmynin@csiro.au
 * 
 */
package au.csiro.cass.arch.logProcessing;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.PrintStream;
import java.text.DateFormat;
import java.util.Date;


public class ScoreFileImpl implements ScoreFile
{
 Date              start ; // start of time interval covered by the scores
 Date                end ; // end of time interval covered by the scores
 String              URL ; // URL read from the last record by next
 int               score ; // score read from the last record by next
 FileOutputStream     fo ; // output
 PrintStream          ps ; // output
 FileReader           fr ; // input 
 BufferedReader       br ; // input
 String         filename ; // file name
 
 /**
  * A constructor
  */ 
 ScoreFileImpl() {}
 
 
 /**
  * A factory. Opens a score file for writing. Writes the scoring 
  * start and end dates.
  * 
  * @param filename name of file to open
  * @param start    scoring start date
  * @param end      scoring end date
  * @throws Exception
  */ 
 public static ScoreFile newScoreFile( String filename, Date start, Date end )
 throws Exception 
 {
  ScoreFileImpl sf = new ScoreFileImpl() ;
  sf.fo = new FileOutputStream( filename, false ) ; 
  sf.ps = new PrintStream( sf.fo ) ;
  sf.ps.println( DateFormat.getDateTimeInstance().format( start ) ) ;
  sf.ps.println( DateFormat.getDateTimeInstance().format( end ) ) ;
  sf.start = start ;
  sf.end = end ;
  sf.filename = filename ;
  return sf ;
 }
 
 /**
  * A factory. Opens a score file for reading. Reads and parses the scoring 
  * start and end dates.
  * 
  * @param filename name of file to open
  * @throws Exception
  */ 
 public static ScoreFile newScoreFile( String filename )
 throws Exception
 {
  ScoreFileImpl sf = new ScoreFileImpl() ;
  sf.fr = new FileReader( filename ) ;
  sf.br = new BufferedReader( sf.fr ) ;
  try { 
        String start = sf.br.readLine() ;
        String end = sf.br.readLine() ;
        sf.start = DateFormat.getDateTimeInstance().parse( start ) ;
        sf.end = DateFormat.getDateTimeInstance().parse( end ) ;
      } catch( Exception e )
      {
        throw new Exception( "Can't read dates in the score file." ) ;
      }
  sf.filename = filename ;
  return sf ;
 }

 /**
  * Read and parse next record. Update URL and score.
  * 
  * @return true if there is a next record, false otherwise
  * @throws Exception
  */
 public boolean next()
 throws Exception 
 {
   String ln = "" ;
   try {
         ln = br.readLine() ;
         if ( ln == null ) return false ;
         int i = ln.indexOf( " " ) ;
         score = Integer.parseInt( ln.substring( i + 1 ) ) ;
         URL = ln.substring( 0, i ) ;
         return true ;
       } catch( Exception e )
       {
           System.out.println( "Bad line in " + filename + ": " + ln + ", error: " + e.getMessage() ) ;
       }
   return false ; // will not return, anyway
 }

 /**
  * Write URL and score
  * 
  * @param URL URL to write 
  * @param score score to write 
  * @throws Exception
  */
 public void put( String URL, int score )
 throws Exception
 {
  if ( ps == null )
   throw new Exception( "This score file was not opened for output." ) ;
  ps.format( "%s %d\n", URL, score ) ;
  this.score = score ;
  this.URL = URL ;
 }
 
 /**
  * Close file
  * 
  * @throws Exception
  */
 public void close()
 throws Exception
 {
   if ( fo != null ) fo.close() ;
   if ( ps != null ) ps.close() ; 
   if ( fr != null ) fr.close() ; 
   if ( br != null ) br.close() ;
 } 
 
 // Getters
 
 public Date getStart()
 {
  return start ;
 }
 
 public Date getEnd()
 {
   return end ;
 }
 
 public String getURL()
 {
   return URL ;
 }
 
 public int getScore()
 {
   return score ;
 }
  
}
