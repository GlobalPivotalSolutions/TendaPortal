package au.csiro.cass.arch.index;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Matcher of URL -> site and area
 * 
 * Copyright 2015, CSIRO Australia All rights reserved.
 * 
 */
public class SiteMatcher
{
  /** site host address -> area matcher */
  private Map<String, AreaMatcher> areaMatchers;

  public SiteMatcher()
  {
    areaMatchers = new HashMap<String, AreaMatcher>();
  }

  /**
   * SiteMatcher factory. Creates an site matcher based on a map of roots, inclusions and exclusions. The keys have form
   * of <site name>.<area name>.
   * 
   * @param roots
   *          map of roots, inclusions and exclusions
   * @return created matcher
   */
  public static SiteMatcher newSiteMatcher( Map<String, ArrayList<String>[]> roots ) throws Exception
  {
    SiteMatcher matcher = new SiteMatcher();
    Iterator<Map.Entry<String, ArrayList<String>[]>> it = roots.entrySet().iterator();
    while ( it.hasNext() )
    {
      Map.Entry<String, ArrayList<String>[]> ent = it.next();
      String[] kk = ent.getKey().split( "\n" );
      if ( kk[ 1 ].equals( "bookmarks" ) )
      {
        continue;
      }
      ArrayList<String>[] paths = ent.getValue();
      String u = null;
      if ( paths[ 2 ] != null && paths[ 2 ].size() > 0 )
      {
        u = paths[ 2 ].get( 0 );
      } else if ( paths[ 1 ] != null && paths[ 1 ].size() > 0 )
      {
        u = paths[ 1 ].get( 0 );
      } else if ( paths[ 0 ] != null && paths[ 0 ].size() > 0 )
      {
        u = paths[ 0 ].get( 0 );
      }
      if ( u == null )
      {
        continue;
      }
      URL url = new URL( u );
      String host = url.getHost().toLowerCase();
      AreaMatcher areaMatcher = matcher.areaMatchers.get( host );
      if ( areaMatcher == null )
      {
        areaMatcher = AreaMatcher.newAreaMatcher( kk[ 0 ] );
        matcher.areaMatchers.put( host, areaMatcher );
      }
      // Check that all urls belong to same host
      checkHost( host, paths[ 0 ] );
      checkHost( host, paths[ 1 ] );
      checkHost( host, paths[ 2 ] );
      areaMatcher.addArea( kk[ 1 ], paths[ 2 ], paths[ 0 ], paths[ 1 ] );
    }
    return matcher;
  }
  
  
  /* (non-Javadoc)
   * @see java.lang.Object#toString()
   */
  public String toString()
  {
    StringBuilder b = new StringBuilder( "=============================== SITEMATCHER ===============================\n " ) ;
    for ( AreaMatcher am : areaMatchers.values() )
      b.append( am.toString() ) ;
    b.append( "END SITEMATCHER ===========================================================\n " ) ;
    return b.toString() ;
  }


  /**
   * Check that all urls belong to same host
   * 
   * @param host
   *          - a host all urls should belong to.
   * @return urls - URLs to check.
   */
  public static boolean checkHost( String host, ArrayList<String> urls ) throws Exception
  {
    for ( String url : urls )
    {
      URL u = new URL( url );
      if ( !host.equalsIgnoreCase( u.getHost() ) )
      {
        throw new Exception( "Different hosts in one area: " + host + " and " + u.getHost() );
      }
    }
    return true;
  }

  /**
   * Identify site and areas based on the URL.
   * 
   * @param url
   *          URL to use for identification.
   * @return SiteAreas container with site and area names.
   */
  public SiteAreas getSiteAreas( String url ) throws Exception
  {
    String u = url.toLowerCase();
    URL uu = new URL( u );
    AreaMatcher matcher = areaMatchers.get( uu.getHost() );
    if ( matcher == null )
    {
      return null;
    }
    ArrayList<String> areas = matcher.getAreas( u );
    return new SiteAreas( matcher.getSite(), areas );
  }

  /**
   * Identify site and areas based on the URL.
   * 
   * @param url
   *          URL to use for identification.
   * @return SiteAreas container with site and area names.
   */
  public String getSite( String url ) throws Exception
  {
    String u = url.toLowerCase();
    URL uu = new URL( u );
    AreaMatcher matcher = areaMatchers.get( uu.getHost() );
    return matcher == null ? null : matcher.getSite();
  }

}
