<td>
 <fieldset>
  <legend>{$engine} {$groups} ({$rel10}, {$relTop}) ({$av10}, {$avTop})</legend>
     {section name=i1 loop=$r}
       { if $r[i1].title neq "" }
          {$r[i1].index}. <a href="{$r[i1].link}" class="{$r[i1].style}" target="_blank">{$r[i1].title}</a><br>
       {/if}
     {/section}
</fieldset>
</td>