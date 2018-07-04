/*
 * 
 * Copyright (c) CSIRO Astronomy and Space Science
 * Author Arkadi.Kosmynin@csiro.au
 * 
 */
package au.csiro.cass.arch.logProcessing;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.PrintStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

import org.apache.hadoop.conf.Configuration;
import org.apache.nutch.net.URLFilters;
import org.apache.nutch.util.NutchConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.csiro.cass.arch.sql.DBInterface;
import au.csiro.cass.arch.sql.DBInterfaceFactory;
import au.csiro.cass.arch.sql.IndexRootDB;
import au.csiro.cass.arch.utils.ConfigList;
import au.csiro.cass.arch.utils.Utils;

/**
 * The main class for log processing. Can also be used to reset the database from 
 * a command line.
 * 
 * @author Arkadi Kosmynin
 *
 */
public class LogProcessor
{
 public static final Logger LOG = LoggerFactory.getLogger( LogProcessor.class ) ;

 ConfigList                             cfg ; // parsed configuration parameters
 String                             dataDir ; // home directory
 String                             workDir ; // temporary/work directory
 ArrayList                            sites ; // site names (directories)
 String                       textFileNames ; // extensions of names of textual files
 String[]                         textPages ; // split textFileNames  
 int                         maxIPCacheSize ; // max cache size for client IP addresses
 int                        maxURLCacheSize ; // max cache size for URLs
 int                        captureInterval ; // time interval for search engine hit counting 
 int                          hitsThreshold ; // min hits per interval to quilify for a search eingine
 int                          maxHitsPerDay ; // max hits per URL per day
 int                        maxHitsIpPerDay ; // max hits per IP per URL per day
 int                         abuseThreshold ; // hits/IP/URL beyond which the IP is ignored
 int                         hitHistorySize ; // number of IP hits to remember for a URL
 int                           maxUrlLength ; // max URL length, URLs are considered abnormal if longer
 int                              logLength ; // max length of the log to process, days
 String                            targetDB ; // the target database to write calculated scores to
 String                            dbDriver ; // the db driver
 float                             maxScore ; // high end for the scores scale
 String                          scoresFile ; // scores file name (for input or output)
 Date                              lineDate ; // date & time of the last processed log line
 Map<String,String>              ignoredIPs ; // ips to ignore (search engines)
 IndexRootDB                             db ; // db connection
 String                          initDbMode ; // init database mode
 PreparedStatement              updateScore ; // update url's score in the live db table
 PreparedStatement              getMaxScore ; // get max score from the db
 int                              lineCount ; // input line counter
 String                      ignoredIpsFile ; // file containing a list of IPs to ignore
 long                          cleaningTime ; // latest time IP cache was compacted
 Configuration                    nutchConf ; // Nutch configuration
 URLFilters                         filters ; // URL filters to use 
 public boolean               filterByAgent ; // If true, discard log lines that don'e have known agents
 public boolean            filterByFileName ; // If true, discard log lines that don'e have text file names
 boolean                         deleteLogs ; // delete logs after processing
 // Parsed command line parameters
 boolean                           compress ; // true if to compress output
 String                              action ; // action to perform
 String                              fileIn ; // input file name 
 String                             fileOut ; // output file name 
 String                             keyFile ; // encryption key file name
 String                            siteName ; // name of single site to process
 String                        logDirectory ; // log directory location
 String                          sitemapURL ; // sitemap file URL
 String                          configFile ; // configuration file to use
/**
 * The main method. To get a use description, start without arguments.
 *
 * @param args  an array of string arguments, as per use description.
 */
 public static void main( String[] args )
 {
  
  try
  {
    if ( args.length < 4 || args[ 0 ].charAt( 0 ) != '-' ) 
    {
     System.out.println( "Parameters: -a {lf, fw, lw, uw, rw, ra, ri, mi, gk} -c <config file>\n" +
     		             "  [-s site] [-i file] [-o file/directory] [-z] [-k file] [-d logs directory] [-u sitemap URL]" ) ; 
     System.out.println( " -a  action to perform" ) ; 
     System.out.println( " Actions: es - export a sitemap file." ) ; 
     System.out.println( "          is - import a sitemap file." ) ; 
     System.out.println( "          lf - take log files as input and export scores to a text file." ) ; 
     System.out.println( "          fw - take scores file as input and recalculate weights." ) ; 
     System.out.println( "          lw - take log files as input and recalculate weights." ) ; 
     System.out.println( "          uw - is equal to either is or lw, depending on availability of sitemap." ) ; 
     System.out.println( "          rw - reset document scores, weights and logs processed intervals." ) ; 
     System.out.println( "          ra - reset all - be careful, it will drop all tables in the DB." ) ; 
     System.out.println( "          ri - reset list of bloked IPs." ) ; 
     System.out.println( "          mi - make list of bloked IPs." ) ; 
     System.out.println( "          gk - generate a pair or public and private encryption keys." ) ; 
     System.out.println( " -c  configuration file name" ) ; 
     System.out.println( " -s  name of site to process. All sites are processed if dropped." ) ; 
     System.out.println( " -i  input file name, for operations requiring input files." ) ; 
     System.out.println( " -o  output file name or directory, for operations producing output files." ) ; 
     System.out.println( " -z  compress output file" ) ; 
     System.out.println( " -k  encrypt output file with public key supplied by this option" ) ; 
     System.out.println( " -d  logs directory, overrides the default log directory location" ) ;
     return ;
    }
    doMain( args ) ;
  } catch ( Exception e )
  {
    LOG.error( e.getMessage(), e ) ;
    System.out.println( e.getMessage() ) ;
    e.printStackTrace() ;
    System.exit( -1 ) ;
  }
  System.exit( 0 ) ;
 }
 

/**
 * A factory. Creates and initialises LogProcessor object.
 *
 * @param config configuration parameters file name
 * @return a new LogProcessor object
 * @throws Exception
 */
 public static LogProcessor newLogProcessor( String config )
 throws Exception
 {
   LogProcessor logProcessor = new LogProcessor( config ) ;
   logProcessor.init() ;
   return logProcessor ;
 }
 
/**
 * The main method. To get a use description, start without arguments.
 *
 * @param args  an array os string arguments, as per use description.
 */
 public static void doMain( String[] args ) throws Exception
 {
   String config = Utils.getOption( "-c", args, null ) ;
   if ( config == null ) throw new Exception( "Configuration file is required." ) ;
   LogProcessor logProcessor = new LogProcessor( config ) ;
   logProcessor.init() ;
   logProcessor.action = Utils.getOption( "-a", args, null ) ;
   if ( logProcessor.action == null ) throw new Exception( "Action code is required." ) ;
   logProcessor.siteName = Utils.getOption( "-s", args, null ) ;
   logProcessor.fileIn = Utils.getOption( "-i", args, null ) ;
   logProcessor.fileOut = Utils.getOption( "-o", args, null ) ;
   logProcessor.keyFile = Utils.getOption( "-k", args, null ) ;
   logProcessor.logDirectory = Utils.getOption( "-d", args, null ) ;
   logProcessor.compress = Utils.getOption( "-z", args, false ) ;
   logProcessor.sitemapURL = Utils.getOption( "-u", args, null ) ;
   
   if ( logProcessor.action.equals( "gk" ) ) // the only action that does not require sites
   { if ( logProcessor.fileOut == null ) throw new Exception( "Output directory is required." ) ;
     logProcessor.generateKeys() ;
     return ;
   }

   
   if ( logProcessor.siteName == null )
   {  
	  if ( logProcessor.action.equals( "is" ) || logProcessor.action.equals( "es" ) )
	       throw new Exception( "Site name is required for sitemaps." ) ;
      for ( int i = 0 ; i < logProcessor.sites.size() ; i++ )
      {
    	try {   
              logProcessor.process(  i>0, logProcessor.action, (String)logProcessor.sites.get( i ) ) ;
    	    } catch( Exception e )
    	    {
    	      LOG.error( e.getMessage(), e ) ;
    	      System.out.println( "Processing of logs failed for site " + (String)logProcessor.sites.get( i ) + ": " + e.getMessage() ) ;
    	    }
      }
   }
    else logProcessor.process(  false, logProcessor.action, logProcessor.siteName ) ;
   LogSite.closeAll() ;
 }
 
 public void process( String action, String siteName ) throws Exception
 { process( true, action, siteName ) ; }
 

/**
 * Perform requested action for given site
 *
 * @param repeat    false if this is the first site to be processed, else true
 * @param action    code of the action to perform
 * @param siteName  name of the site to process
 * @param filename  name of file to put/get scores from, or null
 * @return 0 if success, -1 if failed
 */
 public void process( boolean repeat, String action, String siteName )
 throws Exception
 {
     LogSite site = LogSite.getSite( siteName, this ) ;
     if ( site == null ) return ; // log links processing must be disabled 
     if ( action.equals( "ra" ) )
       { if ( !repeat ) { resetIgnoredIPs() ; db.resetAll() ; } site.resetAll() ; }
     else if ( action.equals( "rw" ) ) { site.resetWeights() ; }
     else if ( action.equals( "ri" ) ) { if ( !repeat ) resetIgnoredIPs(); site.resetIgnoredIPs(); }
     else if ( action.equals( "mi" ) ) { checkLogsIgnored( site ) ; site.makeIgnoredIPs( ignoredIPs ) ; }
     else if ( action.equals( "lf" ) )
           { 
    	     if ( fileOut == null ) throw new Exception( "Output file name is required." ) ;
    	     checkLogsIgnored( site ) ; site.logs2scores() ; site.scores2file( fileOut ) ;
    	   }
     else if ( action.equals( "es" ) )
           { 
    	     if ( fileOut == null ) throw new Exception( "Output file name is required." ) ;
	         site.exportSiteMap( fileOut ) ;
	       }
     else if ( action.equals( "fw" ) )
           { if ( fileOut == null ) throw new Exception( "Scores file name is required." ) ;
    	     site.file2scores( fileIn ) ; site.scores2weights() ;
           }
     else if ( action.equals( "uw" ) )
           { if ( fileIn != null && site.sitemapURL != null ) action = "is" ;
                                                         else action = "lw" ;
           }
     else if ( !action.equals( "is" ) && !action.equals( "lw" ) )
    	     throw new Exception( " Unrecognized action code: " + action ) ;

     if ( action.equals( "lw" ) ) { checkLogsIgnored( site ) ; site.logs2scores() ; site.scores2weights() ; }
     if ( action.equals( "is" ) )
           { if ( fileIn == null && site.sitemapURL == null )
                              throw new Exception( "Input file name or URL is required." ) ;
             if ( fileIn != null && sitemapURL != null )
               throw new Exception( "Both input file name and URL are provided, only one of them needed." ) ;
             site.importSiteMap( fileIn ) ; 
           }

     if ( action.equals( "mi" ) ) saveIgnoredIPs() ;
 }
 
 private void checkLogsIgnored( LogSite site ) throws Exception
 {
   if ( fileIn != null || site.sitemapURL != null )
         throw new Exception( "Sitemap file source (URL or file) is defined, therefore logs are being ignored." ) ;	 
 }
 
/**
 * Initialise log processor object
 * @throws Exception
 */
 void init() throws Exception 
 {    
  ignoredIpsFile = dataDir + "/ignoredIPs.txt" ;
  
  maxScore = cfg.get( "max.score", 50f ) ; 
  textFileNames = cfg.get( "text.file.names", ".htm .doc .gs .html .pdf .txt .php" ) ;
  textPages = textFileNames.split( " " ) ;
  filterByAgent = cfg.get( "filter.by.agent",  true ) ;
  filterByFileName = cfg.get( "filter.by.file.name",  true ) ;
  maxIPCacheSize = cfg.get( "max.ip.cache", 100000 ) ;  
  maxURLCacheSize = cfg.get( "max.url.cache", 100000 ) ; 
  captureInterval = cfg.get( "capture.interval", 10 ) * 1000 ; // convert to milliseconds 
  logLength = cfg.get( "log.length", 365 ) ; 
  hitsThreshold = cfg.get( "hits.threshold", 60 ) ;
  deleteLogs = cfg.get( "delete.logs", false ) ;
  
  maxHitsPerDay       = cfg.get( "max.hits.day", 5 ) ; // max hits per URL per day
  maxHitsIpPerDay  = cfg.get( "max.hits.ip.day", 5 ) ; // max hits per IP per URL per day
  abuseThreshold     = cfg.get( "max.hits.norm", 5 ) ; // hits/IP/URL/day beyond which the IP is ignored
  maxUrlLength     = cfg.get( "max.url.length", -1 ) ; 
  hitHistorySize     = cfg.get( "history.size", 10 ) ; // number of IP hits to remember for a URL
  ignoredIPs = new HashMap<String,String>( maxIPCacheSize ) ; // ips to ignore (search engines)
  String nutchHome = System.getenv( "NUTCH_HOME" ) ;
  if ( nutchHome == null )
	  throw new Exception( "Required system environment variable NUTCH_HOME is not set." ) ;
  workDir = cfg.get( "temp.dir", nutchHome + "/temp" ) ;
  new File( workDir ).mkdirs() ;

  nutchConf = NutchConfiguration.create() ;
  nutchConf.set( "solr.server.url", cfg.get( "solr.url", "http://localhost:8993/arch", "SOLR_URL" ) ) ;
  filters = new URLFilters( nutchConf ) ;
  DBInterfaceFactory factory = DBInterfaceFactory.get( nutchConf ) ;
  String database = cfg.getInherited( "database", "MySQL" ) ;
  DBInterface intrf = factory.get( database ) ;  
  db = intrf.newIndexRootDB( cfg, false );
  
  String sitesDir = dataDir + "/sites" ;
  sites = new ArrayList() ;
  String[] files = new File( sitesDir ).list() ;
  for ( int i = 0 ; i < files.length ; i++ )
    {
      File fe = new File( sitesDir + "/" + files[ i ] ) ;
      if ( fe.isDirectory() ) sites.add( files[ i ] ) ;
    }
  readIgnoredIPs() ;
 }

 
/**
 * A constructor
 * @param configFileName configuration parameters file name
 */
 public LogProcessor( String configFileName ) throws Exception
 {
   cfg = ConfigList.newConfigList( configFileName ) ;
   lineCount = 0 ;
   dataDir = cfg.getDataDir() ;
   LOG.debug( "Data directory: " + dataDir ) ;
 }
   
/**
 * Generate a pair of public and private encryption keys
 */
 public void generateKeys() throws Exception
 {
   KeyPairGenerator kpg = KeyPairGenerator.getInstance( "RSA" ) ;
   kpg.initialize( 2048 ) ;
   KeyPair keyPair = kpg.generateKeyPair() ;
   PrivateKey privKey = keyPair.getPrivate() ;
   PublicKey pubKey = keyPair.getPublic() ;
   byte[] bytes = privKey.getEncoded() ;
   FileOutputStream fos = new FileOutputStream( fileOut + "/private.key" ) ;
   fos.write( bytes ) ;
   fos.close() ;
   bytes = pubKey.getEncoded() ;
   fos = new FileOutputStream( fileOut + "/public.key" ) ;
   fos.write( bytes ) ;
   fos.close() ;
 }
  
/**
 * Resets list of ignored (blacklisted) IP addresses
 */
 public void resetIgnoredIPs()
 {
   ignoredIPs.clear() ;
   File f = new File( ignoredIpsFile ) ;
   if ( f.exists() ) f.delete() ;
 }
   
/**
 * Reads list of ignored (blacklisted) IP addresses from a file
 */
 public void readIgnoredIPs() throws Exception
 {
  // read all log lines and look for IPs sending too frequent requests for text pages
  ignoredIPs.clear() ;
  File f = new File( ignoredIpsFile ) ;
  if ( f.exists() ) // read previously calculated ignored ips 
     {
       FileReader fr = new FileReader( f ) ;
       BufferedReader br = new BufferedReader( fr ) ;
       String ln ;
       while( (ln=br.readLine()) != null )
         {
           ln = ln.trim();
           if ( ln.length() == 0 ) continue ;
           if ( LOG.isTraceEnabled() )
        	LOG.trace("Ignored IP: " + ln) ;
           String arr[] = ln.split( " " ) ;
           if ( arr.length == 1 ) ignoredIPs.put( arr[0], "0" ) ;
                else ignoredIPs.put( arr[0], arr[1] ) ;
         }
       br.close();
         // return ;
     }
 }
  
/**
 * Saves list of ignored (blacklisted) IP addresses to a file
 */
 public void saveIgnoredIPs() throws Exception
 {  
  if ( ignoredIpsFile.length() != 0 )
   {
     FileOutputStream fo = new FileOutputStream( ignoredIpsFile, false ) ; 
     PrintStream ps = new PrintStream( fo ) ;
     for (Map.Entry<String, String> entry : ignoredIPs.entrySet())
     {  
       ps.println( entry.getKey() + " " + entry.getValue() ) ;
     }
     ps.close() ;
   }
 }
 
 public boolean isIgnoredIPsEmpty()
 {
   return ignoredIPs.isEmpty() ; 
 }
 
 public String ignoredIP( String ip )
 {
   return ignoredIPs.get( ip ) ;	 
 }
 
 public void ignoreIP( String ip, String level )
 {
   ignoredIPs.put( ip, level ) ;	 
 }
 
 /**
  * @return the textPages
  */
 public String[] getTextPages()
 {
 	return textPages;
 }

 /**
  * @return the ignoredIPs
  */
 public Map<String,String> getIgnoredIPs()
 {
 	return ignoredIPs;
 }

 /**
  * @return the maxUrlLength
  */
 public int getMaxUrlLength()
 {
   return maxUrlLength;
 }

 /**
  * @param maxUrlLength the maxUrlLength to set
  */
 public void setMaxUrlLength( int maxUrlLength )
 {
   this.maxUrlLength = maxUrlLength;
 }

 
}
