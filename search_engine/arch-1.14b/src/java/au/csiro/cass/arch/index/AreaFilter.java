package au.csiro.cass.arch.index;

import java.util.ArrayList;
import java.util.Collection;

import org.apache.nutch.util.PrefixStringMatcher;

import au.csiro.cass.arch.utils.ConfigList;

/**
 * Area filter. Uses lists of roots, inclusion and exclusion prefixes to determine if a URL belongs to this particular
 * area. A URL belongs to the area (passes the filter) if it is either one of the roots or matches at least one of
 * inclusion prefixes and none of exclusion prefixes.
 *
 * Copyright 2015, CSIRO Australia All rights reserved.
 */
public class AreaFilter
{
  /** roots matcher */
  private PrefixStringMatcher rootMatcher;
  /** includes matcher */
  private PrefixStringMatcher inclMatcher;
  /** excludes matcher */
  private PrefixStringMatcher exclMatcher;
  /** false for log links filtering only */
  private boolean active;
  /** area name */
  private String areaName;
  /** ArrayLists for incremental building */
  private ArrayList<String> aRoots;
  /** ArrayLists for incremental building */
  private ArrayList<String> aExclusions;
  /** ArrayLists for incremental building */
  private ArrayList<String> aInclusions;

  public AreaFilter()
  {};

  /**
   * Filter factory
   * 
   * @param areaName
   *          area name
   * @param roots
   *          a list of root (seed) URLs for this area
   * @param exclusions
   *          a list of exclusion prefixes
   * @param inclusions
   *          a list of inclusion prefixes
   * @return created area filter
   * 
   */
  public static AreaFilter newAreaFilter( String areaName, ArrayList<String> roots, ArrayList<String> exclusions,
      ArrayList<String> inclusions )
  {
    AreaFilter filter = new AreaFilter();
    filter.aRoots = roots;
    filter.aExclusions = exclusions;
    filter.aInclusions = inclusions;
    filter.finalise();
    filter.areaName = areaName;
    return filter;
  }
  
  /* (non-Javadoc)
   * @see java.lang.Object#toString()
   */
  public String toString()
  {
    StringBuilder b = new StringBuilder( "      ====== AREAFILTER for area " ) ;
    b.append( areaName ).append( " ======\n" ) ;
    dumpArray( b, aRoots, "ROOTS" ) ;
    dumpArray( b, aInclusions, "INCLUSIONS" ) ;
    dumpArray( b, aExclusions, "EXCLUSIONS" ) ;
    b.append( "      END AREAFILTER ======================\n" ) ;
    return b.toString() ;
  }

  private void dumpArray( StringBuilder b, Collection<String> a, String tag )
  {
    if ( a == null ) return ;
    b.append( "        " ).append( tag ).append( " --------------\n" ) ;
    for ( String s : a )
      b.append( "            " ).append( s ).append( "\n" ) ;
    b.append( "        END " ).append( tag ).append( "----\n" ) ;
  }
  
  /*
   * The next few functions are used to build a filter in incremental mode, when roots, inclusions and exclusions can be
   * added one by one
   */

  /**
   * Create a new empty filter
   * 
   * @param areaName
   *          area name
   * @return created empty area filter
   */
  public static AreaFilter newAreaFilter( String areaName )
  {
    AreaFilter filter = new AreaFilter();
    filter.areaName = areaName;
    filter.aRoots = new ArrayList<String>();
    filter.aExclusions = new ArrayList<String>();
    filter.aInclusions = new ArrayList<String>();
    return filter;
  }

  /**
   * Add a root, exclusion or inclusion prefix to the filter
   * 
   * @param path
   *          root, exclusion or inclusion prefix to add
   * @param type
   *          type of the path: 'r' - root, 'e' - exclusion, 'i' - inclusion
   */
  public void addPath( String path, String type )
  {
    switch ( type.charAt( 0 ) )
    {
    case 'r':
      aRoots.add( path );
      break;
    case 'e':
      aExclusions.add( path );
      break;
    case 'i':
      aInclusions.add( path );
      break;
    }
  }

  /**
   * Finalise incremental building and make ready for filtering
   */
  public void finalise()
  {
    rootMatcher = newMatcher( aRoots );
    exclMatcher = newMatcher( aExclusions );
    inclMatcher = newMatcher( aInclusions );
    setActive( true );
  }

  /**
   * Create a new prefix string matcher
   * 
   * @param arr
   *          ArrayList of prefixes
   * @return created prefix string matcher
   */
  PrefixStringMatcher newMatcher( ArrayList<String> arr )
  {
    int sz;
    if ( ( sz = arr.size() ) > 0 )
    {
      String[] a = new String[ sz ];
      for ( int i = 0; i < sz; i++ )
      {
        a[ i ] = arr.get( i ).toLowerCase();
      }
      return new PrefixStringMatcher( a );
    }
    return null;
  }

  // End incremental building functions -----------------------------------

  /**
   * Create a filter for a particular area based on a ConfigList object
   * 
   * @param cfg
   *          configuration object
   * @param areaName
   *          area name
   * @return created AreaFilter
   */
  public static AreaFilter newAreaFilter( ConfigList cfg, String areaName )
  {
    String[] roots; // starting points for indexing this area
    String[] exclusions; // sub-trees to exclude from indexing
    String[] inclusions; // sub-trees to include in the area

    exclusions = cfg.getAll( "exclude." + areaName, true );
    inclusions = cfg.getAll( "include." + areaName, true );
    roots = cfg.getAll( "root." + areaName, true );

    AreaFilter filter = new AreaFilter();
    filter.areaName = areaName;
    // if ( areaName.equals( "loglinks" ) ) return filter ; // active == false
    filter.setActive( true );

    if ( roots != null )
    { 
      filter.rootMatcher = new PrefixStringMatcher( roots );
    }
    if ( exclusions != null && exclusions.length != 0 )
    {
      for ( int i = 0; i < exclusions.length; i++ )
      {
        exclusions[ i ] = exclusions[ i ].toLowerCase();
      }
      filter.exclMatcher = new PrefixStringMatcher( exclusions );
    }
    if ( inclusions != null && inclusions.length != 0 )
    {
      for ( int i = 0; i < inclusions.length; i++ )
      {
        inclusions[ i ] = inclusions[ i ].toLowerCase();
      }
      filter.inclMatcher = new PrefixStringMatcher( inclusions );
    }
    return filter;
  }

  /**
   * Checks if a URL belongs to the area
   * 
   * @param url
   *          URL to check
   * @return true if the URL does belong to the area
   */
  public boolean pass( String url )
  {
    if ( rootMatcher != null )
    {
      String lm = rootMatcher.longestMatch( url );
      if ( lm != null && lm.equals( url ) )
      {
        return true;
      }
    }

    if ( inclMatcher != null ) // else everything is included, except excluded
    {
      if ( !inclMatcher.matches( url ) )
      {
        return false;
      }
    }
    if ( exclMatcher == null )
    {
      return true;
    }
    if ( exclMatcher.matches( url ) )
    {
      return false;
    }
    return true;
  }

  String getAreaName()
  {
    return areaName;
  }

  /**
   * @return the active
   */
  public boolean isActive()
  {
    return active;
  }

  /**
   * @param active the active to set
   */
  public void setActive( boolean active )
  {
    this.active = active;
  }

}
