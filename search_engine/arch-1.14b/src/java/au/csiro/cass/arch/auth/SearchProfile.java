package au.csiro.cass.arch.auth;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Container for a search (front-end) profile information.
 * 
 * Copyright 2015, CSIRO Australia All rights reserved.
 * 
 */
public class SearchProfile
{
  /** id of the gateway */
  private String id;
  /** gateway's password */
  private String password;
  /** list of names of sites allowed to be searched */
  private String sites;
  /** list of names of areas allowed to be searched */
  private String areas;
  /** list of names of groups allowed to be presented */
  private String groups;
  /** list of names of users allowed to be presented */
  private String users;
  /** true if this is guest profile */
  private boolean guest;

  /** set of names of sites allowed to be searched */
  private HashSet<String> siteS;
  /** set of names of areas allowed to be searched */
  private HashSet<String> areaS;
  /** set of names of groups allowed to be presented */
  private HashSet<String> groupS;
  /** set of names of users allowed to be presented */
  private HashSet<String> userS;

  /**
   * Parses configuration search profile (front-end profile) record and creates a SearchProfile object
   * 
   * @param record
   *          search profile record as described in sample configuration file
   * @return SearchProfile object
   */
  static public SearchProfile newSearchProfile( String record ) throws IOException
  {
    // search profile record example:
    // myId | myPassword | site1 site2 | area1 area2 | group1 group2 | user1 user2
    SearchProfile c = new SearchProfile();
    c.guest = false;
    String[] fields = record.split( "\\|" );
    if ( fields.length < 2 )
    {
      throw new IOException( "Id and password are required parameters in search profile." );
    }
    c.id = fields[ 0 ].trim();
    c.password = fields[ 1 ].trim(); // id and password are the only requred parameters
    if ( fields.length > 2 )
    {
      c.sites = fields[ 2 ].trim();
      if ( c.sites.length() == 0 )
      {
        c.sites = null;
      } else
      {
        c.siteS = newSet( c.sites );
      }
      if ( fields.length > 3 )
      {
        c.areas = fields[ 3 ].trim();
        if ( c.areas.length() == 0 )
        {
          c.areas = null;
        } else
        {
          c.areaS = newSet( c.areas );
        }
        if ( fields.length > 4 )
        {
          c.groups = fields[ 4 ].trim();
          if ( c.groups.length() == 0 )
          {
            c.groups = null;
          } else
          {
            c.groupS = newSet( c.groups );
          }
          if ( fields.length > 5 )
          {
            c.users = fields[ 5 ].trim();
            if ( c.users.length() == 0 )
            {
              c.users = null;
            } else
            {
              c.userS = newSet( c.users );
            }
          }
        }
      }
    }
    return c;
  }

  /**
   * Create blank search profile object
   * 
   * @return created search profile object
   */
  static public SearchProfile newGuestSearchProfile()
  {
    SearchProfile c = new SearchProfile();
    c.guest = true;
    return c;
  }

  public String getId()
  {
    return id;
  }

  public String getPassword()
  {
    return password;
  }

  public String getSites()
  {
    return sites;
  }

  public String getAreas()
  {
    return areas;
  }

  public String getGroups()
  {
    return groups;
  }

  public String getUsers()
  {
    return users;
  }

  public HashSet<String> getSiteS()
  {
    return siteS;
  }

  public HashSet<String> getAreaS()
  {
    return areaS;
  }

  public HashSet<String> getGroupS()
  {
    return groupS;
  }

  public HashSet<String> getUserS()
  {
    return userS;
  }

  SearchProfile()
  {}

  /**
   * Converts a string of space separated tokens to a set of Strings
   * 
   * @param str
   *          space separated tokens
   * @return set with the tokens
   */
  static public HashSet<String> newSet( String str )
  {
    String[] a = str.split( " " );
    HashSet<String> set = new HashSet<String>( a.length );
    for ( int i = 0; i < a.length; i++ )
    {
      String aa = a[ i ].trim();
      if ( aa.length() == 0 )
      {
        continue;
      }
      set.add( aa );
    }
    return set;
  }

  /**
   * Returns names of sites to search as intersection of wanted sites and profile sites
   * 
   * @param sites
   *          wanted sites
   * @return space separated list of names of sites
   */
  public String getSites( String sites )
  {
    if ( guest )
    {
      return sites;
    }
    if ( sites == null || sites.length() == 0 )
    {
      return this.sites;
    } else
    {
      return intersect( siteS, sites );
    }
  }

  /**
   * Returns names of areas to search as intersection of wanted areas and profile areas
   * 
   * @param areas
   *          wanted areas
   * @return space separated list of names of areas
   */
  public String getAreas( String areas )
  {
    if ( guest )
    {
      return areas;
    }
    if ( areas == null || areas.length() == 0 )
    {
      return this.areas;
    } else
    {
      return intersect( areaS, areas );
    }
  }

  /**
   * Returns names of groups to filter on as intersection of request groups and profile groups
   * 
   * @param groups
   *          request groups
   * @return space separated list of names of groups
   */
  public String getGroups( String groups )
  {
    if ( guest )
    {
      return ( "public" );
    }
    if ( groups == null || groups.length() == 0 )
    {
      return this.groups;
    } else
    {
      return intersect( groupS, groups );
    }
  }

  /**
   * Returns names of users to filter on as intersection of request users and profile users
   * 
   * @param users
   *          request users
   * @return space separated list of names of users
   */
  public String getUsers( String users )
  {
    if ( guest )
    {
      return ( "guest" );
    }
    if ( users == null || users.length() == 0 )
    {
      return this.users;
    } else
    {
      return intersect( userS, users );
    }
  }

  /**
   * Intersects two collections of tokens, one in a Set, another one in a space separated String
   * 
   * @param set
   *          a set of tokens
   * @param str
   *          a space separated list of tokens
   * @return intersection of two collections
   */
  String intersect( Set<String> set, String str )
  {
    if ( set == null )
    {
      return str; // no limitations
    }
    String[] a = str.split( " " );
    StringBuffer buf = new StringBuffer();

    for ( int i = 0; i < a.length; i++ )
    {
      String aa = a[ i ].trim();
      if ( set.contains( aa ) )
      {
        if ( buf.length() > 0 )
        {
          buf.append( " " );
        }
        buf.append( aa );
      }
    }

    return buf.toString();
  }
}
