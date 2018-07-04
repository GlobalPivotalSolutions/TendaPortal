/*
 * 
 * Copyright (c) CSIRO Astronomy and Space Science
 * Author Arkadi.Kosmynin@csiro.au
 * A reference scanner implementation
 * 
 */
package au.csiro.cass.arch.security;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.sql.ResultSet;

import org.apache.hadoop.conf.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.csiro.cass.arch.index.IndexSite;
import au.csiro.cass.arch.index.Indexer;
import au.csiro.cass.arch.sql.IndexRootDB;

public class BasicReporter implements Reporter
{
  public static final Logger LOG = LoggerFactory.getLogger(BasicReporter.class);
  Configuration        conf ;

  @Override
  public void report( Indexer indexer, IndexSite site ) throws Exception
  {
	if ( site == null )
		throw new Exception( "Site mist be defined." ) ;
  	// read alerts
	IndexRootDB db = indexer.getDb() ;
	ResultSet alerts = db.readAlerts( site.getName() ) ;
	ScanAlert alert = null ;
	boolean threatsReported = false ;
	String outFile = site.getCfg().getInherited( "scan.out.file", null ) ;
	boolean sendEmail = site.getCfg().getInherited( "scan.send.email", true ) ;
	PrintWriter writer = null ;
	if ( outFile != null ) 
		writer = new PrintWriter( new BufferedWriter( new FileWriter( outFile, true ) ) ) ;
	
	while( (alert = db.nextAlert( alerts )) != null )
	{
	  String logRecord = "Scan alert " ;	
	  int level = Indexer.INFO ;
	  if ( alert.code != alert.SAFE ) level = Indexer.WARN ;
	  if ( alert.code == alert.THREAT ) level = Indexer.ERROR ;
	  // attach to email that will be sent
      if ( !threatsReported )
    	  { report( Indexer.ERROR, "\r\nSecurity scan results for site " + site.getName() +
    			    "\r\n", site, sendEmail, writer ) ;
    	    threatsReported = true ;
    	  }
      report( level, alert.codes[ alert.code ] + " " + alert.url + " " + alert.message,
    		  site, sendEmail, writer ) ;
      logRecord += alert.codes[ alert.code ] + " " + alert.url + " " + alert.message ;
      if ( !logRecord.equals( "Scan alert " ) )
    	                                   LOG.warn( logRecord ) ;
	}
	if ( writer != null ) writer.close() ;
  }

  public void report( int level, String message, IndexSite site, boolean sendMail, PrintWriter writer ) throws Exception
  {
	if ( sendMail ) site.appendEmail( level, message ) ;
	if ( level == Indexer.INFO && LOG.isInfoEnabled() ||
	     level == Indexer.WARN && LOG.isWarnEnabled() ||
	     level == Indexer.ERROR && LOG.isErrorEnabled() ) 
	{
	  writer.append( message ) ; writer.append( "\r\n" ) ;
	}
  }
  
  
  @Override
  public void setConf( Configuration conf )
  {
	this.conf = conf ;
  }

  @Override
  public Configuration getConf()
  {
	return conf ;
  }

}
