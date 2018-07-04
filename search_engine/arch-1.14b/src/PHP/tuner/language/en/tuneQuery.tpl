  </tr>
  </table>
<fieldset>
  <legend>New query</b></legend>

  <p style="margin-left: 0.5cm;">
    <b>Query:</b>&nbsp;&nbsp;<input size=40 type='text' value='{$nextQuery}' name='query'>&nbsp;
       <input type="submit" name="aa" value="Search"/>&nbsp;
    <input type="checkbox" name="publicSearch" value="on" { if $public } checked {/if} { if $publicOnly } READONLY {/if}> 
    Limit search to public pages only.
       <input type="hidden" name="lang" value="{$language}">
       <input type="hidden" name="i" value="{$i}">   
       {$sid}       
</fieldset>
</form>

</fieldset>
</div>

</body>

</html>