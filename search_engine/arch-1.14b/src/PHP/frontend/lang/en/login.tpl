<form name='loginForm' id='loginForm'>
  <H3 align='center'>Login</H3>
  <table size=600 border=0>
  <tr><td align='right'><b>User name:</b></td><td><input size=20 type='text' value='' name='ar_fname' id='ar_fname'></td></tr>
  <tr><td align='right'><b>Password:</b></td><td><input size=20 type='password' value='' name='ar_fpass' id='ar_fpass'></td></tr>
{* Uncomment this to enable site and/or realm specific authentication  
  <tr><td align='right'><b>Realm:</b></td><td><input size=20 type='text' value='' name='ar_frealm' id='ar_frealm'></td></tr>
  <tr><td align='right'><b>Site:</b></td><td><input size=20 type='text' value='' name='ar_fsite' id='ar_fsite'></td></tr>
*}
  </table>
  <center>
  <input type='button' value='OK' onClick='return onLoginOKClick();'>
  <input type='button' value='Close' onClick='return onLoginCloseClick();'>
  </center>
  </form>