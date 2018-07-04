<?php
require_once( "localname.php" ) ;
require_once( local( "config.php" ) ) ;

class Utils
{
    
 // Encoding and decoding must match what is done by Java code in 
 // au.csiro.atnf.arch.utils.Utils
 static function htmlEncode( $str )
 {
  if ( $str == null ) return "" ;
  $buf = "" ;
  $len = strlen( $str ) ;
  for ( $i = 0 ; $i < $len ; $i++ )
    {
      $c = $str{ $i } ;
      if ( $c != '&' && $c != '>' && $c != '<' && $c != '\"' && $c != '\'' )
         $buf .= $c ;
        else $buf .= "&#".ord($c).";" ;
    }
   return $buf ;

 }  

 // Encoding and decoding must match what is done by Java code in 
 // au.csiro.atnf.arch.utils.Utils
 static function htmlDecode( $str )
 {
  if ( $str == null ) return "" ;
  $buf = "" ;
  $len = strlen( $str ) ;
  $i = 0 ; $p = 0 ; 
  while( ($p = strpos( $str, '&', $i )) !== false )
  {
   if ( $p > $i )
      { $buf = substr( $str, $i, $p - $i ) ; }
   $end = strpos( $str, ';', $p ) ;
   $ii = (int)substr( $str, $p+1, $end - $p - 1 ) ;  
   $buf .= char( $ii ) ; 
   $i = $end + 1 ;
  }
  // have remains of $str starting with $i that don't have '&'
  if ( $i < $len )
     $buf .= substr( $str, $i ) ;   

  return $buf ;
 }
 
 static function getJsp( $args, $user, $groups, $action, $isRoot )
 {
   global $id, $password, $domain, $ArchAddress ;
   $isRootStr = $isRoot ? "&ar_isRoot=y" : "" ;
   $jspAddress = $ArchAddress."/ajax" ;
   $request = "frontid=".urlencode($id)."&password=".urlencode($password).$args.
                      "&ar_user=".urlencode($user)."&ar_groups=".urlencode($groups)."&ar_action=$action".$isRootStr ;
   if ( $domain != null && $domain != "" ) $request = "domain=$domain&".$request ; 
   $msg = Utils::get_it( $request, $jspAddress ) ;
   if ( isset( $msg ) ) return explode( " ", $msg, 2 ) ;
   return $msg ;
 }
 
 // Sockets implementation of HTTP POST request. Use curl instead if ssl is needed.
 static function post_it( $request, $url )
 {
   $url = preg_replace( "@^http://@i", "", $url ) ;
   $host = substr( $url, 0, strpos( $url, "/" ) ) ;
   $port = 80 ;
   $col = strpos( $host, ":" ) ; 
   if ( $col > 0 ) // non-standard port number
      {
        $port = substr( $host, $col + 1 ) ; 
        $host = substr( $host, 0, $col ) ;
      }
   $uri = strstr( $url, "/" ) ;
   $contentlength = strlen( $request ) ;
   $reqheader =  "POST $uri HTTP/1.1\r\n".
                 "Host: $host\n". "User-Agent: PHP\r\n".
                 "Content-Type: application/x-www-form-urlencoded\r\n".
                 "Content-Length: $contentlength\r\n\r\n".
                 "$request\r\n";
                 
                
   $socket = fsockopen( $host, $port, $errno, $errstr ) ;

   if ( !$socket )
    return "--Failed: Can't access jsp interface, errno = $errno, errstr = $errstr" ;

   fputs( $socket, $reqheader );

   $response = $result = "" ;
   while ( !feof( $socket ) )
   {
     $response = fgets( $socket, 4096 ) ;
     if ( $response === false ) break ;
     else $result .= $response ;
   }
   fclose( $socket ) ;

/*   
   $socket = socket_create(AF_INET, SOCK_STREAM, SOL_TCP);
   if ( $socket === false )
     return "--Failed: socket_create() " . socket_strerror( socket_last_error() ) ;
   $address = gethostbyname( $host ) ;
   $con = socket_connect( $socket, $address, $port ) ;
   if ( $con === false ) 
     return "--Failed: socket_connect() " . socket_strerror( socket_last_error() ) ;
   socket_write($socket, $reqheader, strlen( $reqheader ) ) ;
   
   $response = $result = "" ;
   do {
        $response = socket_read( $socket, 4096 ) ;
        $result .= $response ;
      } while ( $response != "" ) ; 
  

  socket_close($socket);
*/
   $col = strpos( $result, "++OK" ) ;
   if ( $col > 0 ) $result = substr( $result, $col + 5 ) ; 

   return $result;
 }
  
 static function get_it( $request, $url )
 {
   $url .= "?".$request ;
   $result = file_get_contents( $url ) ;
   if ( $result == null )
             { return "--Failed: can't get $url" ; }

   $col = strpos( $result, "++OK" ) ;
   if ( $col > 0 ) $result = substr( $result, $col + 5 ) ; 

   return $result;
  }

 static function makeQuery( )
 {
   
   $all = $_REQUEST[ "ar_fall" ] ;
   $not = $_REQUEST[  "ar_fnot" ] ;
   $url = $_REQUEST[ "ar_furl" ] ;
   $host = $_REQUEST[ "ar_fhost" ] ;
   $title = $_REQUEST[ "ar_ftitle" ] ;
   $content = $_REQUEST[ "ar_fcontent" ] ;

   $query = "ar_fall=".urlencode( $all ) ;
   $query .= "&ar_fnot=".urlencode( $not ) ;
   $query .= "&ar_furl=".urlencode( $url ) ;
   $query .= "&ar_fhost=".urlencode( $host ) ;
   $query .= "&ar_ftitle=".urlencode( $title ) ;
   $query .= "&ar_fcontent=".urlencode( $content ) ;
   
/*   
   $query .= Utils::parseField( $all, null, "+" ) ;
   $query .= Utils::parseField( $not, null, "-" ) ;
   $query .= Utils::parseField( $url, "url", "" ) ;
   $query .= Utils::parseField( $host, "host", "" ) ;
   $query .= Utils::parseField( $title, "title", "" ) ;
   $query .= Utils::parseField( $content, "content", "" ) ;
*/   
   return $query ;
 }
 
 static function parseField( $field, $name, $sign )
 {
   if ( $field == null || $field == "" ) return "" ;
   $tk = strtok( stripslashes( $field ), " " ) ;
   $group = $result = $groupSign = "" ;
   $inQ = false ;
   while( $tk !== false )
   {
     if ( $tk{ 0 } == '\"' )
       if ( $inQ ) // inside quotes, finish group
       { 
         $inQ = false ;
         $result .= Utils::finishGroup( $group, $name, $groupSign, $sign ) ;
         $group = "" ;
         $groupSign = "" ;
         $tk = strtok( " " ) ; continue ;
       } else // outside quotes
       {
         $inQ = true ;
         $group = "\"" ;
         $tk = strtok( " " ) ; continue ;
       }
     
     // else this is not a delimeter
     if ( $inQ ) // inside quotes, add term to group
     { if ( strlen( $group ) > 1 ) $group .= " " + $tk ;
                              else $group .= $tk ;
     } else if ( ( $tk{ 0 } == '-' || $tk{ 0 } == '+' ) && strlen( $tk ) == 1  ) // group sign
     {
       $groupSign = $tk ;
     } else // this is a separate term, possibly, with sign
     {
       $s = $sign ;
       if ( $tk{ 0 } == '-' || $tk{ 0 } == '+' )
          { $s = substr( $tk, 0, 1 ) ; $tk = substr( $tk, 1 ) ; }
          else if ( $groupSign != "" ) $s = $groupSign ;
       if ( $name != null ) $result .= " " . $s . $name . ":" . $tk ;
                      else $result .= " " . $s . $tk ;
       $groupSign = "" ;
     }             
     $tk = strtok( " " ) ;
   }
   // forgive unclosed quotes and finish a started group, if any
   if ( $inQ ) 
         $result = Utils::finishGroup( $group, $name, $groupSign, $sign ) ;
   return $result ;     
 }
 
 static function finishGroup( $group, $name, $groupSign, $sign )
 {
   $group .= "\"" ;
   if ( $groupSign == "" ) $groupSign = $sign ;
   if ( $name != null ) $group = " " . $groupSign . $name . ":" . $group ;
                   else $group = " " . $groupSign . $group ;
   if ( strlen( $group ) > 1 ) return $group ; else return "" ;
 }
 
 // Retirns true if $str is found in $arr 
 static function isIn( $str, $arr )
 {
   if ( $str != null && $arr != null && strlen( str ) > 0 )
      foreach( $arr as $val )
            if ( $str == $val ) return true ;   
      return false ;
 }

 // Retirns true if two arrays have at least 1 common member
 static function intersect( $a, $b )
 {
   if ( $a != null && $b != null )
       foreach( $a as $ai ) 
         foreach( $b as $bj ) 
           if ( $ai != "" && $ai == $bj ) return true ;   
   return false ;
 }
  
 static function getTheme()
 {
   $theme = "blue" ;
   if ( !isset( $_COOKIE[ "options" ] ) ) return $theme ;
   $options = explode( "\t", $_COOKIE[ "options" ] ) ;
       foreach( $options as $option )
        {
          if ( substr( $option, 0, 6 ) == "theme=" ) return substr( $option, 6 ) ;
        } 
   return $theme ;
 }

 // Log output
 static function L( $msgLevel, $msg )
 {
   global $logFile, $logLevel ;    
  
   if ( $msgLevel > $logLevel ) return ;
   
   $a = date("d/m/y : H:i:s ", time() ) ;
   $a .= $msg . " Session: " . session_id() . "\n" ;
 
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

  
}

?>
