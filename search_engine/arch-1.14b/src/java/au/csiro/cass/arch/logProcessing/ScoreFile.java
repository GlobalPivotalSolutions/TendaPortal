/*
 * 
 * Copyright (c) CSIRO Astronomy and Space Science
 * Author Arkadi.Kosmynin@csiro.au
 * 
 */
package au.csiro.cass.arch.logProcessing;

import java.util.Date;

/**
 * Score files contain a list of urls with counted hit scores, plus
 * dates between which the hits were received
 * 
 * Interface to be implemented by a score file object
 */
public interface ScoreFile
{
 /**
  * Read and parse next record. Update URL and score.
  * 
  * @return true if there is a next record, false otherwise
  * @throws Exception
  */
 boolean next() throws Exception ;
 
 /**
  * Get scoring interval start date and time
  * 
  * @return scoring interval start date and time
  */
 Date getStart() ;

 /**
  * Get scoring interval end date and time
  * 
  * @return scoring interval end date and time
  */
 Date getEnd() ;
 
 /**
  * Get URL from the last record
  * 
  * @return URL from the last record
  */
 String getURL() ;

 /**
  * Get score from the last record
  * 
  * @return URL from the last record
  */
 int getScore() ;
 
 /**
  * Write URL and score
  * 
  * @param URL URL to write 
  * @param score score to write 
  * @throws Exception
  */
 void put( String URL, int score ) throws Exception ;

 /**
  * Close file
  * 
  * @throws Exception
  */
 void close() throws Exception ;
}
