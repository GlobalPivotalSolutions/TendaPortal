<form name='propertiesForm' id='propertiesForm'>
<input type='hidden' id='ar_propNodeId' value='node{$site}.{$nodeId}'>
<input type='hidden' id='ar_propNodeSite' value='{$nodeSite}'>
<input type='hidden' id='ar_fname' value='{$name}'>
 
<table size=600 border=0><tr><td align='center' colspan=2><h3> Index Node Properties </h3></td></tr>

<tr><td align='right'><b>Name:</b></td><td><b>&nbsp;{$name}</b></td></tr>
<tr><td align='right'><b>Label:</b></td><td><input size=88 type='text' value='{$label}' name='ar_flabel' id='ar_flabel'></td></tr>
<tr><td align='right'><b>Description:</b></td><td><input size=88 type='text' value='{$title}' name='ar_ftitle' id='ar_ftitle'
 {$titleDisabled}></td></tr>
</table>
<P align='center' font='+1'><b>Access permissions<b></p>
<table size=600 border =0>
<tr>
  <td colspan=2 align='center'><input type='checkbox' {$inherited} name='ar_access' id='ar_access'
    onChange='accessChanged() ;'>Permissions inherited from parent folder</td>
</tr>
<tr>
   <td align='right'><b>Groups R/O</b></td>
   <td align='left'><input size=84 type='text' value='{$groupr}' name='ar_groupr' id='ar_groupr'></td>
</tr>
<tr>
   <td align='right'><b>Groups R/W</b></td>
   <td align='left'><input size=84 type='text' value='{$groupw}' name='ar_groupw' id='ar_groupw'></td>
 </tr>
 <tr>
   <td align='right'><b>Users R/O</b></td>
   <td align='left'><input size=84 type='text' value='{$userr}' name='ar_userr' id='ar_userr'></td>
 </tr>
 <tr>
   <td align='right'><b>Users R/W</b></td>
   <td align='left'><input size=84 type='text' value='{$userw}' name='ar_userw' id='ar_userw'></td>
 </tr>
 <tr>
   <td align='right'><b>Administrators</b></td>
   <td align='left'><input size=84 type='text' value='{$owners}' name='ar_owners' id='ar_owners'></td>
 </tr></table>
 <center>
 <input type='button' value='OK' onClick='return onPropertiesOKClick();'>
 <input type='button' value='Close' onClick='return onPropertiesCloseClick();'>
 </center>
 </form>
