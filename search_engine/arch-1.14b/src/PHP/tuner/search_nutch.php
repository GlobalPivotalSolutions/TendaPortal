<?php
  // This module uses Arch open search servlet to get Arch search results

 require_once( 'localname.php' ) ;  // functions substituting names of files with that of local copies if exist
 require_once( local( 'config.php' ) ) ; // configuration parameters
 require_once( local( 'utils.php' ) ) ; // configuration parameters

// $aa = nutchsearch1( null, "atnf" ) ;

// The $public parameter is true if search for public pages only.
// It is ignored for Google and Nutch. But, when Arch is evaluated against
// Google, Arch must be restricted to search only public pages, for a fair test.
// When Arch is evaluated against Nutch, it must search all pages
function search_nutch( $public, $query, $hits )
{
  //---------------------------------------------------------------------------
  // Addresses of Nutch "all" and "public only" pages
  // These must be configured for this module to work!
  $publicAddress = 'http://<nutch-server-address>/nutchPublic' ;
  $nutchAddress = 'http://<nutch-server-address>/nutchAll' ;
      
  if ( $public ) $nutchAddress = $publicAddress ;
  $description = 
    "$nutchAddress/opensearch?".
         'query={searchTerms}&start=0&hitsPerSite=0&clustering=&format=rss' ;
  
  $query = urlencode( trim( $query ) ) ;
  $description = str_replace( '{searchTerms}', $query, $description ) ;
  $description .= "&hitsPerPage=$hits" ;

  $contents = file_get_contents( $description ) ;
  if ( $contents == null ) return Array( "Nutch did not return results." ) ;
  $p = xml_parser_create();
  xml_parse_into_struct( $p, $contents, $vals, $index ) ;
  xml_parser_free( $p ) ;
  $results= array() ;
  $count = count( $index[ "DESCRIPTION" ] ) ; $first = true ;
  for( $n = 1 ; $n < $count && $n <= $hits ; $n++ )
   {
     if ( !isset( $index[ "TITLE"][ $n ] ) || !isset( $index[ "LINK"][ $n ] ) ) break ;
     $item = array() ;
     $item[ "link" ] = $vals[ $index[ "LINK"][ $n ] ][ "value" ] ;
     $item[ "title" ] = $vals[ $index[ "TITLE"][ $n ] ][ "value" ] ;
     if ( isset( $vals[ $index[ "DESCRIPTION"][ $n ] ][ "value" ] ) )
        $item[ "description" ] = $vals[ $index[ "DESCRIPTION"][ $n ] ][ "value" ] ;
        else $item[ "description" ] = "" ;
     array_push( $results, $item ) ;
   }                    
 return $results ;
} 
?>
