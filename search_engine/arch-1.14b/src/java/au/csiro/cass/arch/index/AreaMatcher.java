package au.csiro.cass.arch.index;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import au.csiro.cass.arch.utils.ConfigList;

/**
 * Matcher of URL -> area name. Uses area filters to determine if the URL belongs to an area of this site.
 * 
 * Copyright 2015, CSIRO Australia All rights reserved.
 * 
 */
public class AreaMatcher
{
  /** map area name -> area filter */
  private Map<String, AreaFilter> areaFilters;
  /** site name */
  private String site;

  /**
   * Creates an empty area matcher.
   * 
   * @param site
   *          site name
   */
  public AreaMatcher( String site )
  {
    areaFilters = new HashMap<String, AreaFilter>();
    this.site = site;
  }

  
  /* (non-Javadoc)
   * @see java.lang.Object#toString()
   */
  public String toString()
  {
    StringBuilder b = new StringBuilder( "  ================ AREAMATCHER for site " ) ;
    b.append( site ).append( " ==================\n" ) ;
    for ( AreaFilter filter : areaFilters.values() )
      b.append( filter.toString() ) ;
    b.append( "   END AREAMATCHER ========================================\n" ) ;
    return b.toString() ;
  }
  
  /**
   * AreaMatcher factory. Creates an area matcher based on a ConfigList object.
   * 
   * @param cfg
   *          configuration object
   */
  static public AreaMatcher newAreaMatcher( ConfigList cfg )
  {
    String[] areas = cfg.getAll( "area" );
    AreaMatcher matcher = new AreaMatcher( null );
    for ( int i = 0; i < areas.length; i++ )
    {
      if ( cfg.get( "enabled." + areas[ i ] , true ) )
           matcher.areaFilters.put( areas[ i ], AreaFilter.newAreaFilter( cfg, areas[ i ] ) );
    }
    return matcher;
  }

  /**
   * Returns name of the area where the URL belongs, if found, else null
   * 
   * @param URL
   *          URL to check
   * @return area name if found, else null
   */
  public ArrayList<String> getAreas( String URL )
  {
    String u = URL.toLowerCase();
    Iterator<Map.Entry<String, AreaFilter>> it = areaFilters.entrySet().iterator();
    ArrayList<String> areas = new ArrayList<String>();
    while ( it.hasNext() )
    {
      AreaFilter filter = it.next().getValue();
      if ( filter.pass( u ) )
      {
        areas.add( filter.getAreaName() );
      }
    }
    return areas;
  }

  /**
   * Returns if this URL belongs to at least one of the areas
   * 
   * @param URL
   *          URL to check
   * @return true if found, else false
   */
  public boolean pass( String URL )
  {
    String u = URL.toLowerCase();
    Iterator<Map.Entry<String, AreaFilter>> it = areaFilters.entrySet().iterator();
    while ( it.hasNext() )
    {
      AreaFilter filter = it.next().getValue();
      if ( filter.pass( u ) )
        return true;
    }
    return false;
  }

  /*
   * The next few functions are used to build a filter in incremental mode, when roots, inclusions and exclusions can be
   * added one by one
   */

  /**
   * AreaMatcher factory. Creates an empty area matcher.
   * 
   * @param site
   *          site name
   */
  static public AreaMatcher newAreaMatcher( String site )
  {
    AreaMatcher matcher = new AreaMatcher( site );
    return matcher;
  }

  /**
   * Add a root, exclusion or inclusion prefix to the filter
   * 
   * @param area
   *          name of the area the path defines
   * @param path
   *          root, exclusion or inclusion prefix to add
   * @param type
   *          type of the path: 'r' - root, 'e' - exclusion, 'i' - inclusion
   */
  public void addPath( String area, String path, String type )
  {
    AreaFilter filter = areaFilters.get( area );
    if ( filter == null )
    {
      filter = AreaFilter.newAreaFilter( area );
      areaFilters.put( area, filter );
    }
    filter.addPath( path, type );
  }

  /**
   * Create and add an area filter.
   * 
   * @param area
   *          area name
   * @param roots
   *          a list of root (seed) URLs for this area
   * @param exclusions
   *          a list of exclusion prefixes
   * @param inclusions
   *          a list of inclusion prefixes
   */
  public void addArea( String area, ArrayList<String> roots, ArrayList<String> exclusions, ArrayList<String> inclusions )
  {
    AreaFilter filter = AreaFilter.newAreaFilter( area, roots, exclusions, inclusions );
    areaFilters.put( area, filter );
  }

  /**
   * Finalise incremental building and make ready for filtering
   */
  public void finalise()
  {
    for ( int i = 0; i < areaFilters.size(); i++ )
    {
      areaFilters.get( i ).finalise();
    }
  }

  // --------- End incremental building -------------------------------------------

  /**
   * @return the site
   */
  public String getSite()
  {
    return site;
  }
}
