/* MicroJava Symbol Table Scopes (HM 23-03-08)
   =============================
*/
package MJ.SymTab;

public class Scope {
    public Scope outer;		// to outer scope
    public Obj   locals;	// to local variables of this scope
    public int   nVars;   // number of variables in this scope
}