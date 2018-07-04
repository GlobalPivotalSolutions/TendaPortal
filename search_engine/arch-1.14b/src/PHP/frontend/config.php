<?php
  // A template for configuration parameters. This file should not be edited to
  // avoid loss of data when upgrading. Instead, create local/config.php
  // and edit it. If exists, it will be used instead of this one.
  $language = "en" ;
  
  // Address of your Arch server to be used by the front-end
  // Replace host, port, path with your host, port and path.
  $ArchAddress = "http://localhost:8993/solr/arch" ;
    
  // Module used for local user authentication
  $authenticator = "./auth/FileAuthentication.php" ; 
  
  // FileAuthentication.php implements Apache file authentication requiring passwords and groups files
  $passFile = "/etc/auth/testPasswords.txt" ;
  $groupsFile = "/etc/auth/testGroups.txt" ;
   
  // Arch front-end id, this must match the front-end id in Arch server configuration files
  $id = "global" ;
  
  // Arch authentication password, this must match the front-end password in Arch server configuration files
  $password = "pass1" ;
  
  // Site of interest
  // If this is set and not empty, browsing and search will be reduced to this site only
  // In this case front-end record matching the id and password must be in the site config file
  // Else it must be in the root config file
  $domain = "" ;
   
   
?>
