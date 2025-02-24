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
        if (Character.isLetter(ch)) {
            readName(t);
        }
        else switch (ch) {
            case ';': nextCh(); t.kind = semicolon; break;
            case '.': nextCh(); t.kind = period; break;
            case eofCh: t.kind = eof; break;
            case '=': nextCh();
                if (ch == '=') { nextCh(); t.kind = eql; } else t.kind = assign;
                break;
            case '/': nextCh();
                if (ch == '/') {
                    do nextCh(); while (ch != '\n' && ch != eofCh);
                    t = next(); // call scanner recursively
                } else t.kind = slash;
                break;
            case '\'': readCharCon(t); break;
            case '+': nextCh(); t.kind = plus; break;
            case '-': nextCh(); t.kind = minus; break;
            case '*': nextCh(); t.kind = times; break;
            case '%': nextCh(); t.kind = rem; break;
            case '!': nextCh();
                if (ch == '=') {nextCh(); t.kind = neq; break;}
                else {nextCh(); t.kind = none; break;}
            case '<': nextCh();
                if (ch == '=') {nextCh(); t.kind = leq; break;}
                else {nextCh(); t.kind = lss; break;}
            case '>': nextCh();
                if (ch == '=') {nextCh(); t.kind = geq; break;}
                else {nextCh(); t.kind = gtr; break;}
            case ',': nextCh(); t.kind = comma; break;
            case '(': nextCh(); t.kind = lpar; break;
            case ')': nextCh(); t.kind = rpar; break;
            case '[': nextCh(); t.kind = lbrack; break;
            case ']': nextCh(); t.kind = rbrack; break;
            case '{': nextCh(); t.kind = lbrace; break;
            case '}': nextCh(); t.kind = rbrace; break;
            default: nextCh(); t.kind = none; break;
        }
        return t;
    }

    private static void readName(Token t){
        while (Character.isLetterOrDigit(ch)) { // Loop through input, until a white space.
            t.val += ch;
            nextCh();
        }
        int index = 0;
        boolean keyword_found = false;
        for (String keyword : key) {
            if (keyword.equals(t.val)) {
                // as both lists are sorted we can use the index to get the code for the keyword
                t.kind = keyVal[index];
                keyword_found = true;
            }
            index++;
        }
        if (!keyword_found) { // If the name isn't a keyword, set token kind to ident
            t.kind = ident;
        }

    }

    private static void readNumber(Token t){

    }

    private static void readCharCon(Token t){

        // Read Next Character So Starts On The Character After '.
        nextCh();

        StringBuilder CharacterString = new StringBuilder();
        while (ch != eol && ch != '\'') {
            CharacterString.append(ch);
            nextCh();
        }

        // As Per Spec, Set Token Kind To charCon Regardless of Errors.
        t.kind = charCon;

        if (ch == '\'' && (!CharacterString.isEmpty())){
            // Successfully Found Start And End '.

            //If The Character String Is 1 Character Long And Not A Backslash, e.g. 'A'.
            if (CharacterString.length() == 1 && CharacterString.charAt(0) != '\\'){
                t.val = CharacterString.toString();
            }

            // If The Character String Is 2 Characters Long e.g. '\t'.
            else if(CharacterString.length() == 2 && CharacterString.charAt(0) == '\\'){
                boolean ReadEscapeSequence = true;
                switch(CharacterString.charAt(1)){
                    case '\\': t.val = "\\"; break;
                    case '\'': t.val = "'"; break;
                    case '\"': t.val = "\""; break;
                    case 't': t.val = "\t"; break;
                    case 'b': t.val = "\b"; break;
                    case 'n': t.val = "\n"; break;
                    case 'r': t.val = "\r"; break;
                    case 'f': t.val = "\f"; break;
                    default: ReadEscapeSequence = false; break;
                }

                if (!ReadEscapeSequence){
                    System.out.printf("Found Error With Character: '%s'%n", CharacterString);
                }
            }

            // If The Character String Is Anything Else e.g. 'AA'.
            else{
                System.out.printf("Found Error With Character: '%s'%n", CharacterString);
            }

            // No Ending ' Was Found Or Character String Was Empty.
        }else{
            System.out.printf("Found Error With Character: '%s'%n", CharacterString);
        }

        if (ch == '\''){
            // Read Next Character So Starts After Ending '.
            nextCh();
        }
    }

}