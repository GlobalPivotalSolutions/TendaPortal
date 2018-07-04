<?php
  // This module uses Arch open search servlet to get Arch search results

 require_once( 'localname.php' ) ;  // functions substituting names of files with that of local copies if exist
 require_once( local( 'config.php' ) ) ; // configuration parameters
 require_once( local( 'utils.php' ) ) ; // configuration parameters


// The $public parameter is true if search for public pages only.
// It is ignored for Google and Nutch. But, when Arch is evaluated against
// Google, Arch must be restricted to search only public pages, for a fair test.
// When Arch is evaluated against Nutch, it must search all pages
function search_arch( $public, $query, $hits )
{
  //---------------------------------------------------------------------------
  // Arch address, front-end id and password
  // These must be configured for this module to work!
  $archAddress = 'http://127.0.0.1:8080/arch/search' ;    
  $id = 'frontid' ;
  $password = 'frontpass' ;
  
  if ( $public ) $group = "public" ; else $group = "staff" ;
  $description = "$archAddress?lang=en&q={searchTerms}&start=0".
         "&ar_format=rss{groups}&ar_user=guest&ar_frontid=$id&ar_password=$password" ;
  
  $groups = '&ar_groups='.$group ;    
  $description = str_replace( '{groups}', $groups, $description ) ;
  $query = urlencode( trim( $query ) ) ;
  $description = str_replace( '{searchTerms}', $query, $description ) ;
  $description .= "&rows=$hits" ;

  $contents = file_get_contents( $description ) ;
  if ( $contents == null ) return Array( "Arch did not return results." ) ;
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
     if ( isset( $vals[ $index[ "TITLE"][ $n ] ][ "value" ] ) )
          $item[ "title" ] = $vals[ $index[ "TITLE"][ $n ] ][ "value" ] ;
          else $item[ "title" ] = $item[ "link" ] ;
          
     if ( isset( $vals[ $index[ "DESCRIPTION"][ $n ] ][ "value" ] ) )
        $item[ "description" ] = $vals[ $index[ "DESCRIPTION"][ $n ] ][ "value" ] ;
        else $item[ "description" ] = "" ;
//     $item[ "debug" ] = "1" ;
     array_push( $results, $item ) ;
   }                    
 return $results ;
} 

?>
