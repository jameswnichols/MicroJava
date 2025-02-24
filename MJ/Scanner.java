/* MicroJava Scanner (HM 23-03-09)
   =================
*/
package MJ;
import java.io.*;

public class Scanner {
    private static final char eofCh = '\u0080';
    private static final char eol = '\n';
    private static final int  // token codes
            none      = 0,  // error token
            ident     = 1,  // identifier
            number    = 2,  // number
            charCon   = 3,  // character constant
            plus      = 4,  // +
            minus     = 5,  // -
            times     = 6,  // *
            slash     = 7,  // /
            rem       = 8,  // %
            eql       = 9,  // ==
            neq       = 10, // !=
            lss       = 11, // <
            leq       = 12, // <=
            gtr       = 13, // >
            geq       = 14, // >=
            assign    = 15, // =
            semicolon = 16, // ;
            comma     = 17, // ,
            period    = 18, // .
            lpar      = 19, // (
            rpar      = 20, // )
            lbrack    = 21, // [
            rbrack    = 22, // ]
            lbrace    = 23, // {
            rbrace    = 24, // }
            class_    = 25, // ... keywords ...
            else_     = 26,
            final_    = 27,
            if_       = 28,
            new_      = 29,
            print_    = 30,
            program_  = 31,
            read_     = 32,
            return_   = 33,
            void_     = 34,
            while_    = 35,
            eof       = 36; // end-of-file token

    private static final String key[] = { // sorted list of keywords
            "class", "else", "final", "if", "new", "print",
            "program", "read", "return", "void", "while"
    };
    private static final int keyVal[] = {
            class_, else_, final_, if_, new_, print_,
            program_, read_, return_, void_, while_
    };

    private static char ch;			// lookahead character
    public  static int col;			// current column
    public  static int line;		// current line
    private static Reader in;  	// source file reader
    private static char[] lex;	// current lexeme (token string)

    //----- ch = next input character
    private static void nextCh() {
        try {
            ch = (char)in.read(); col++;
            if (ch == eol) {line++; col = 0;}
            else if (ch == '\uffff') ch = eofCh;
        } catch (IOException e) {
            ch = eofCh;
        }
    }

    //--------- Initialize scanner
    public static void init(Reader r) {
        in = new BufferedReader(r);
        lex = new char[128];
        line = 1; col = 0;
        nextCh();
    }

    //---------- Return next input token
    public static Token next() {
        while (ch <= ' ') nextCh(); // skip blanks, tabs, eols
        Token t = new Token(); t.line = line; t.col = col;
        switch (ch) {
//            case 'a': case 'b': ... case 'z': case 'A': case 'B': ... case 'Z':
//                readName(t); break;
//            case '0': case '1': ... case '9':
//                readNumber(t); break;
            case ';': nextCh(); t.kind = semicolon; break;
            case '.': nextCh(); t.kind = period; break;
            case eofCh: t.kind = eof; break; // no nextCh() any more
            case '=': nextCh();
                if (ch == '=') { nextCh(); t.kind = eql; } else t.kind = assign;
                break;
            case '/': nextCh();
                if (ch == '/') {
                    do nextCh(); while (ch != '\n' && ch != eofCh);
                    t = next(); // call scanner recursively
                } else t.kind = slash;
                break;
            default: nextCh(); t.kind = none; break;
        }
        return t;
    }

    private static void readName(Token t){

    }

    private static void readNumber(Token t){

    }

    private static void readCharCon(Token t){

    }

}