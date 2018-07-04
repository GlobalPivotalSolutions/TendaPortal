<?php
  // NOTE! This module uses Google REST API to get Google search results.
  // PS. Google recently added a block preventing any useful access to this API.
  // After a few calls, it starts replying with "Suspected Terms of Service Avuse"
  // message instead of expected results set.
  // THREFORE, THE CODE BELOW DOES NOT WORK !!!
  
  // However, if you really need it, it is possible to take results from
  // "normal" Google pages. An example is available here:
  // http://hackingsearch.com/2009/10/how-to-parse-google-serp-results-page-using-regular-expressions/
  // Replace the fetchResults function below with code based on this example.
  // PPS. Google keeps moving and the example above does not seem to work.
  // Anyway, it took about an hour to get something similar working. 
  // For legal reasons, we can't distribute this code with Arch, though
  // this use of Google pages should be covered by the fair use provisions
  // (for test and evaluation).  
  
require_once( "localname.php" ) ; 
require_once( local( "utils.php") ) ;
//$arr = googlesearch( null, "time allocation" ) ;  
//echo  variable_array_dump( "response", $arr ) ;


// The $public parameter is true if search for public pages only.
// It is ignored for Google and Nutch. But, when Arch is evaluated against
// Google, Arch must be restricted to search only public pages, for a fair test.
// When Arch is evaluated against Nutch, it must search all pages
function search_google( $public, $query, $max )
{
// $site = " +site:<place your site filter here>" ; // example: +site:atnf.csiro.au
 $site = " +site:atnf.csiro.au" ; // example: +site:atnf.csiro.au
 // The $public parameter is ignored, Google search is always public
 $url =  "http://ajax.googleapis.com/ajax/services/search/web?v=1.0&rsz=large" ;
 $query .= $site ;
 $query = urlencode( trim( $query ) ) ;
 $url = $url."&q=".$query ;
 $start = 0 ;
 $results = array() ;
 do {
      $total = fetchResults( $results, $url, $max, $start, $msg ) ;
      if ( !$total || $total >= $max ) break ;
      $start += 8 ;
    } while( $total && $total < $max ) ;
    
 if ( !$total ) ; // TODO: write alert to log
 return $results ; 
}


function fetchResults( &$results, $url, $max, $start, &$msg )
{
  //$count = count( $results ) ;
  $uu = $url."&start=".$start ;     
  $contents = file_get_contents( $uu ) ;
  if ( $contents == null )
           { $msg = "Google did not return results." ; return false ; } 
  $arr = json_decode( $contents, 1 )  ;
  if ( $arr == null )
    { $msg = "Could not parse JASON results." ; return false ; } 
  if ( $arr[ "responseStatus" ] != "200" )
     { $msg = "Google returned bad response status ".$arr[ "responseStatus" ] ; return false ; }
                      
// class='highlight'&gt;Public&lt;/span&gt; Observatories ATNF Work Experience Search: Go Webcam&lt;span class='ellipsis'&
  $count = count( $results ) ; 
  foreach( $arr[ "responseData" ][ "results" ] as $result )
   {
     $item = array() ;
     $item[ "link" ] = urldecode( $result[ "url" ] ) ;
     $item[ "title" ] = $result[ "titleNoFormatting" ] ;
     $d = $result[ "content" ] ;
     $d = str_ireplace( "<b>...</b>", "<span class='ellipsis'>...</span>", $d ) ;   
     $d = str_ireplace( "<b>", "<span class='highlight'>", $d ) ;   
     $d = str_ireplace( "</b>", "</span>", $d ) ;
     $item[ "description" ] = $d ;
     // Check that this URL is not there already. Google tends to send duplicates
     $there = false ;
     foreach( $results as $item0 ) 
      {
        if ( $item0[ "link" ] == $item[ "link" ] ) { $there = true ; break ; }
      }
     if ( $there == false )
        { 
          array_push( $results, $item ) ;
          $count++ ;
        }
     if ( $count >= $max ) break ;
   }                    
  return $count ;
}

function badResults( $results, $message )
{
  if ( count( $results ) > 0 ) return $results ;
  array_push( $results, $message ) ;
  return $results ;
}
    
function variable_array_dump( $VARIABLE_NAME, $VARIABLE_ARRAY )
{
  if ( is_array( $VARIABLE_ARRAY ) )
  {
    $output = "<table border='1'>";
    $output .= "<head><tr><td><b>" . $VARIABLE_NAME . "</b></td><td><b>VALUE</b></td></tr></head><body>" ;
    foreach ( $VARIABLE_ARRAY as $key => $value )
    {
         $value = variable_array_dump($key, $value);
         $output .= "<tr><td>$key</td><td>$value</td></tr>";
    }
      $output .= "</body></table>";
      return $output;
   } else
   { return strval( $VARIABLE_ARRAY ) ; }
}

?>
