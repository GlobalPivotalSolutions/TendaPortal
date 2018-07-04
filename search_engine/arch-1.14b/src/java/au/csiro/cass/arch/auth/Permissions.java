package au.csiro.cass.arch.auth;

import java.io.IOException;

import au.csiro.cass.arch.utils.URLSplit;

/**
 * Container for node (URL) permissions information
 * 
 * Copyright 2015, CSIRO Australia All rights reserved.
 * 
 */
public class Permissions
{
  /** url these permissions apply to */
  private String file;
  /**
   * type of the url: "f" - file, "d" - directory note that /b/c/d refer to directory d (if d is a directory) but
   * /b/c/d/ refer to default file in directory d
   */
  private String type;
  /** split into path and file name */
  private URLSplit split;
  /** list of users having read access */
  private String userr;
  /** list of users having read and write access */
  private String userw;
  /** list of groups having read access */
  private String groupr;
  /** list of groups having read and write access */
  private String groupw;
  /* list of users having admin access */
  private String owners;
  /**
   * "s" - standard, "i" - inherited Everything is taken from parent node when access is inherited, and this is the
   * default access type. The only reason to have an "i" Permissions object is to change access from "s" where it was
   * set to "s" before.
   */
  private String accessType;

  /**
   * Parameterless constructor
   */
  public Permissions()
  {}

  /**
   * Parses configuration permissions record and creates a Permissions object
   * 
   * @param record
   *          permissions record as described in sample configuration file
   * @return Permissions object
   */
  static public Permissions newPermissions( String record ) throws IOException
  {
    // permissions record example:
    // http://a.b.c/d/e | userr1 userr2 | userw1 | groupr1 | groupw1 | amdin | s
    Permissions p = new Permissions();
    String[] fields = record.split( "\\|" );
    p.type = fields[ 0 ].trim();
    if ( p.type.length() == 0 )
    {
      p.type = "f";
    }
    p.file = fields[ 1 ].trim();
    if ( p.file.length() == 0 )
    {
      p.file = null;
    }
    p.groupr = fields[ 2 ].trim();
    if ( p.groupr.length() == 0 )
    {
      p.groupr = null;
    }
    p.groupw = fields[ 3 ].trim();
    if ( p.groupw.length() == 0 )
    {
      p.groupw = null;
    }
    p.userr = fields[ 4 ].trim();
    if ( p.userr.length() == 0 )
    {
      p.userr = null;
    }
    p.userw = fields[ 5 ].trim();
    if ( p.userw.length() == 0 )
    {
      p.userw = null;
    }
    p.owners = fields[ 6 ].trim();
    if ( p.owners.length() == 0 )
    {
      p.owners = null;
    }
    p.accessType = fields[ 7 ].trim();
    if ( p.accessType.length() == 0 )
    {
      p.accessType = null;
    }
    p.split = URLSplit.newURLSplit( p.file, null, p.type );
    return p;
  }

  public String getFile()
  {
    return file;
  }

  /**
   * Sets file and type
   * @param file
   *          the file to set
   * @param type
   *          the type to set
   */
  public void setFile( String file, String type ) throws Exception
  {
    this.file = file;
    this.type = type;
    this.split = URLSplit.newURLSplit( this.file, null, type );
  }

  /**
   * Gets file name part of the URL
   * @return file name
   */
  public String getName()
  {
    return split.getName();
  }

  /**
   * Gets file path of the URL
   * @return file path
   */
  public String getPath()
  {
    return split.getPath();
  }

  public String getUserr()
  {
    return userr;
  }

  /**
   * Gets names of users with read access as an array of strings 
   * @return array of names of users having read access
   */
  public String[] getUserrArray()
  {
    if ( userr != null )
      return userr.split( " " );
    else
      return null;
  }

  public void setUserr( String userr )
  {
    this.userr = userr;
  }

  public String getUserw()
  {
    return userw;
  }

  /**
   * Gets names of users with read/write access as an array of strings
   * @return array of users having read and write access
   */
  public String[] getUserwArray()
  {
    if ( userw != null )
      return userw.split( " " );
    else
      return null;
  }

  public void setUserw( String userw )
  {
    this.userw = userw;
  }

  public String getGroupr()
  {
    return groupr;
  }

  /**
   * Gets names of groups with read access as an array of strings
   * @return array of groups having read access
   */
  public String[] getGrouprArray()
  {
    if ( groupr != null )
      return groupr.split( " " );
    else
      return null;
  }

  public void setGroupr( String groupr )
  {
    this.groupr = groupr;
  }

  public String getGroupw()
  {
    return groupw;
  }

  /**
   * Gets names of groups with read/write access as an array of strings
   * @return array of groups having write access
   */
  public String[] getGroupwArray()
  {
    if ( groupw != null )
      return groupw.split( " " );
    else
      return null;
  }

  public void setGroupw( String groupw )
  {
    this.groupw = groupw;
  }

  public String getOwners()
  {
    return owners;
  }

  /**
   * Gets names of users with admin access as an array of strings
   * @return array of owners
   */
  public String[] getOwnersArray()
  {
    if ( owners != null )
      return owners.split( " " );
    else
      return null;
  }

  public void setOwners( String owners )
  {
    this.owners = owners;
  }

  public String getAccessType()
  {
    return accessType;
  }

  public void setAccessType( String accessType )
  {
    this.accessType = accessType;
  }

  public String getType()
  {
    return type;
  }

  public void setType( String type )
  {
    this.type = type;
  }

}
