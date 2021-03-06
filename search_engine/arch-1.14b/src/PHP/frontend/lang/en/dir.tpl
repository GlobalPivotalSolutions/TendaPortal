<!DOCTYPE html>
<html>
<head>

<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">

<title>Arch Site Directory</title>
  <meta name="DC.Title" content="Arch Site Directory" lang="en">
  <meta name="DC.Subject" content="arch;websites;sitemap;directory" lang="en">
  <meta name="DC.Description" content="Arch automatic site directory." lang="en">

  <script type="text/javascript" src="scripts/ajax.js"></script>
  <script type="text/javascript" src="scripts/context-menu.js"></script>
  <script type="text/javascript" src="scripts/drag-drop-folder-tree.js"></script>
  <script type="text/javascript" src="scripts/modal-message.js"></script>
  <script type="text/javascript" src="scripts/arch.js"></script>
  <script type="text/javascript">
  var ajaxAddress = "ajax.php" ;
  var archAddress = "" ;
  </script>
  <link rel="stylesheet" type="text/css" href="css/arch-{$theme}.css"/>
  <link rel="alternate stylesheet" type="text/css" href="css/arch-blue.css" title="blue" />
  <link rel="alternate stylesheet" type="text/css" href="css/arch-green.css" title="green" />
  <link rel="alternate stylesheet" type="text/css" href="css/arch-purple.css" title="purple" />

  <link rel="stylesheet" type="text/css" href="css/context-menu-{$theme}.css"/>
  <link rel="alternate stylesheet" type="text/css" href="css/context-menu-blue.css" title="blue" />
  <link rel="alternate stylesheet" type="text/css" href="css/context-menu-green.css" title="green" />
  <link rel="alternate stylesheet" type="text/css" href="css/context-menu-purple.css" title="purple" />
  <link rel="stylesheet" type="text/css" href="css/drag-drop-folder-tree.css"/>
  <link rel="stylesheet" type="text/css" href="css/modal-message.css"/>
  <link rel="shortcut icon" type="image/x-icon" href="images/favicon.ico"/> 

</head>
<body>

<div class="page">

<table width="95%">
 <tr valign="middle">
   <td><h2 align='center'>Arch Site Directory</h2></td>
   <td width="100" align='center'>
   </td>
 </tr>
</table>
  <div>
  <ul id="arch_tree" class="dhtmlgoodies_tree">
    <li id="node._root_.0" site="_root_" noDrag="true" noSiblings="true"
                    noDelete="true" noRename="true" class='folder' rw="rw">
      <img src='images/plus.gif'><img src='images/folder.gif'>
      <a target='_blank' style='font-size:1em;' href="#">Indexed Sites</a>
      <UL></UL>
    </li>
  </ul>
  </div>
  <div style="padding-left:0.3cm;padding-top:0.5cm;">
      Powered by <a href="http://www.atnf.csiro.au/computing/software/arch/" style="font-size:1em;">CSIRO Arch</a>
  </div>
 {literal}    
  <script type="text/javascript">
   
      // placed here to reduce the number of places of customisation 
      function initMenu( trObj )
       {
         trObj.menuModel = new DHTMLGoodies_menuModel() ;
         trObj.menuModel.addItem(1,'Refresh','','',false,'JSTreeObj.refreshItem');
         trObj.menuModel.addItem(2,'Properties','','',false,'JSTreeObj.readProperties');
         trObj.menuModel.addItem(3,'Delete','','',false,'JSTreeObj.deleteItem');
         trObj.menuModel.init();

         initMessages( trObj ) ; // init messages too
       }

      function initMessages( trObj )
      {
        trObj.Q_DELETE = 'Delete this node?'
      }

    treeObj = new JSDragDropTree() ;
    treeObj.setTreeId( 'arch_tree' ) ;
    treeObj.setMaximumDepth( 100 ) ;
    treeObj.setMessageMaximumDepthReached('Maximum directory depth reached.') ; 
    treeObj.initTree();
    treeObj.expandAll();
    var messageObj = new DHTML_modalMessage() ;
    messageObj.setShadowOffset( 7 ) ;    // Large shadow
  </script>
 {/literal}    
</div>

</body>

</html>