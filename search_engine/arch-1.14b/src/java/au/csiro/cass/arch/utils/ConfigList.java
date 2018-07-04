/*
 * 
 * Copyright (c) CSIRO Astronomy and Space Science
 * Author Arkadi.Kosmynin@csiro.au
 * 
 */
package au.csiro.cass.arch.utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.collections.MultiHashMap;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.io.WritableUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import au.csiro.cass.arch.auth.Permissions;
import au.csiro.cass.arch.auth.SearchProfile;

/** 
 * Configuration paramters holder
 * 
 * @author Arkadi Kosmynin
 *
 */
public class ConfigList implements Writable
{
	
  public static void main( String args[] )
  {
    try 
    {
      String json1 = properties2JSON( "C:\\_\\ArchProject\\test\\config - Copy.txt", false ) ;
      String json2 = XML2JSON( "C:\\_\\ArchProject\\test\\nutch-site - Copy.xml", false ) ;
      JSON2Properties( "C:\\_\\ArchProject\\test\\config - Copy.txt", json1 ) ;
      JSON2XML( "C:\\_\\ArchProject\\test\\nutch-site - Copy.xml", json2 ) ; 
    } catch( Exception e )
    {
      System.out.println(  e.getMessage() ) ;
      e.printStackTrace() ;
    }
  }
	
  public static final Logger LOG = LoggerFactory.getLogger( ConfigList.class ) ;
  private final static String KEY = ConfigList.class.getName();
/**
 * Attribute pair holder
 */

  public class AtPair 
  {
    public String k ;
    public String d ;
    public String desc ;
    public AtPair( String key, String data )
    {
      k = key ; d = data ;      
    }
    public AtPair( String key, String data, String description )
    {
      k = key ; d = data ; desc = description ;     
    }
  }

  public String        fileName ; // configuration file name
  public ArrayList<AtPair>   ar ; // list of parameters
//  public AtPair[]            ar ; // array for faster/simpler access
  public Map        permissions ; // permissions records 
  public Map           profiles ; // SearchProfile records 
  protected ConfigList   parent ; // parent to inherit values from 
  protected String      dataDir ; // directory where config file is
 
  
  /**
   * ConfigList factory
   * 
   * @param fname    file to read parameters from 
   * @param parent   optional parent to inherit parameters from
   * @throws Exception
   */
   static public ConfigList newConfigListXML( String fname )
   throws IOException 
   {
    ConfigList cfg = new ConfigList() ;
    cfg.parse( fname ) ;
    return cfg ;
   }
   
   /**
    * ConfigList factory
    * 
    * @param fname    file to read parameters from 
    * @param parent   optional parent to inherit parameters from
    * @throws Exception
    */
    static public ConfigList newConfigList( String fname, ConfigList parent )
    throws IOException 
    {
     ConfigList cfg = new ConfigList() ;
     cfg.read( fname, true ) ;
     cfg.parent = parent ;
     return cfg ;
    }
    
 /**
  * ConfigList factory for parentless object
  * 
  * @param fname    file to read parameters from 
  * @throws Exception
  */
  static public ConfigList newConfigList( String fname )
  throws IOException 
  { return newConfigList( fname, null ) ; }
  

  /**
   * ConfigList factory
   * 
   * @param in stream to deserialise from 
   * @throws Exception
   */
  static public ConfigList newConfigList( DataInput in ) throws IOException
  {
	ConfigList cfg = new ConfigList() ;
	cfg.readFields( in ) ;
	return cfg ;
  }
  
  static public ConfigList newConfigList() throws IOException { return new ConfigList() ; }

 /**
  * Default parameterless constructor
  * 
  * @throws IOException
  */
  public ConfigList() throws IOException
  {
   permissions = new HashMap() ;
   profiles = new HashMap() ;
   ar = new ArrayList<AtPair>();
  }
  
  
  /**
   * Reads config list contents from a text file
   * 
   * @param fname    file to read parameters from
   * @param fin finalise if true  
   * @throws Exception
   */
   public void read( String fname, boolean fin )
   throws IOException
   {
     FileReader fr = null ;
     BufferedReader br = null ;
     LOG.info( " Reading configuration from " + fname ) ;

     try
     {
       File f = new File( fname ) ;
       File parent = f.getParentFile() ;
       dataDir = parent == null ? "" : parent.getCanonicalPath() ;
       fileName = fname ;
       fr = new FileReader( fname ) ;
       br = new BufferedReader( fr ) ;
       String ln ;
       while( (ln = br.readLine()) != null  )
       {
         ln = ln.trim() ;
         if ( ln.length() == 0 ) continue ;
         int i = ln.indexOf( "=" ) ;
         if ( ln.startsWith( "#" ) || ln.startsWith( "//" ) )
         { 
           if ( LOG.isTraceEnabled( ) )
          	 LOG.trace( "Ignored: " + ln ) ;
           continue ;
         }
         if ( i < 0 )
           throw new IOException( "Bad line in " + fileName + " :" + ln ) ;
         try { 
               String k = ln.substring( 0, i-1 ).trim().toLowerCase() ;
               String d = ln.substring( i+1 ).trim() ;
               if ( LOG.isDebugEnabled() )
                    LOG.debug( k + " = " + d ) ;
               ar.add( new AtPair( k, d ) ) ; 
             } catch ( Exception e )
             {
         	  throw new IOException( "Bad line in " + fileName + " :" + ln ) ;	
             }
       }

       if ( fin ) finalise() ;
       
     } catch( IOException e )
     { 
       LOG.error( "Error in configuration list, exception " + e.getMessage() ) ;
     	throw e ;
     }
     finally { if ( br != null ) br.close() ;
               if ( fr != null ) fr.close() ;
             }
   }
   
   
   /**
    * Reads config list contents from a Hadoop XML Configuration file
    * @param fname    file to read parameters from 
    * @throws Exception
    */
    public void parse( String fname )
    throws IOException
    {
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
	  try
	  {
	    DocumentBuilder db = dbf.newDocumentBuilder();
	    Document d = db.parse( fname ) ;
	    Element root = d.getDocumentElement();
		NodeList nl = root.getElementsByTagName( "property" ) ;
		if ( nl != null && nl.getLength() > 0 )
		 for( int i = 0 ; i < nl.getLength() ; i++ )
		 {
		   Element el = (Element)nl.item( i ) ;
           String k = getTagVal( el, "name" ) ;
           String dat = getTagVal( el, "value" ) ;
           String desc = getTagVal( el, "description" ) ;
           ar.add( new AtPair( k, dat, desc ) ) ;
	     }
	   finalise() ;
	  } catch( Exception e )
	  {
		throw new IOException( e ) ;
	  }
	}	
    
    String getTagVal( Element e, String tag )
    {
	  NodeList nl = e.getElementsByTagName( tag ) ;
	  Node node ;
	  if ( nl == null || nl.getLength() == 0 ) return "" ;
      node = nl.item(0) ;
      if ( node == null ) return "" ;
      node = node.getFirstChild() ;
      if ( node == null ) return "" ;
      String val = node.getNodeValue() ;
      if ( val == null ) return "" ;
	  return val.trim();
	}
    
    
    
  /**
   * Called after finishing reading params into a list
   * Creates permissions and profiles objects
   */
   public void finalise() throws IOException 
   {
	 String[] pp = getAll( "permissions" ) ;
	 String url = get( "url", null, "SITE_URL" ) ;
	 if ( pp != null )
	    for ( int i = 0 ; i < pp.length ; i++ )
	             { 
	    	       if ( pp[ i ].contains( "${url}" ) && url != null )
	    	    	          pp[ i ] = pp[ i ].replace( "${url}", url ) ;
 	               Permissions p = Permissions.newPermissions( pp[ i ] ) ;
	    	       permissions.put( p.getFile(), p ) ;
	             }	
	 String[] cc = getAll( "frontend.profile" ) ;
	 if ( cc != null )
	    for ( int i = 0 ; i < cc.length ; i++ )
	             { 
 	               SearchProfile c = SearchProfile.newSearchProfile( cc[ i ] ) ;
	    	       profiles.put( c.getId(), c ) ;
	             }	
   }
   
   /**
    * Writes contents of a MultiMap to a file, preserving as much of the file as possible
    * @param file - name of the file to overwrite
    * @param conf - a configuration object in a MultiHashMap
    */
   public static void write( String file, MultiHashMap conf )
   throws IOException
   {
	 // read the file into an array of strings and re-open for writing
	 ArrayList<String> lines = new ArrayList<String>() ;
     File f = new File( file ) ;
     String ln ;
     if ( f.exists() )
     {
       FileReader fr = new FileReader( file ) ;
       BufferedReader br = new BufferedReader( fr ) ;
       while( (ln = br.readLine()) != null  ) lines.add( ln ) ;
       br.close() ; fr.close() ;
     }
     BufferedWriter out = new BufferedWriter( new FileWriter( file ) ) ;
     
     for ( String line : lines )
     {
       ln = line.trim() ;	 
       if ( ln.startsWith( "#" ) ) mergeLine( ln.substring( 1 ), out, conf, true ) ; 
        else if ( ln.startsWith( "//" ) ) mergeLine( ln.substring( 2 ), out, conf, true ) ;
        else if ( ln.length() == 0 ) { out.write( line ) ; out.newLine() ; } 
        else if ( !mergeLine( ln, out, conf, false ) ) 
        		throw new IOException( "Bad line in " + file + " :" + line ) ;
     } 
     // now write what's left in the map
     Collection<String> keys = conf.keySet() ;
     for ( String key : keys)
     {
       Collection<String> values = conf.getCollection( key ) ;
         if ( values != null )
            for ( String value : values )
                out.write( key + " = " + value + "\n" ) ;	 
     }
	 out.close() ;  
   }
   
   
   public static boolean mergeLine( String line, BufferedWriter out, MultiHashMap conf, boolean commentOut )
   throws IOException
   {
	 String ln = line.trim() ;  
	 int i = ln.indexOf( "=" ) ;
     if ( i <= 0 ) // did not find a key, value pair in the string
      if ( commentOut ) { out.write( "#" + line + "\n" ) ; return true ; }
        else return false ;
     String k = ln.substring( 0, i-1 ).trim() ;
     Collection<String> values = conf.getCollection( k ) ;
     if ( values == null ) // no values provided - comment out the line
        { /* out.write( "#" + line + "\n" ) ; */ return true ; }
     for ( String value : values )
              out.write( k + " = " + value + "\n" ) ;
       conf.remove( k ) ;
     return true ;
   }
  
    
   /**
    * Writes contents of a MultiMap to an XML Configuration file, preserving descriptions
    * @param file - name of the file to overwrite
    * @param conf - a configuration object in a MultiHashMap
    */
   public static void writeXML( String file, MultiHashMap conf )
   throws IOException
   {
	 // read the file into an array of strings and re-open for writing
     File f = new File( file ) ;
     ConfigList old = f.exists() ? newConfigListXML( file ) : null ;
     BufferedWriter out = new BufferedWriter( new FileWriter( file ) ) ;
     out.write( "<?xml version=\"1.0\"?>\n" +
                "<?xml-stylesheet type=\"text/xsl\" href=\"configuration.xsl\"?>\n" +
                "<configuration>\n" ) ;
     
     if ( old != null )
       for ( AtPair pair : old.ar )
        {
    	 Collection<String> values = conf.getCollection( pair.k ) ;
         if ( values != null )
            for ( String value : values )
                out.write( "\n<property>\n <name>" + pair.k + "</name>\n" +
                		   " <value>" + value + "</value>\n" +
                		   " <description>\n  " + pair.desc + "\n </description>\n</property>\n" ) ;
         conf.remove( pair.k ) ;
        } 
     // now write what's left in the map
     Collection<String> keys = conf.keySet() ;
     for ( String key : keys)
     {
       Collection<String> values = conf.getCollection( key ) ;
       if ( values != null )
         for ( String value : values )
             out.write( "\n<property>\n <name>\n" + key + "</name>\n" +
          		   " <value>" + value + "</value>\n" +
          		   " <description></description>\n</property>\n" ) ;
     }
     out.write( "</configuration>\n" ) ;
	 out.close() ;  
   }
  
   public MultiHashMap getMap()
   {
	 MultiHashMap map = new MultiHashMap() ;
	 AtPair[] aa = new AtPair[ ar.size() ] ;
	 ar.toArray( aa ) ;
	 String[] areas = this.getAll( "area" ) ;
	 if ( areas != null )
	   for ( String area : areas )
	     map.put( "ar_area_" + area, getMap( aa, area  ) ) ;
     for ( AtPair pair : ar )
    	 if ( pair.d != null ) map.put( pair.k, pair.d ) ;
     return map ;
   }

   public MultiHashMap getMap( AtPair[] ar, String area )
   {
	 MultiHashMap map = new MultiHashMap() ;
	 String suffix = "." + area.toLowerCase() ;
	 for ( AtPair pair : ar )
	   { 
		 if ( !pair.k.toLowerCase().endsWith( suffix ) ) continue ;
		 String key = pair.k.substring( 0, pair.k.length() - suffix.length() ) ;
		 map.put( key, pair.d ) ;
		 pair.d = null ;
	   }
	 return map ;
   }
   
	 
   /**
    * Encodes <name,value> pairs of  XML Configuration file into a JSON string
    * @param file - name of the file to read
    * @param censor - censor out sensitive attributes if true
    */
   static public String XML2JSON( String file, boolean censor ) throws IOException
   {
	 ConfigList cfg = ConfigList.newConfigListXML( file ) ;
	 return cfg.getJSON( censor ) ;
   } 
	 
	   
   /**
    * Encodes <name,value> pairs of  properties file into a JSON string
    * @param file - name of the file to overwrite
    * @param censor - censor out sensitive attributes if true
    */
   static public String properties2JSON( String file, boolean censor ) throws IOException
   {
	 ConfigList cfg = ConfigList.newConfigList( file ) ;
	 return cfg.getJSON( censor ) ;
   } 
	 
	   
   /**
    * Writes JSON encoded configuration to an XML file
    * @param file - name of the file to overwrite
    * @param json - name of the file to overwrite
    */
   static public void JSON2XML( String file, String json ) throws IOException
   {
     MultiHashMap map = JSON2Map( json );
     writeXML( file, map ) ;
   } 
	 
	   
   /**
    * Writes JSON encoded configuration to a properties file
    * @param file - name of the file to overwrite
    * @param json - JSON encoded configuration
    */
   static public void JSON2Properties( String file, String json ) throws IOException
   {
	 MultiHashMap map = JSON2Map( json );
     write( file, map ) ;
   } 
	   
   public String getJSON( boolean censor )
   {
     MultiHashMap map = getMap() ;
     if ( censor )
     {
       if ( map.containsKey( "target.db" ) )
    	 { map.remove( "target.db" ) ; map.put( "target.db", "default" ) ; }
     }
     return getJSON( map ).toString() ;
   }
     
   public JSONObject getJSON( MultiHashMap map )
   {
     Set<String> keys = map.keySet() ;
     JSONObject json = new JSONObject() ;

	 for ( String key : keys )
	 {
	  boolean isArea = key.startsWith( "ar_area_" ) ;
	  if ( isArea )
	     { json.put( key, getJSON( (MultiHashMap)((ArrayList)map.get( key ) ).get(0) ) ) ;
	       continue ;
	     }
	  Collection<String> values = map.getCollection( key ) ;
      JSONArray list = new JSONArray() ;
	  if ( values != null )
	    for ( String value : values )
	      { 
	    	if ( value.equalsIgnoreCase( "on" ) ) value = "true" ;
	    	else if ( value.equalsIgnoreCase( "off" ) ) value = "false" ;
	    	list.add( value ) ;
	      }
      json.put( key, list ) ;
	 }
	 return json ;
   }
   
   public static MultiHashMap JSON2Map( String json ) throws IOException
   {
     JSONParser parser = new JSONParser() ;
	 MultiHashMap map = new MultiHashMap() ;
	 JSONObject parsed = null ;
	 try { parsed = (JSONObject)parser.parse( json ) ; }
	 catch( ParseException e )
	 {
	   throw new IOException( e.getMessage() ) ;	 
	 }
	 Collection<String> keys = parsed.keySet() ;
	 for ( String key : keys )
	 {
	   Object value = parsed.get( key ) ;
	   if ( value instanceof String ) map.put( key, value ) ;
	   else if ( value instanceof JSONArray )
	   {
		 for ( Object val : (JSONArray)value )
		                               map.put( key, val ) ;
	   } else // must be a JSON object - encoded area parameters 
	   {
		 JSONObject parsed2 = (JSONObject)value ;
	     Collection<String> keys2 = parsed2.keySet() ;
		 for ( String key2 : keys2 )
		 {
		   Object value2 = parsed2.get( key2 ) ;
		   String areaKey = key2 + "." + key.substring( 8 ) ;
		   if ( value2 instanceof String ) map.put( areaKey, value2 ) ;
		   else if ( value2 instanceof JSONArray )
		   {
			 for ( Object val2 : (JSONArray)value2 )
			                               map.put( areaKey, val2 ) ;
		   }
	     }
	   }
	 }
	return map ;
/*
 		 JSONParser parser = new JSONParser( json ) ;
		 String key ;
		 MultiHashMap map = new MultiHashMap() ;
		 while( ( key = parser.getKey() ) != null )
		 {
		   String value ;
		   while ( ( value = parser.getValue() ) != null )
			{ 
			  if ( parser.isJSON )
			  {
			    JSONParser parser2 = new JSONParser( value ) ;
			    String key2 ;
				 while( ( key2 = parser2.getKey() ) != null )
				 {
				   String value2 ;
				   String areaKey = key2 + "." + key.substring( 8 ) ;
				   while ( ( value2 = parser2.getValue() ) != null )
					 map.put( areaKey, value2) ;
				 }
			  }  
			  else map.put( key, value ) ;
			}
		 }
		 return map ;
*/
   }
    
  /**
   * Clears contents 
   */
   void clear()
   {
	 ar.clear() ;
	 permissions.clear() ;
	 profiles.clear() ;
   }
  
  /**
   * Implements Writable readFields function
   * @param in   stream to read from 
   */
  public void readFields( DataInput in ) throws IOException
  {
	clear() ;
    int size = WritableUtils.readVInt( in ) ;
    int parentExists = WritableUtils.readVInt( in ) ;
	for( int i = 0 ; i < size ; i++ )
	{
      ar.add( new AtPair( 
		            org.apache.hadoop.io.Text.readString( in ), 
	                org.apache.hadoop.io.Text.readString( in ) ) ) ;
	}
	try
	{ 
 	  if ( parentExists != 0 ) 
	   {
	     parent = new ConfigList() ;
	     parent.readFields( in ) ;
	   }
	  finalise() ;
	} catch( Exception e )
	{
      throw new IOException( e.getMessage() ) ;		
	}
  }
  
  
  /**
   * Implements Writable readFields function
   * @param out   stream to write to 
   */
  public void write( DataOutput out ) throws IOException
  {
	WritableUtils.writeVInt( out, ar.size() ) ;
	int pp = parent == null ? 0 : 1 ;
	WritableUtils.writeVInt( out, pp ) ;	
	for ( int i = 0 ; i < ar.size() ; i++ )
	  { 
	    AtPair p = ar.get( i ) ;
		org.apache.hadoop.io.Text.writeString( out, p.k ) ; 
		org.apache.hadoop.io.Text.writeString( out, p.d ) ; 
	  }
	if ( parent != null )
		        parent.write( out ) ;
  }

  
  /**
   * Creates a clone of this ConfigList
   * @param finalise - finalise if true
   * @return a cloned ConfigList object, possibly, not finalised
   */
  public ConfigList clone( boolean finalise ) throws Exception
  {
    ConfigList cfg = new ConfigList() ;
    cfg.fileName = fileName ;
    cfg.dataDir = dataDir ;
    if ( parent != null )
         cfg.parent = parent.clone( finalise ) ;
    
    for ( AtPair pair : ar )
         cfg.ar.add( pair ) ;
    if ( finalise )
    	    cfg.finalise() ;
    return cfg ;
  }

  
 /**
  * Returns value of first attribute with given key
  * @param k    key to use for search
  * @return string value if found or null 
  */
  public String find( String k )
  {
    AtPair ap ;
    int i ;
    String kk = k.toLowerCase().trim() ;
    for( AtPair p : ar )
    { 
      if ( p.k.equals( kk ) )
//       System.out.println( ar[i].k + " : " + ar[i].d ) ; 
        return p.d ;
    }
    return null ;
  }
    
 /**
  * Returns number of attributes with given key
  * @param k    key to use for search
  * @return int number of attributes  
  */
  public int numberOf( String k )
  {
    AtPair ap ;
    int i, num = 0 ;
    String kk = k.toLowerCase().trim() ;
    for( AtPair p : ar ) if ( p.k.equals( kk ) ) num++ ;
    return num ;
  }
    
 /**
  * Get values of all attributes with given key
  * @param k    key to use for search
  * @return String[] values
  */
  public String[] getAll( String k )
  { return getAll( k, false ) ; }
  
 /**
  * Get values of all attributes with given key, optionally convert to lower case
  * @param k    key to use for search
  * @param tolower  if true convert to lower case
  * @return String[] values
  */
  public String[] getAll( String k, boolean tolower )
  {
    AtPair ap ;
    int i, num, j ;
    String kk = k.toLowerCase().trim() ;
    String[] array ;
    num = numberOf( kk ) ;
    if ( num == 0 ) return null ;
    array = new String[ num ] ;
    j = 0 ;
    for( AtPair p : ar )
     if ( p.k.equals( kk ) )
        { array[ j ] = p.d ; j++ ;
          if ( tolower ) array[ j - 1 ] = array[ j - 1 ].toLowerCase() ;
        }
    return array ;
  }

  
  /**
   * Get values of all attributes with given key. If they don't exist, get them
   * from the parent object. If no luck, try the system variable.
   * @param k    key to use for search
   * @return String[] values
   */
   public String[] getAllInherited( String k, String system )
   {
	 String ss = System.getenv( system ) ;
	 if ( ss != null ) return ss.split( "\\|" ) ;
	 String[] s = getAllInherited( k ) ;
	 return s ; 
   }
   
   
   /**
    * Get values of all attributes with given key. If they don't exist, try to
    * get them from the system variable.
    * @param k    key to use for search
    * @param system    system variable name
    * @return String[] values
    */
    public String[] getAll( String k, String system )
    {
      String ss = System.getenv( system ) ;
 	  if ( ss != null ) return ss.split( "\\|" ) ;
 	  String[] s = getAll( k ) ;
      return s ; 
    }
    
    
   /**
    * Get values of all attributes with given key. If they don't exist, get them
    * from the parent object.
    * @param k    key to use for search
    * @return String[] values
    */
    public String[] getAllInherited( String k )
    {
      String[] res = getAll( k ) ;
      if ( res == null && parent != null ) return parent.getAll( k ) ;
      else return res ;
    }
    
  /**
   * Get first String value of attribute with given key. If it does not exist,
   * return the default value.
   * @param k    key to use for search
   * @return String value
   */
   public String get( String k, String def ) 
   { 
     String s = find( k ) ;
     if ( s == null ) return def ;
     return s ;
   }

   /**
    * Get first boolean value of attribute with given key. If it does not exist,
    * return the default value.
    * @param k    key to use for search
    * @return String value
    */
    public boolean get( String k, boolean def ) 
    { 
      String s = find( k ) ;
      if ( s == null ) return def ;
      return s.equalsIgnoreCase( "on" ) ||  s.equalsIgnoreCase( "yes" ) ||
             s.equalsIgnoreCase( "y" ) || s.equals( "1" ) || s.equalsIgnoreCase( "true" ) ;
    }

 /**
  * Set value of first attribute with given key. If it does not exist,
  * create a new attribute-value pair.
  * @param k    key
  * @param d    value
  */
  public void set( String k, String d ) 
  { 
    String kk = k.toLowerCase().trim() ;
    for( AtPair p : ar )
     { 
       if ( p.k.equals( kk ) )
          {
            p.d = d ;
            return ;
          }
     }
    AtPair ap = new AtPair( k, d ) ;
    ar.add( ap ) ;
  }

 /**
  * Create and add a new attribute-value pair
  * @param k    key
  * @param d    value
  */
  public void add( String k, String d ) 
  { 
    AtPair ap ;
    ap = new AtPair( k, d ) ;
    ar.add( ap ) ;
  }

 /**
  * Create and add several new attribute-value pairs with same k
  * @param k    key
  * @param d    array of values
  */
  public void add( String k, String[] d ) 
  { 
    AtPair ap ;
    for ( int i = 0 ; i < d.length ; i++ )
     { 
       ap = new AtPair( k, d[i] ) ;
       ar.add( ap ) ;
     }
  }

  /**
   * Get value of first attribute with given key. If it does not exist, get it
   * from the system variable. If failed, return the default value.
   * @param k    key to use for search
   * @param def  default value 
   * @return String value
   */
   public String get( String k, String def, String system )
   {
	 String s = getPreferred( k, false, system ) ;
     if ( s != null ) return s ; else return def ;
   }
   
   
   /**
    * Get value of first attribute with given key. If it does not exist, get it
    * from the parent object. If it does not exist there, try the system variable.
    * If failed, return the default value.
    * @param k    key to use for search
    * @param def  default value 
    * @return String value
    */
    public String getInherited( String k, String def, String system )
    {
 	 String s = getPreferred( k, true, system ) ;
      if ( s != null ) return s ; else return def ;
    }
    
    
   /**
    * Get value of first attribute with given key. If it does not exist, get it
    * from the parent object. If it does not exist there, return the default value.
    * @param k    key to use for search
    * @param def  default value 
    * @return String value
    */
    public String getInherited( String k, String def )
    {
      String s = find( k ) ;
      if ( s == null && parent != null ) s = parent.find( k ) ;
      if ( s != null ) return s ; else return def ;
    }
    
    
  /**
   * Gets value of system variable with given name. If it does not exist, 
   * gets the value of first attribute with given key or the default value.
   * 
   * @param k    key to use for search
   * @param def  default value 
   * @param system  name of system environment variable
    * @return boolean value
    */
    public boolean get( String k, boolean def, String system )
    {  
      String s = getPreferred( k, false, system ) ;
      if ( s.equalsIgnoreCase( "no" ) ||
              s.equalsIgnoreCase( "off" ) ||
              s.equals( "0" ) ||
              s.equalsIgnoreCase( "false" ) ) def = false ;
          else def = true ;
      return def ;
    }
  

   /**
    * Gets value of system variable with given name. If it does not exist, 
    * gets the value of first attribute with given key. If it does not exist, get it
    * from the parent object. If it does not exist there, returns the default value.
    * 
    * @param k    key to use for search
    * @param def  default value 
    * @param system  name of system environment variable
    * @return boolean value
    */
    public boolean getInherited( String k, boolean def, String system )
    {  
      String s = getPreferred( k, true, system ) ;
      if ( s.equalsIgnoreCase( "no" ) ||
              s.equalsIgnoreCase( "off" ) ||
              s.equals( "0" ) ||
              s.equalsIgnoreCase( "false" ) ) def = false ;
          else def = true ;
      return def ;
    }
  
  
 /**
  * Get boolean value of first attribute with given key. If it does not exist, get it
  * from the parent object. If it does not exist there, return the default value.
  * Value is false if one of {"no", "0", "off", "false"}, else true.
  * 
  * @param k    key to use for search
  * @param def  default value 
  * @return boolean value
  */
  public boolean getInherited( String k, boolean def )
  {
    String s = find( k ) ;
    if ( s == null && parent != null ) s = parent.find( k ) ;
    if ( s != null ) 
      { if ( s.equalsIgnoreCase( "no" ) ||
             s.equalsIgnoreCase( "off" ) ||
             s.equals( "0" ) ||
             s.equalsIgnoreCase( "false" ) ) def = false ;
         else def = true ; 
      }
    return def ;
  }

  
  /**
   * Gets value of system variable with given name. If it does not exist, 
   * gets the value of first attribute with given key or the default value.
   * 
   * @param k    key to use for search
   * @param def  default value 
   * @param system  name of system environment variable
   * @return int value
   */
   public int get( String k, int def, String system )
   {  
     int i ;
     String s = getPreferred( k, false, system ) ;
     try { i = Integer.parseInt( s ) ; } 
       catch( NumberFormatException e )
            { return get( k, def ) ; }       
     return i ;
   }


   /**
    * Gets value of system variable with given name. If it does not exist, 
    * gets the value of first attribute with given key. If it does not exist, get it
    * from the parent object. If it does not exist there, returns the default value.
    * 
    * @param k    key to use for search
    * @param def  default value 
    * @param system  name of system environment variable
    * @return int value
    */
    public int getInherited( String k, int def, String system )
    {  
      int i ;
      String s = getPreferred( k, true, system ) ;
      try { i = Integer.parseInt( s ) ; } 
        catch( NumberFormatException e )
             { return getInherited( k, def ) ; }       
      return i ;
    }

  
 /**
  * Get int value of first attribute with given key. If it does not exist,
  * return the default value.
  * 
  * @param k    key to use for search
  * @param def  default value 
  * @return int value
  */
  public int get( String k, int def ) 
  { 
    int i ;
    String s = find( k ) ;
    if ( s == null ) return def ;
    try { i = Integer.parseInt( s ) ; } 
      catch(NumberFormatException e) { return def ; } 
    return i ;
  }

 /**
  * Get int value of first attribute with given key. If it does not exist, get it
  * from the parent object. If it does not exist there, return the default value.
  * 
  * @param k    key to use for search
  * @param def  default value 
  * @return int value
  */
  public int getInherited( String k, int def )
  {
    int i ;
    String s = find( k ) ;
    if ( s == null && parent != null ) s = parent.find( k ) ;
    if ( s == null ) return def ; 
    try { i = Integer.parseInt( s ) ; } 
      catch( NumberFormatException e ) { return def ; }       
    return i ;
  }

  
  /**
   * Gets value of system variable with given name. If it does not exist, 
   * gets the value of first attribute with given key or the default value.
   * 
   * @param k    key to use for search
   * @param def  default value 
   * @param system  name of system environment variable
   * @return long value
   */
   public long get( String k, long def, String system )
   {  
     long i ;
     String s = getPreferred( k, false, system ) ;
     try { i = Long.parseLong( s ) ; } 
       catch( NumberFormatException e )
            { return get( k, def ) ; }       
     return i ;
   }


   /**
    * Gets value of system variable with given name. If it does not exist, 
    * gets the value of first attribute with given key. If it does not exist, get it
    * from the parent object. If it does not exist there, returns the default value.
    * 
    * @param k    key to use for search
    * @param def  default value 
    * @param system  name of system environment variable
    * @return long value
    */
    public long getInherited( String k, long def, String system )
    {  
      long i ;
      String s = getPreferred( k, true, system ) ;
      try { i = Long.parseLong( s ) ; } 
        catch( NumberFormatException e )
             { return getInherited( k, def ) ; }       
      return i ;
    }


  
 /**
  * Get long value of first attribute with given key. If it does not exist,
  * return the default value.
  * 
  * @param k    key to use for search
  * @param def  default value 
  * @return long value
  */
  public long get( String k, long def ) 
  { 
    long i ;
    String s = find( k ) ;
    if ( s == null ) return def ;
    try { i = Long.parseLong( s ) ; } 
      catch(NumberFormatException e) { return def ; } 
    return i ;
  }

 /**
  * Get long value of first attribute with given key. If it does not exist, get it
  * from the parent object. If it does not exist there, return the default value.
  * 
  * @param k    key to use for search
  * @param def  default value 
  * @return long value
  */
  public long getInherited( String k, long def )
  {
    long i ;
    String s = find( k ) ;
    if ( s == null && parent != null ) s = parent.find( k ) ;
    if ( s == null ) return def ; 
    try { i = Long.parseLong( s ) ; } 
      catch( NumberFormatException e ) { return def ; }       
    return i ;
  }
  

  /**
   * Gets value of system variable with given name. If it does not exist, 
   * gets float value of first attribute with given key or the default value.
   * 
   * @param k    key to use for search
   * @param def  default value 
   * @param system  name of system environment variable
   * @return float value
   */
   public float get( String k, float def, String system )
   {  
     float i ;
     String s = getPreferred( k, false, system ) ;
     try { i = Float.parseFloat( s ) ; } 
       catch( NumberFormatException e )
            { return get( k, def ) ; }       
     return i ;
   }


   /**
    * Gets value of system variable with given name. If it does not exist, 
    * gets float value of first attribute with given key. If it does not exist, get it
    * from the parent object. If it does not exist there, returns the default value.
    * 
    * @param k    key to use for search
    * @param def  default value 
    * @param system  name of system environment variable
    * @return float value
    */
    public float getInherited( String k, float def, String system )
    {  
      float i ;
      String s = getPreferred( k, true, system ) ;
      try { i = Float.parseFloat( s ) ; } 
        catch( NumberFormatException e )
             { return getInherited( k, def ) ; }       
      return i ;
    }

    
    String getPreferred( String k, boolean inherited, String system )
    {
      String value = find( k ) ;
      String sysValue = System.getenv( system ) ;
      String parValue = null ;
      if ( value == null && sysValue == null && inherited && parent != null )
    	                                        parValue = parent.find( k ) ;
      if ( sysValue != null && inherited ) value = sysValue ;
      if ( value == null && inherited ) value = parValue ;
      return value ;
    }
   
    /**
     * Get float value of first attribute with given key. If it does not exist,
     * return the default value.
     * 
     * @param k    key to use for search
     * @param def  default value 
     * @return float value
     */
    public float get( String k, float def ) 
    { 
      float i ;
      String s = find( k ) ;
      if ( s == null ) return def ;
      try { i = Float.parseFloat( s ) ; } 
        catch(NumberFormatException e) { return def ; } 
      return i ;
    }

    
   /**
    * Get float value of first attribute with given key. If it does not exist, get it
    * from the parent object. If it does not exist there, return the default value.
    * 
    * @param k    key to use for search
    * @param def  default value 
    * @return float value
    */
    public float getInherited( String k, float def )
    {
      float i ;
      String s = find( k ) ;
      if ( s == null && parent != null ) s = parent.find( k ) ;
      if ( s == null ) return def ; 
      try { i = Float.parseFloat( s ) ; } 
        catch( NumberFormatException e ) { return def ; }       
      return i ;
    }

    
 /**
  * Get int values of all attributes with given key. 
  * 
  * @param k    key to use for search
  * @return array of int values
  */
  public int[] getAllInt( String k ) 
  { 
    int[] i ;
    int j ;
    String[] s = getAll( k ) ;
    if ( s == null ) return null ;
    i = new int[ s.length ] ;
    try { 
         for ( j = 0 ; j < s.length ; j++ )
           { i[j] = Integer.parseInt( s[j] ) ; }
        } 
      catch(NumberFormatException e) { return null ; } 
    return i ;
  }

 /**
  * Get int values of all attributes with given key. If they don't not exist, get
  * them from the parent object. 
  * 
  * @param k    key to use for search
  * @return array of int values
  */
  public int[] getAllIntInherited( String k ) 
  { 
    int[] i ;
    int j ;
    String[] s = getAll( k ) ;
    if ( s == null ) s = parent.getAll( k ) ;
    if ( s == null ) return null ;
    i = new int[ s.length ] ;
    try { 
         for ( j = 0 ; j < s.length ; j++ )
           { i[j] = Integer.parseInt( s[j] ) ; }
        } 
      catch(NumberFormatException e) { return null ; } 
    return i ;
  }

 /**
  * Get long values of all attributes with given key. 
  * 
  * @param k    key to use for search
  * @return array of long values
  */
  public long[] getAllLong( String k ) 
  { 
    long[] i ;
    int j ;
    String[] s = getAll( k ) ;
    i = new long[ s.length ] ;
    try { 
         for ( j = 0 ; j < s.length ; j++ )
           { i[j] = Long.parseLong( s[j] ) ; }
        } 
      catch(NumberFormatException e) { return null ; } 
    return i ;
  }

 /**
  * Get long values of all attributes with given key. If they don't not exist, get
  * them from the parent object. 
  * 
  * @param k    key to use for search
  * @return array of long values
  */
  public long[] getAllLongInherited( String k ) 
  { 
    long[] i ;
    int j ;
    String[] s = getAll( k ) ;
    if ( s == null ) s = parent.getAll( k ) ;
    if ( s == null ) return null ;
    i = new long[ s.length ] ;
    try { 
         for ( j = 0 ; j < s.length ; j++ )
           { i[j] = Long.parseLong( s[j] ) ; }
        } 
      catch(NumberFormatException e) { return null ; } 
    return i ;
  }

 /**
  * Get float values of all attributes with given key. 
  * 
  * @param k    key to use for search
  * @return array of float values
  */
  public float[] getAllFloat( String k ) 
  { 
    float[] i ;
    int j ;
    String[] s = getAll( k ) ;
    i = new float[ s.length ] ;
    try { 
         for ( j = 0 ; j < s.length ; j++ )
           { i[j] = Float.parseFloat( s[j] ) ; }
        } 
      catch(NumberFormatException e) { return null ; } 
    return i ;
  }  
  
 /**
  * Get float values of all attributes with given key. If they don't not exist, get
  * them from the parent object. 
  * 
  * @param k    key to use for search
  * @return array of float values
  */
  public float[] getAllFloatInherited( String k ) 
  { 
    float[] i ;
    int j ;
    String[] s = getAll( k ) ;
    if ( s == null ) s = parent.getAll( k ) ;
    if ( s == null ) return null ;
    i = new float[ s.length ] ;
    try { 
         for ( j = 0 ; j < s.length ; j++ )
           { i[j] = Float.parseFloat( s[j] ) ; }
        } 
      catch(NumberFormatException e) { return null ; } 
    return i ;
  }
  
  public String toString()
  {
   StringBuffer buf = new StringBuffer() ;
   
   for( AtPair p : ar )
	{ buf.append( p.k ) ; buf.append( " = " ) ; buf.append( p.d ) ; buf.append( "\n" ) ; } 
   return buf.toString() ;
  }
  
  /**
 * @return the dataDir
 */
public String getDataDir() {
	return dataDir;
}

/**
 * @param dataDir the dataDir to set
 */
public void setDataDir(String dataDir) {
	this.dataDir = dataDir;
}

/**
 * Get SearchProfile object for given id 
 * 
 * @param id    id
 * @param password    password
 * @return SearchProfile object or null
 */  
public SearchProfile getSearchProfile( String id, String password )
{
  if ( id == null || id.equals( "guest" ) ) return SearchProfile.newGuestSearchProfile() ;
	SearchProfile pr = (SearchProfile)profiles.get( id ) ;
  if ( password == null || !pr.getPassword().equals( password ) ) return null ;
	return pr ;
}

/**
 * Get permissions record for given url 
 * 
 * @param url    url to use for search
 * @return Permissions object or null
 */  
public Permissions getPermissions( String url )
{
	return (Permissions)permissions.get( url ) ;
}

/**
 * Get all permissions records
 * 
 * @return array of Permissions objects
 */  
public Permissions[] getPermissions()
{
	if ( permissions.size() == 0 ) return null ; 
	Permissions[] pp = new Permissions[ permissions.size() ] ;
	Iterator it = permissions.values().iterator() ;
	int i = 0 ;
	while( it.hasNext() )
	{
   pp[ i++ ] = (Permissions)it.next() ;
	}
	return pp ;
}
  
  public static ConfigList getConfig( ServletContext application, String file, String parent )
  throws Exception
  {
	//   
	HashMap cache = (HashMap)application.getAttribute( KEY ) ;
	if ( cache == null )
	{
	  cache = new HashMap() ;
      application.setAttribute( KEY, cache ) ;
	}
	String key = file ; 
	if ( parent != null ) key = file + "|" + parent ;
	ConfigList list = (ConfigList)cache.get( key ) ;
	if ( list == null )
	{
	  ConfigList parentList = null ;
	  if ( parent != null )
	   {
		 parentList = (ConfigList)cache.get( parent ) ;
		 if ( parentList == null )
			 { parentList = ConfigList.newConfigList( parent, null ) ;
		       cache.put( parent, parentList ) ;
			 }
	   }
	  list = ConfigList.newConfigList( file, parentList ) ;
	  cache.put( key, list ) ;
	}
	return list ;
  }
  
 
  public String getFileName()
  {
	return fileName;
  }
  
  public String getParentFileName()
  {
	if ( parent != null ) return parent.fileName ;
	else return null ;
  }

  public ConfigList getParent()
  {
	return parent;
  }
  
  
/*  
  // A very simple, quick and dirty JSON parser for parsing JSON
  // strings in form { "key1" : [ "value1", "value2", ... ] ... }
  // All keys and values must be UTF-8 strings.
  public static class JSONParser
  {
	String       src ; // parse source
	int            i ; // parsing position
	boolean  escaped ; // true if the last seen character is '\'
	boolean inquotes ; // true if the current character is inside "" ;
	boolean noquotes ; // true if the current body element is not enquoted ;
	boolean  isJSON ;
	int          end ;
	StringBuffer buf ;
	
	public JSONParser( String source )
	{ 
	  src = source ; i = -1 ; escaped = false ; inquotes = false ;
	  end = source.length() ;
	}
	
	public String getKey() throws IOException
	{ return getQuotedBody( true ) ; }
	
	public String getValue() throws IOException
	{ return getQuotedBody( false ) ; }
	
	private String getQuotedBody( boolean key ) throws IOException
	{
	  // skip everything until reach '"'
	  i++ ; buf = new StringBuffer() ; 
	  if ( i >= end ) return null ; 
	  if ( isJSON ) { isJSON = false ; return null ; } // only one nested object allowed
	  char c = ' ' ; noquotes = false ;
	  while( i < end && (c = src.charAt( i )) != '\"' && !noquotes )
	  {
	    switch( c )
	    {
	      case ' ' : case '\r' : case '\n' : case '\t' : case ',' : break ; // allowed, do nothing
	      case '{' : if ( key ) if ( buf.length() == 0 ) break ; else
	    	                    throw new IOException( " Unexpected { at " + i ) ;
	                      else { isJSON = true ; break ; }      
	      case '}' : if ( key ) return null ; else
	                  throw new IOException( " Unexpected } at pos " + i + " of " + src ) ;
	      case ']' : if ( !key ) return null ; else
	                  throw new IOException( " Unexpected ] at pos " + i + " of " + src ) ;
	      case ':' : case '[' : if ( !key ) break ; else
              throw new IOException( " Unexpected " + c + " at pos " + i + " of " + src ) ;
	      case '"' : break ;
	      default: // found something that is not started with quotes
	    	       if ( !key ) { noquotes = true ; i-=2 ; }
	    }
	    i++ ;
	  }
	  // found starting " or {, get the body until next unescaped " or }.
	  // if noquotes - take everything until \,,} or ].
	  
	  if ( !isJSON ) 
	  {  
        i++ ;
	    do
	     { 
   	      if ( i >= end ) throw new IOException( "Key or data is not terminated properly." ) ;
   	      c = src.charAt( i ) ;
   	      switch( c )
   	      {
   	       case '\r' : case '\n': case '\t' : if ( !key ) break ; else // allow only in values
   	         throw new IOException( " Invalid character in key at position " + i + " of string " + src ) ;
   	       case '\\' : if ( !escaped ) escaped = true ; else buf.append( '\\' ) ; break ;
   	       case '\"' : if ( escaped ) { buf.append( '\"' ) ; escaped = false ; break ; }
   	                     else if ( !noquotes ) return buf.toString() ;
   	       case ',' : case ']': case '}': if ( noquotes ) { i-- ; return buf.toString() ; }
   	       default: buf.append( src.charAt( i  ) ) ;
   	      }
   	      i++ ;
         } while( true ) ;
	  } else // else this is JSON encoded object. Copy everything until next unenquoted }.
	  {  
		i-- ;
		do
		 { 
	   	  if ( i >= end ) throw new IOException( "Key or data is not terminated properly." ) ;
	   	  c = src.charAt( i ) ;
	   	  switch( c )
	   	  {
	   	    case '\\' : if ( !escaped ) escaped = true ; else buf.append( c ) ; break ;
	   	    case '\"' : buf.append( c ) ; inquotes = !inquotes ; break ;
	   	    case '}'  : buf.append( c ) ; if ( !inquotes ) return buf.toString() ; break ; 
	   	    default: buf.append( c ) ;
	   	  }
	   	  i++ ;
	     } while( true ) ;
	  }
    }
  } 
  */
}
