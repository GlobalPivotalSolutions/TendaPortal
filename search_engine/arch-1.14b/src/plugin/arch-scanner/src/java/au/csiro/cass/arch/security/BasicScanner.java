/*
 * 
 * Copyright (c) CSIRO Astronomy and Space Science
 * Author Arkadi.Kosmynin@csiro.au
 * A reference scanner implementation
 * 
 */
package au.csiro.cass.arch.security;

import java.util.HashSet;
import java.util.Set;
import java.util.zip.CRC32;

import org.apache.hadoop.conf.Configuration;
import org.apache.nutch.parse.Outlink;
import org.apache.nutch.parse.Parse;

import au.csiro.cass.arch.sql.IndexRootDB;
import au.csiro.cass.arch.sql.IndexSiteDB;

public class BasicScanner implements Scanner
{
  Configuration        conf ;
  
  @Override
  public void scan( ScanContext context, ScanResult scanResult ) throws Exception
  {
	// read status of the page at the last scan
	ScanResult status = context.siteDB.readScanResult( context.url ) ;
    // calculate CRC32 of the source and output pages
	if ( context.scanningSrc && scanResult.srcCRC == 0 ) // not calculated yet
		scanResult.srcCRC = calculateCRC( context.lowcaseSource ) ;
	if ( context.scanningOut && scanResult.outCRC == 0 ) // not calculated yet
		scanResult.outCRC = calculateCRC( context.lowcaseContent ) ;
	if ( ( !context.scanningSrc || scanResult.srcCRC == status.srcCRC ) &&
		 ( !context.scanningOut || scanResult.outCRC == status.outCRC ) )
	       { scanResult.notChanged = true ; return ; } // page not changed, no need to scan
	// scan for script fragments
	if ( context.config.reportNewScripts || context.config.reportChangedScripts )
	{
	  if ( context.scanningOut ) scanForScripts( context, scanResult, context.lowcaseContent ) ;	
	  if ( context.scanningSrc ) scanForScripts( context, scanResult, context.lowcaseSource ) ;
	  if ( scanResult.scriptFound == true )
	  {
	   if ( !status.scriptFound ) // new script
		if ( status.outCRC == 0 && status.srcCRC == 0 &&
				( context.config.reportNewScripts || context.config.reportChangedScripts ) )
		  scanResult.addAlert( new ScanAlert( context.site, context.url, "UNSURE", "New script found." ) ) ;
		else if ( ( status.outCRC != 0 || status.srcCRC != 0 ) && context.config.reportChangedScripts )
		  scanResult.addAlert( new ScanAlert( context.site, context.url, "UNSURE", "Page with script changed." ) ) ;
	  }
	}
	// check for suspicious strings
    scanForAlerts( context, scanResult ) ;
    
	// check for forms
    if ( context.config.reportChangedForms || context.config.reportNewForms )
	  if ( context.lowcaseContent.indexOf( "<form" ) >= 0 ) // don't have to scan source 
		 { 
		   scanResult.formFound = true ;
		   if ( !status.isFormFound() ) // new form
			if ( status.outCRC == 0 && status.srcCRC == 0
			   && ( context.config.reportNewForms || context.config.reportChangedForms ) )
			  scanResult.addAlert( new ScanAlert( context.site, context.url, "UNSURE", "New form found." ) ) ;
			else if ( ( status.outCRC != 0 || status.srcCRC != 0 ) && context.config.reportChangedForms )
			  scanResult.addAlert( new ScanAlert( context.site, context.url, "UNSURE", "Page with form changed." ) ) ;
		 }
    // 
	// read links at the previous scan
    if ( context.config.reportLinkChanges && 
         context.parseResult != null && !context.parseResult.isEmpty() )
    {
      Set<String> oldLinks = context.siteDB.readLinks( context.url, -1 ) ;
	  // compare the links and report new and removed ones
      Parse parse = context.parseResult.get( context.content.getUrl() ) ;
      Outlink[] outlinks = parse.getData().getOutlinks() ;
      HashSet<String> newLinks = new HashSet<String>() ;
      if ( outlinks != null )
        for ( Outlink link : outlinks )
        {
          String li = link.getToUrl() ;
          if ( context.config.ignoredLinks.contains( li ) )
        	                                          continue ;
           newLinks.add( li ) ;
        }
      if ( oldLinks.size() != newLinks.size() || !oldLinks.containsAll( newLinks ) )
      {
    	scanResult.newLinks = new HashSet<String>( newLinks ) ;
    	scanResult.newLinks.removeAll( oldLinks ) ;
    	scanResult.removedLinks = oldLinks ;
    	scanResult.removedLinks.removeAll( newLinks ) ;
      }
    }
  }
  
  long calculateCRC( String cont )
  {
	CRC32 crc = new CRC32() ;
	crc.update( cont.getBytes() ) ;
	return crc.getValue() ;
  }
  
  
  void scanForScripts( ScanContext context, ScanResult scanResult, String str )
  throws Exception
  {
    for ( String[] edges : context.config.scriptEdges )
    {
      int index = 0 ;
      String start = edges[ 0 ] ;
      if ( edges.length != 2 )
    	throw new Exception( "Badly defined script edges in site " + context.site + ": " + start ) ;
      String end = edges[ 1 ] ;
      searchingForScript:
      do { 
           int startI = str.indexOf( start, index ) ;
           if ( startI < 0 ) break ;
           int endI = str.indexOf( end, index ) ;
           if ( endI < 0 ) endI = str.length() ;
           index = endI + 1 ;
           // Should we ignore this one?
           if ( endI - startI < context.config.minScriptSize ) continue ;
           for ( String bit : context.config.ignore )
           {
        	  int pos = str.indexOf( bit, startI ) ;
              if ( pos > 0 && pos < endI ) continue searchingForScript ;
           }
           // At this point we found a script fragment and it is not to be ignored
           scanResult.scriptFound = true ; return ;
         } while( index > 0 ) ; 
    }
  }

  
  void scanForAlerts( ScanContext context, ScanResult scanResult )
  throws Exception
  {
    for ( String[] alert : context.config.alert )
    {
      String bit = alert[ 0 ] ;
      if ( alert.length < 2 )
      	throw new Exception( "Badly defined scan alert " + context.site + ": " + bit ) ;
      String level = alert[ 1 ] ;
      String where = alert.length > 2 ? alert[ 2 ] : "both" ;
      if ( where.equalsIgnoreCase( "both" ) || where.equalsIgnoreCase( "src" ) )
    	  scanForAlerts( context, scanResult, context.lowcaseSource, bit, level ) ;
      if ( where.equalsIgnoreCase( "both" ) || where.equalsIgnoreCase( "out" ) )
    	  scanForAlerts( context, scanResult, context.lowcaseContent, bit, level ) ;
    }
  }
  
  
  void scanForAlerts( ScanContext context, ScanResult scanResult, String str, String bit, String level )
  {
	int index = str.indexOf( bit ) ;
	if (  index >= 0 ) // found an alert
	{
      int end = index + 20 > str.length() ? str.length() : index + 20 ;
	  String fragment = str.substring( index, end ) ;
	  fragment = fragment.replace( "\r", "\\r" ).replace( "\n", "\\n" ).replace( "\t", "\\t" ) ;
	  ScanAlert alert = new ScanAlert( context.site, context.url, level, "Found " + fragment ) ;
	  scanResult.addAlert(alert) ;
	}
	  
  }
  
  
  @Override
  public void setConf( Configuration conf )
  {
    this.conf = conf ;
  }

  @Override
  public Configuration getConf()
  { return conf ; }

}
