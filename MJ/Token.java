/* MicroJava Scanner Token  (HM 23-03-09)
   =======================
*/
package MJ;

public class Token {
    public int kind;		// token kind
    public int line;		// token line
    public int col;			// token column
    public String val;	// token value
    public int numVal;	// numeric token value (for number and charConst)
    public float fVal;
}