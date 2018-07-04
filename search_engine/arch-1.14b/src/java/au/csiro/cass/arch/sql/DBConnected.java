/*
 * 
 * Copyright (c) CSIRO Astronomy and Space Science
 * Author Arkadi.Kosmynin@csiro.au
 * 
 */
package au.csiro.cass.arch.sql;

/**
 * A set of db related functions needed by IndexSite and LogSite objects
 * 
 * @author Arkadi Kosmynin
 *
 */
public interface DBConnected
{
  /**
  * Disconnects whatever is connected
  * 
  */
	
  void disconnect() throws Exception ;


}