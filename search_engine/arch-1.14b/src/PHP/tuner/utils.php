<?php
require_once( "localname.php" ) ;
require_once( local( "config.php" ) ) ;

$sessID = "" ;
$sessName = "" ;
$homePages = null ;


// Aux functions that do not depend on context structure 
class Utils
{
// Adds empty results to array to bring results count to 10

 static function initSmarty()
 {
   global $language ;
   require_once( local( './Smarty/libs/Smarty.class.php' ) ) ;
   $smarty = new Smarty ; // templating object
   $smarty->template_dir = "./language/$language";
   $smarty->compile_dir = './Smarty/templates_c';
   $smarty->cache_dir = './Smarty/cache';
   $smarty->config_dir = './Smarty/configs';
   return $smarty ;
 }

 static function checkResults( &$results, &$message )
 {
    $total = 0 ;
    foreach( $results as $engine => $arr ) 
     {
       if ( !isset( $arr ) ) return -1 ;
       $total += count( $arr ) ;
     } 
    return $total ;  
 }
 
 static function totalsOut( $msg )
 {
   global $resultsFile ;
   global $sessID ;
     
   $a = date("d/m/y : H:i:s ", time() ) ;
   $a .= " " . $msg . " Session: ".$sessID."\n" ;
   Utils::fileWrite( $resultsFile, $a ) ;
 }

 static function relevanceOut( $queries )
 {
   global $relevanceFile ;

   $m = "" ;
   foreach( $queries as $query => $urls )
   {
     if ( isset( $urls ) && count( $urls ) > 0 ) 
      { 
        $m .= "###### ".$query."\n" ;
        foreach( $urls as $url => $y )
          {
            $m .= " + ".$url."\n" ; 
          }
      }  
   }
   if ( $m != "" )      
        Utils::fileWrite( $relevanceFile, $m ) ;
 }

 // Log output
 static function L( $msgLevel, $msg )
{
   global $logLevel ;
   global $logFile ;
   global $sessID ; 
   if ( $msgLevel > $logLevel ) return ;
   
   $a = date("d/m/y : H:i:s ", time() ) ;
   $a .= $msg . " Session: " . $sessID . "\n" ;
 
   if ( $msgLevel >= log_DEBUG )
     while( list($kk, $value) = each( $_REQUEST ) )
     {
       $a .= "REQUEST[$kk] = $value\n" ;
     }      
     
   // DUMP: output full context as well
   if ( $msgLevel >= log_DUMP )
     {
       ob_start() ;
       var_dump( $_SESSION[ "context" ] ) ;
       $context = ob_get_contents() ;
       ob_end_clean() ;          
       $a .= "Context:\n$context\n" ;
     }

   Utils::fileWrite( $logFile, $a ) ;
 }
 
 // keep trying to open the file until it is available
 static function fileWrite( $name, $msg )
 {
   $fh = null ;
   for ( $i = 0 ; $i < 60 ; $i++ )
    {
      $fh = fopen( $name, "a+" ) ;
      if ( $fh ) break ;
      sleep( 1 ) ;
    }
   if ( $fh )
      {
        fwrite( $fh, $msg ) ;
        fclose( $fh ) ;  
      } else 
      {
        throw new Exception( "Can't open file " + $name + " for writing." ) ; 
      }
 }

 static function resumeSession()
 {
   global $sessionStore ;
   global $sessID ;
   global $sessName ;
   // check that sessions work OK 
   if ( isset( $_SESSION[ "started" ] ) )
      { $sessID = session_id(); $sessName = session_name() ; return ; } 
   // quick and dirty hack for cases when "normal" sessions just won't work
   Utils::sessNameVal( $name, $value ) ;
   if ( isset( $_REQUEST[ $name ] ) && $sessionStore )
   {
     $sessID = $_REQUEST[ $name ] ;
     $fname = "$sessionStore"."/".$_REQUEST[ $name ].".txt" ;
     $contents = file_get_contents( $fname ) ;
     if ( $contents )
       $_SESSION = unserialize( $contents ) ;  
   }
   if ( !isset( $_SESSION[ "started" ] ) ) 
      throw new Exception( "PHP sessions do not work. Please report this." ) ;
 }
 
 static function restartSession()
 {
   $_SESSION = array();
   $_SESSION[ "started" ] = "Y" ;
   $name = $value= "" ; 
   Utils::sessNameVal( $name, $value ) ; // init globals
 }

 static function storeSession()
 {
   global $sessionStore, $sessID ;
   global $sessName ;
   
   Utils::sessNameVal( $name, $value ) ;
   // if not saved, will be caught by resumeSession, unless sessions work anyway
   $rr = 0 ;
   if  ( $sessionStore ) 
     {
       $fname = "$sessionStore"."/".$value.".txt" ;
       $contents = serialize( $_SESSION ) ;
       if ( !file_exists( $sessionStore ) ) mkdir( $sessionStore, 0777, true ) ;
       $rr = file_put_contents( $fname, $contents ) ;
     }
   if ( SID != null && SID != "" && !$rr && $sessionStore )
      throw new Exception( "Can't save session data" ) ;
       
   return "<input type='hidden' name='$name' value='$value'>" ;   
 }
 
 static function sessNameVal( &$name, &$value )
 {
   global $sessID ;
   global $sessName ;
   global $sessionStore ;
   $sid = SID ;
   // if set already
   if ( $sessID != "" ) { $name = $sessName ; $value = $sessID ; return ; }
   $name = "sid" ;
   $value = $value = "session".microtime()."-".rand( 0, 1000000 ) ;
   $value = str_replace( " ", "-", $value ) ; 
   
   if ( $sid == "" || $sid == null ) // means cookies are supported, but just in case...
       if ( session_id() != "" ) $value = session_id() ;
    else if ( !$sessionStore ) // not supported, write in URL
          {   
            $i = strpos( $sid, "=" ) ;
            $name = substr( $sid, 0, $i ) ;
            $value = substr( $sid, $i+1 ) ;
          }
    $sessID = $value ;
    $sessName = $name ;
 }
 
 static function equal( $url1, $url2 )
 {
   if ( $url1 == $url2 ) return true ;
   $len1 = strlen( $url1 ) ;
   $len2 = strlen( $url2 ) ;
   if ( $len1 < $len2 ) { if ( substr( $url2, 0, $len1 ) != $url1 ) return false ; }
                   else { if ( substr( $url1, 0, $len2 ) != $url2 ) return false ; }
   // else one is part of another, may refer to an index/home page   
   $url1 = Utils::normalise( $url1 ) ; 
   $url2 = Utils::normalise( $utl2 ) ;
   return $url1 == $url2 ;  
 }
 
 // Home/index page normalisation
 static function normalise( $url )
 {
   global $homePages ;
   global $home ;
   if ( $homePages == null ) 
      { $homePages = explode( " ", $home ) ;
        foreach( $homePages as $key => $page ) 
         { $page = trim( $page ) ;
           if ( $page != "" ) $homePages[ $key ] = "/".$page ;
         }
      }
   foreach( $homePages as $page )
    {
      if ( $page == "" ) continue ;
      if ( Utils::endsWith( $url, $page ) )
        $url = substr( $url, 0, strlen( $url ) - strlen( $page ) ) ;
    }
   if ( Utils::endsWith( $url, "/" ) )
        $url = substr( $url, 0, strlen( $url ) - 1 ) ;
   return $url ;
 }
 
 static function endsWith( $str, $end )
 {
   return strrpos( $str, $end ) === strlen( $str ) - strlen( $end ) ;
 }   
 
}

?>
