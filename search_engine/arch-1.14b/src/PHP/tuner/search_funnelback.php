<?php
  // This module uses Arch open search servlet to get Arch search results

 require_once( 'localname.php' ) ;  // functions substituting names of files with that of local copies if exist
 require_once( local( 'config.php' ) ) ; // configuration parameters
 require_once( local( 'utils.php' ) ) ; // configuration parameters

// The $public parameter is true if search for public pages only.
function search_funnelback( $public, $query, $hits )
{
  // Addresses of Funnelback "all" and "public only" pages
  // These must be configured for this module to work!
  $addressExt = 'http://<public-search-server>/search' ; 
  $addressAll = 'http://<internal-search-server>/search' ;
  // Server administrators must provide collection name and search scope
  $collection = '<collection name>' ;
  $scope = '<query scope>' ; 
  $address = $public ? $addressExt : $addressAll ; 
  $description = 
         
  "$address/xml.cgi?searchtype=none&$collection=csiro&query={searchTerms}&".
      "$scope=atnf.csiro.au&form=advanced&submit=Search" ;
  
  $query = urlencode( trim( $query ) ) ;
  $description = str_replace( '{searchTerms}', $query, $description ) ;
  $description .= "&num_ranks=$hits" ;

  $contents = file_get_contents( $description ) ;
  if ( $contents == null ) return Array( "Panoptic did not return results." ) ;
  $p = xml_parser_create();
  xml_parse_into_struct( $p, $contents, $vals, $index ) ;
  xml_parser_free( $p ) ;
  $results= array() ;
  $count = count( $index[ "SUMMARY" ] ) ; $first = true ;
  for( $n = 0 ; $n < $count && $n <= $hits ; $n++ )
   {
     if ( !isset( $index[ "TITLE"][ $n ] ) || !isset( $index[ "LIVE_URL"][ $n ] ) ) break ;
     $item = array() ;
     $item[ "link" ] = $vals[ $index[ "LIVE_URL"][ $n ] ][ "value" ] ;
     $item[ "title" ] = $vals[ $index[ "TITLE"][ $n ] ][ "value" ] ;
     if ( isset( $vals[ $index[ "SUMMARY"][ $n ] ][ "value" ] ) )
        $item[ "description" ] = $vals[ $index[ "SUMMARY"][ $n ] ][ "value" ] ;
        else $item[ "description" ] = "" ;
     $d = $item[ "description" ] ;
     $d = str_ireplace( "<b>...</b>", "<span class='ellipsis'>...</span>", $d ) ;   
     $d = str_ireplace( "<b>", "<span class='highlight'>", $d ) ;   
     $d = str_ireplace( "</b>", "</span>", $d ) ;
     $item[ "description" ] = $d ;
   
     array_push( $results, $item ) ;
   }                    
 return $results ;
} 
?>
