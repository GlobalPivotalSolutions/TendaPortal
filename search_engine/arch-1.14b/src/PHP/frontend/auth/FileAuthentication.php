<?php
// This code inmplements file based authentication. This authentication method
// is not very likely to be used in a company with a considerable number of
// employees, but this code shows what is needed from implementation of an
// authentication method.


require_once( "localname.php" ) ;
require_once( local( "config.php" ) ) ;
 
function authenticate()
 {
  global $passFile, $groupsFile ; // these must be set in config.php
  if ( $passFile == null || $groupsFile == null )  
     throw new Exception( "Passwords file or groups file is not configured." ) ;
  if ( isset( $_REQUEST[ "ar_action" ]  ) ) $action = $_REQUEST[ "ar_action" ] ; else $action = "" ;
  if ( $action == "login" )
     {
       // This implements simple Apache file based authentication     
       $user = $_REQUEST[ "ar_fname" ] ;
       $password = $_REQUEST[ "ar_fpass" ] ;
  
       $filePassword = getPassword( $passFile, $user ) ;
       if ( $filePassword == null ) return false ;
       $salt = substr( $filePassword, 0, 2 ) ; // This is for crypt DES encryption only!!
       $sentPassword = crypt( $password, $salt ) ;
       
       if ( $filePassword != $sentPassword ) return false ;
       $_SESSION[ "ar_user" ] = $user ; // each user is a guest as well
       $_SESSION[ "ar_groups" ] = getGroups( $groupsFile, $user ) ;
       $_SESSION[ "ar_loggedin" ] = "Y" ;
     }     
      
  return true ;
 }

 function getUserName()
 {
   if ( isset( $_SESSION[ "ar_user" ] ) ) $user = $_SESSION[ "ar_user" ] ; else $user = null ;
   if ( $user == null ) $user = "guest" ;
   return $user ;
 }

 function getUserGroups()
 {
   if ( isset( $_SESSION[ "ar_groups" ] ) )  $groups = $_SESSION[ "ar_groups" ] ; else $groups = null ;
   if ( $groups == null ) $groups = "public" ;
   return $groups ;
 }

 function logout()
 {
   $_SESSION = array();
   if ( ini_get("session.use_cookies") )
   {
     $params = session_get_cookie_params();
      setcookie( session_name(), '', time() - 42000,
                 $params["path"], $params["domain"],
                 $params["secure"], $params["httponly"]
               ) ;
   }
   session_destroy();
 }

 /**
  * Get encrypted password for this user.
  */
 function getPassword( $filename, $user ) 
 {
   $file = fopen( $filename, "r" ) or exit( "Unable to open user passwords file!" ) ;
   $start = $user.":" ;
   $len = strlen( $start ) ;
   while( !feof( $file ) )
   {
     $line = fgets( $file ) ;
     if ( substr( $line, 0, $len ) == $start ) break ;
     $line = null ;
   }
   fclose($file);
   if ( $line != null )
      return trim( substr( $line, $len ) ) ;
   return null ;
 }
 
 /**
  * Get a string of user groups separated by spaces.
  */
 function getGroups( $filename, $user )
 {
   $groups = "" ;
   $file = fopen( $filename, "r" ) or exit( "Unable to open user groups file!" ) ;
   while( ($line = fgets( $file )) !== false )
   {
     $line = trim( $line ) ;
     $pos = 0 ; $len = strlen( $user ) ; $lilen = strlen( $line ) ;
     do {
           $pos = strpos( $line, $user, $pos + 1 ) ;
           if ( $pos > 1 )
           { $a = $line{ $pos - 1 } ;
             $b = $pos + $len == $lilen ? ' ' : $line{ $pos + $len } ;
             if ( ( $a == ':' || $a == ' ' ) && $b == ' ' ) 
              { 
                if ( strlen( $groups ) > 0 ) $groups .= " " ;
                $groups .= substr( $line, 0, strpos( $line, ':' ) ) ;
                break ;
              }
           }
        } while( $pos > 1 ) ;
    }
    fclose($file);
    if ( strpos( $groups, "public" ) === false ) $groups .= " public" ;
    return $groups ;
 }
  
?>
