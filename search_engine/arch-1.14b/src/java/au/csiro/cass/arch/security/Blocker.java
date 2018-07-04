package au.csiro.cass.arch.security;

/* Pruners can be used to block certain pages. But, to make sure that such
 * blocking pruners are called before other pruners modify the page, they
 * must be instances of this class.
 */
public abstract class Blocker implements Pruner
{

}
