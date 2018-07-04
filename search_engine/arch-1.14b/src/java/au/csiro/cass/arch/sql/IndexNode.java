/*
 * 
 * Copyright (c) CSIRO Astronomy and Space Science
 * Author Arkadi.Kosmynin@csiro.au
 * 
 */
package au.csiro.cass.arch.sql;

import java.util.HashSet;

import au.csiro.cass.arch.utils.Utils;

/**
 * A site tree node class.
 * 
 * Used to read nodes from and write them to the db.
 * 
 * @author Arkadi Kosmynin
 *
 */

public class IndexNode
{  
 public int         id ; // node id
 public String    name ; // node name: a directory or file name
 public String   label ; // text shown in the directory, normally, the name
 public String   title ; // html title
 public String    path ; // path from the site root to this node
 public String    type ; // 'd' - directory, 'f' - file (terminal node)
 public String  access ; // 'i' - inherited from parent node, 's' - standard
 public String  groupr ; // names of groups having r/o access 
 public String  groupw ; // names of groups having r/w access 
 public String   userr ; // names of users having r/o access 
 public String   userw ; // names of users having r/w access 
 public String  owners ; // names of users having admin access
 public int   parentId ; // id of the parent node
 public int     nextId ; // id of the next node on the level
 public int    fetched ; // reserved
 public int     errors ; // reserved
 public long    cached ; // reserved
 public float   weight ; // current URL weight
 public int      score ; // URL hit score
 public String  status ; // reserved
 
 // added for security scanning
 public int    hasForm ; // 1 if page has a form in it
 public int  hasScript ; // 1 if page has a script in it
 public long       CRC ; // CRC code of page output
 public long    srcCRC ; // CRC code of page source

 // added for alias handling
 public int    aliasof ; // 0 if not in alias group, or id of main alias
 
 // not stored in db
 public String    site ; // r/o name of site this node belongs to
 public String    base ; // base url
 public boolean   read ; // set to true if this node is visible to user
 public boolean  write ; // set to true if user can change the node
 public boolean  admin ; // set to true if user has administrator access
 private boolean isSet ; // set to true if read/write/admin fields are set
 String[]      grouprA ; // names of groups having r/o access 
 String[]      groupwA ; // names of groups having r/w access 
 String[]       userrA ; // names of users having r/o access 
 String[]       userwA ; // names of users having r/w access 
 String[]      ownersA ; // names of users having admin access
 
 /**
 * Default constructor 
 */
 public IndexNode() { weight = -1 ; } 
     

 /**
 * A constructor
 * 
 * @param path  path from site root to this node
 * @param name  file or directory name
 * @param type  'd' - for directory, 'f' - for file
 */
 public IndexNode( String path, String name, String type )
 {
  id = parentId = aliasof = 0 ;
  this.path = path ;
  access = "i" ;
  this.type = type ;
  label = name = title = path = access = groupr = groupw = userr = userw = owners = "" ;
  cached = 0 ; 
  userr = "admin" ;
  weight = -1 ;
  status = "l" ;
 }

 /**
  * IndexNode factory for a site root node
  * 
  */
 public static IndexNode newRootIndexNode()
 {
  IndexNode n = new IndexNode() ;
  n.id = 0 ;
  n.name = "" ;
  n.label = "/" ;
  n.title = "Root node" ;
  n.path = "" ;
  n.type = "d" ; // a directory
  n.access = "s" ; // standard
  n.groupr = "public" ; // open to public by default
  n.groupw = null ;
  n.userr = "guest" ;
  n.userw = null ;
  n.owners = "admin" ;
  n.parentId = 0 ;
  n.nextId = 0 ;
  n.fetched = 0 ;
  n.errors = 0 ;
  n.cached = 0 ;
  n.weight = -1f ;
  n.score = 0 ;
  n.status = "l" ;
  n.hasScript = 0 ;
  n.hasForm = 0 ;
  n.CRC = 0 ;
  n.srcCRC = 0 ;
  n.aliasof = 0 ;
  return n ;
 }

 /**
  * IndexNode factory
  * 
  * @param path  path from site root to this node
  * @param name  file or directory name
  * @param type  'd' - for directory, 'f' - for file
  */
 public static IndexNode newIndexNode( String path, String name, String type )
 {
  IndexNode n = newRootIndexNode() ;
  n.id = n.aliasof = 0 ;
  n.name = name ;
  n.label = null ;
  n.title = null ;
  n.path = path ;
  n.type = type ; // a directory
  n.access = "i" ; // inherited
  n.groupr = null ; // open to public by default
  n.owners = null ;
  return n ;
 }
 
 /**
  * Serialises to String. Fields are separated by '>'.
  * 
  * @return String result of serialization
  */
 public String toString()
 {
  StringBuilder buf = new StringBuilder() ;
  buf.append( id ) ; 
  buf.append( '>' ) ; buf.append( Utils.htmlEncode( name ) ) ; 
  buf.append( '>' ) ; buf.append( Utils.htmlEncode( label ) ) ; 
  buf.append( '>' ) ; buf.append( Utils.htmlEncode( title ) ) ; 
  buf.append( '>' ) ; buf.append( Utils.htmlEncode( path ) ) ; 
  buf.append( '>' ) ; buf.append( type ) ; 
  buf.append( '>' ) ; buf.append( access ) ; 
  buf.append( '>' ) ; if ( !isSet || write || admin ) buf.append( Utils.htmlEncode( groupr ) ) ;
                                                 else buf.append( "-" ) ;
  buf.append( '>' ) ; if ( !isSet || write || admin ) buf.append( Utils.htmlEncode( groupw ) ) ;  
                                                 else buf.append( "-" ) ;
  buf.append( '>' ) ; if ( !isSet || write || admin ) buf.append( Utils.htmlEncode( userr ) ) ;
                                                 else buf.append( "-" ) ;
  buf.append( '>' ) ; if ( !isSet || write || admin ) buf.append( Utils.htmlEncode( userw ) ) ;
                                                 else buf.append( "-" ) ;
  buf.append( '>' ) ; if ( !isSet || write || admin ) buf.append( Utils.htmlEncode( owners ) ) ;
                                                 else buf.append( "-" ) ;
  buf.append( '>' ) ; buf.append( parentId ) ;
  buf.append( '>' ) ; buf.append( nextId ) ;
  buf.append( '>' ) ; buf.append( fetched ) ;
  buf.append( '>' ) ; buf.append( errors ) ; 
  buf.append( '>' ) ; buf.append( cached ) ;
  buf.append( '>' ) ; buf.append( weight ) ;
  buf.append( '>' ) ; buf.append( score ) ;
  buf.append( '>' ) ; buf.append( status ) ;
  buf.append( '>' ) ; buf.append( Utils.htmlEncode( site ) ) ; 
  buf.append( '>' ) ; buf.append( Utils.htmlEncode( base ) ) ; 
  buf.append( '>' ) ; buf.append( read ? "t" : "f" ) ;
  buf.append( '>' ) ; buf.append( write ? "t" : "f" ) ;
  buf.append( '>' ) ; buf.append( admin ? "t" : "f" ) ;
  buf.append( '>' ) ; buf.append( isSet ? "t" : "f" ) ;  
  buf.append( '>' ) ; buf.append( hasForm ) ;
  buf.append( '>' ) ; buf.append( hasScript ) ;
  buf.append( '>' ) ; buf.append( CRC ) ;
  buf.append( '>' ) ; buf.append( srcCRC ) ;
  buf.append( '>' ) ; buf.append( aliasof ) ;
  
  return buf.toString() ; 
 }

 /**
  * Serialises array of nodes to String. Nodes are separated by '<'.
  * 
  * @return Array of nodes
  */
 static public String toString( IndexNode[] arr )
 {
  StringBuilder buf = new StringBuilder() ;
  for ( int i = 0 ; i < arr.length ; i++ )
   {
    if ( arr[ i ] == null ) continue ;
    if ( buf.length() > 0 ) buf.append( '<' ) ;
    buf.append( arr[ i ].toString() ) ;
   }
  if ( buf.length() > 0 ) return buf.toString() ;
  else return null ;
 }
 
 /**
  * Deserialises from String.
  * 
  * @param str - serialised object
  * 
  * @return IndexNode object
  */
  public static IndexNode newIndexNode( String str )
 {
  IndexNode n = newRootIndexNode() ;
  String[] arr = str.split( ">" ) ;
  n.id = Integer.parseInt( arr[ 0 ] ) ;
  n.name = Utils.htmlDecode( arr[ 1 ] ).trim() ; 
  n.label = Utils.htmlDecode( arr[ 2 ] ).trim() ;
  if ( n.label.length() == 0 ) n.label = null ; 
  n.title = Utils.htmlDecode( arr[ 3 ] ).trim() ;
  if ( n.title.length() == 0 ) n.title = null ; 
  n.path = Utils.htmlDecode( arr[ 4 ] ).trim() ; 
  n.type = arr[ 5 ] ; 
  n.access = arr[ 6 ].trim() ; 
  n.groupr = Utils.htmlDecode( arr[ 7 ] ).trim() ;
  if ( n.groupr.length() == 0 ) n.groupr = null ; 
  n.groupw = Utils.htmlDecode( arr[ 8 ] ).trim() ; 
  if ( n.groupw.length() == 0 ) n.groupw = null ; 
  n.userr = Utils.htmlDecode( arr[ 9 ] ).trim() ; 
  if ( n.userr.length() == 0 ) n.userr = null ; 
  n.userw = Utils.htmlDecode( arr[ 10 ] ).trim() ; 
  if ( n.userw.length() == 0 ) n.userw = null ; 
  n.owners = Utils.htmlDecode( arr[ 11 ] ).trim() ; 
  if ( n.owners.length() == 0 ) n.owners = null ; 
  n.parentId = Integer.parseInt( arr[ 12 ] ) ;
  n.nextId = Integer.parseInt( arr[ 13 ] ) ;
  n.fetched = Integer.parseInt( arr[ 14 ] ) ;
  n.errors = Integer.parseInt( arr[ 15 ] ) ;
  n.cached = Long.parseLong( arr[ 16 ] ) ;
  n.weight = Float.parseFloat( arr[ 17 ] ) ;
  n.score = Integer.parseInt( arr[ 18 ] ) ;
  n.status = arr[ 19 ] ;
  n.site = Utils.htmlDecode( arr[ 20 ] ).trim() ; 
  n.base = Utils.htmlDecode( arr[ 21 ] ).trim() ; 
  n.read = arr[ 22 ].charAt(0) == 't' ;
  n.write = arr[ 23 ].charAt(0) == 't' ;
  n.admin = arr[ 24 ].charAt(0) == 't' ;
  n.isSet = arr[ 25 ].charAt(0) == 't' ;  
  n.hasForm = Integer.parseInt( arr[ 26 ] ) ;
  n.hasScript = Integer.parseInt( arr[ 27 ] ) ;
  n.CRC = Integer.parseInt( arr[ 28 ] ) ;
  n.srcCRC = Integer.parseInt( arr[ 29 ] ) ;
  n.aliasof = Integer.parseInt( arr[ 30 ] ) ;
  
  return n ;
 }

 /**
  * Deserialises arry of IndexNode objects from String.
  * 
  * @param str - serialised array
  * 
  * @return array of IndexNode objects
  */
 public static IndexNode[] newIndexNodeArray( String str )
 {
  String[] arr = str.split( "<" ) ;
  IndexNode[] nodes = new IndexNode[ arr.length ] ;
  for ( int i = 0 ; i < arr.length ; i++ )
   { 
     nodes[ i ] = newIndexNode( arr[ i ] ) ;
   }
  return nodes ;
 }
 

 // Getters and setters below
 
 public long getCached()
 {
  return cached;
 }

 public void setCached( long cached )
 {
  this.cached = cached;
 }

 public String getGroupr()
 {
   return Utils.notNull( groupr ) ;
 }

     public void setGroupr( String groupr )
      {
       this.groupr = groupr;
      }

     public int getId()
      {
       return id;
      }

     public void setId( int id )
      {
       this.id = id;
      }

     public int getParentId()
      {
       return parentId;
      }

     public void setParentId( int parentId )
      {
       this.parentId = parentId;
      }

     public String getAccess()
      {
       return Utils.notNull( access ) ;
      }

     public void setAccess( String access )
      {
       this.access = access;
      }

    /**
	 * @return the aliasof
	 */
	public int getAliasof() {
		return aliasof;
	}


	/**
	 * @param aliasof the aliasof to set
	 */
	public void setAliasof(int aliasof) {
		this.aliasof = aliasof;
	}


	public String getPath()
      {
       return path;
      }

     public void setPath( String path )
      {
       this.path = path;
      }

     public String getLabel()
      {
       return label;
      }

     public void setLabel( String label )
      {
       this.label = label;
      }

     public String getName()
      {
       return name;
      }

     public void setName( String name )
      {
       this.name = name;
      }

     public String getUserr()
      {
    	return Utils.notNull( userr ) ;
      }

     public void setUserr( String userr )
      {
       this.userr = userr;
      }

     public String getType()
      {
       return type;
      }

     public void setType( String type )
      {
       this.type = type;
      }

     public int getErrors()
      {
       return errors;
      }

     public void setErrors( int errors )
      {
       this.errors = errors;
      }

     public int getFetched()
      {
       return fetched;
      }

     public void setFetched( int fetched )
      {
       this.fetched = fetched;
      }

     public String getGroupw()
     {
        return Utils.notNull( groupw ) ;
     }

     public void setGroupw( String groupw )
      {
       this.groupw = groupw;
      }

     public int getNextId()
      {
       return nextId;
      }

     public void setNextId( int nextId )
      {
       this.nextId = nextId;
      }

     public String getOwners()
      {
        return Utils.notNull( owners ) ;
      }

     public void setOwners( String owners )
      {
       this.owners = owners;
      }

     public int getScore()
      {
       return score;
      }

     public void setScore( int score )
      {
       this.score = score;
      }

     public String getTitle()
      {
       return title;
      }

     public void setTitle( String title )
      {
       this.title = title;
      }

     public String getUserw()
      {
    	return Utils.notNull( userw ) ;
      }

     public void setUserw( String userw )
      {
       this.userw = userw;
      }

     public float getWeight()
      {
       return weight;
      }

     public void setWeight( float weight )
      {
       this.weight = weight;
      }

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}
    
    public void setPermissions( String user, String groups )
    {
      String[] gr = groups.split( " " ) ;
      setPermissions( user, gr ) ;
    }

    public void setPermissions( String user, String[] groups )
    {
      if ( grouprA == null && groupr != null ) grouprA = groupr.split( " " ) ;
      if ( groupwA == null && groupw != null  ) groupwA = groupw.split( " " ) ;
      if ( userrA == null  && userr != null ) userrA = userr.split( " " ) ;
      if ( userwA == null  && userw != null ) userwA = userw.split( " " ) ;
      if ( ownersA == null && owners != null ) ownersA = owners.split( " " ) ;
      admin = isIn( user, ownersA ) ;
      write = admin || isIn( user, userwA ) || intersect( groupwA, groups ) ;
      read = admin || write || isIn( user, userrA ) || intersect( grouprA, groups ) ;
    }
    
    boolean isIn( String str, String[] arr )
    {
      if ( str != null && arr != null && str.length() > 0 )	
         for ( int i = 0 ; i < arr.length ; i++ )
            if ( str.equals( arr[ i ] ) ) return true ;   
      return false ;
    }

    boolean intersect( String[] a, String[] b )
    {
      if ( a != null && b != null )
       for ( int i = 0 ; i < a.length ; i++ )
         for ( int j = 0 ; j < b.length ; j++ )
           if ( a[i].length() > 0 && a[ i ].equals( b[ j ] ) ) return true ;   
      return false ;
    }

    public boolean isAdmin( String user, String[] groups )
    {
      if ( !isSet ) setPermissions( user, groups ) ;
      return admin;
    }

    public boolean canRead( String user, String[] groups )
    {
      if ( !isSet ) setPermissions( user, groups ) ;
      return read;
    }

    public boolean canWrite( String user, String[] groups )
    {
      if ( !isSet ) setPermissions( user, groups ) ;
      return write;
    }

    public boolean isAdmin( String user, String groups )
    {
      if ( !isSet ) setPermissions( user, groups ) ;
      return admin;
    }

    public boolean canRead( String user, String groups )
    {
      if ( !isSet ) setPermissions( user, groups ) ;
      return read;
    }

    public boolean canWrite( String user, String groups )
    {
      if ( !isSet ) setPermissions( user, groups ) ;
      return write;
    }
    
    /**
     * @return the site
     */
    public String getSite()
    {
    	return site;
    }


    /**
     * @param site the site to set
     */
    public void setSite(String site)
    {
    	this.site = site;
    }

    /**
     * @return the site
     */
    public String getBase()
    {
    	return base ;
    }


    /**
     * @param site the site to set
     */
    public void setBase(String base)
    {
    	this.base = base ;
    }
    
    public String addWithDelim( String s1, String s2 )
    { return Utils.addWithDelim( s1, s2 ) ; }


	/**
	 * @return the hasForm
	 */
	public int getHasForm() {
		return hasForm;
	}


	/**
	 * @param hasForm the hasForm to set
	 */
	public void setHasForm(int hasForm) {
		this.hasForm = hasForm;
	}


	/**
	 * @return the hasScript
	 */
	public int getHasScript() {
		return hasScript;
	}


	/**
	 * @param hasScript the hasScript to set
	 */
	public void setHasScript(int hasScript) {
		this.hasScript = hasScript;
	}


	/**
	 * @return the cRC
	 */
	public long getCRC() {
		return CRC;
	}


	/**
	 * @param cRC the cRC to set
	 */
	public void setCRC(long cRC) {
		CRC = cRC;
	}


	/**
	 * @return the srcCRC
	 */
	public long getSrcCRC() {
		return srcCRC;
	}


	/**
	 * @param srcCRC the srcCRC to set
	 */
	public void setSrcCRC(long srcCRC) {
		this.srcCRC = srcCRC;
	}
}
