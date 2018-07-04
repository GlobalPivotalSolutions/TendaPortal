package au.csiro.cass.arch.index;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A "special" area for bookmarks processing. Two differences from the parent class: a) This area is considered changed
 * when at least one of the bookmark files has changed. b) Bookmarks from bookmark files are used to inject URLs for
 * processing.
 * 
 * Copyright 2015, CSIRO Australia All rights reserved.
 * 
 */
public class Bookmarks extends IndexArea
{
  public static final Logger LOG = LoggerFactory.getLogger( Bookmarks.class );

  /**
   * Default parameterless constructor
   */
  public Bookmarks()
  {}

  /**
   * Bookmarks factory
   * 
   * @param site
   *          parent site object
   * @return Bookmarks object, a special case of IndexArea
   * @throws Exception
   *
   */
  static public Bookmarks newBookmarks( IndexSite site ) throws Exception
  {
    Bookmarks area = new Bookmarks();

    LOG.info( "New index area: bookmarks " );
    area.name = "bookmarks";
    area.setEnabled( site.getCfg().get( "enabled.bookmarks", true ) );
    area.site = site;
    area.areaClause = " where site=\'" + site.name + "\' and area=\'bookmarks\'";

    area.setRoots( site.getCfg().getAll( "file." + area.name, false ) );
    if ( area.getRoots() != null )
    {
      System.out.println( "Listed " + area.getRoots().length + " bookmark files." );
    }
    else
    {
      area.setRoots( new String[] { "" + site.getCfg().getDataDir() + "/bookmarks.txt" } );
    }

    area.setCrawlingDays();
    area.setExpire();
    area.setEnabled( site.getCfg().getInherited( "enabled.bookmarks", true ) );
    area.site.indexer.getDb().readDbValues( area );

    return area;
  }

  /* (non-Javadoc)
   * @see au.csiro.cass.arch.index.IndexArea#changed()
   */
  @Override
  boolean changed()
  {
    if ( roots == null || roots.length == 0 )
    {
      return false;
    }
    Date indexed = getLastIndexed();
    for ( int fileNum = 0; fileNum < roots.length; fileNum++ )
    {
      File file = new File( roots[ fileNum ] );
      if ( !file.exists() )
      {
        continue;
      }
      long modTime = file.lastModified();
      if ( indexed.before( new Date( modTime ) ) )
      {
        return true;
      }
    }
    return false;
  }

  // Overrides the IndexArea injector. Takes URLs to inject from bookmark files. One URL per line.
  /* (non-Javadoc)
   * @see au.csiro.cass.arch.index.IndexArea#inject()
   */
  @Override
  boolean inject() throws Exception
  {
    if ( roots == null || roots.length == 0 )
    {
      return false;
    }
    int lines = 0;
    for ( int fileNum = 0; fileNum < roots.length; fileNum++ )
    {
      File file = new File( roots[ fileNum ] );
      if ( !file.exists() )
      {
        LOG.warn( "Bookmarks file " + file.getAbsolutePath() + " does not exist" );
        continue;
      }

      BufferedReader in = new BufferedReader( new FileReader( file ) );
      String line;

      while ( ( line = in.readLine() ) != null )
      {
        line = line.trim() ;
        if ( line.length() == 0 || line.startsWith( "#" )) continue ;
        line = line.replaceAll( " ", "%20" );
        site.indexer.getCrawlRoots().write( line + "\n" );
        lines++;
      }
      in.close();
    }

    if ( lines == 0 )
    {
      LOG.warn( "Did not find any bookmarks. Fix file locations or switch off bookmarks area of site " + site.name );
      return false; // throw new Exception( "Did not find any bookmarks to index." ) ;
    }

    return true;
  }

}
