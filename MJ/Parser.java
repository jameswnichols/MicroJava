/*  MicroJava Parser (HM 23-03-08)
    ================
*/
package MJ;

import MJ.CodeGen.Operand;
import MJ.SymTab.Tab;

import java.util.*;
import MJ.SymTab.*;
import MJ.CodeGen.*;

public class Parser {
    private static final int  // token codes
            none      = 0,
            ident     = 1,
            number    = 2,
            charCon   = 3,
            plus      = 4,
            minus     = 5,
            times     = 6,
            slash     = 7,
            rem       = 8,
            eql       = 9,
            neq       = 10,
            lss       = 11,
            leq       = 12,
            gtr       = 13,
            geq       = 14,
            assign    = 15,
            semicolon = 16,
            comma     = 17,
            period    = 18,
            lpar      = 19,
            rpar      = 20,
            lbrack    = 21,
            rbrack    = 22,
            lbrace    = 23,
            rbrace    = 24,
            class_    = 25,
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
            eof       = 36;
    private static final String[] name = { // token names for error messages
            "none", "identifier", "number", "char constant", "+", "-", "*", "/", "%",
            "==", "!=", "<", "<=", ">", ">=", "=", ";", ",", ".", "(", ")",
            "[", "]", "{", "}", "class", "else", "final", "if", "new", "print",
            "program", "read", "return", "void", "while", "eof"
    };

    private static Token t;				// current token (recently recognized)
    private static Token la;			// lookahead token
    private static int sym;				// always contains la.kind
    public  static int errors;  	// error counter
    private static int errDist;		// no. of correctly recognized tokens since last error

    private static Obj curMethod;	// currently compiled method

    //----------- terminal first/sync sets; initialized in method parse() -----
    private static BitSet firstExpr, firstStat, syncStat, syncDecl, relop, FactorSet;

    //------------------- auxiliary methods ----------------------
    private static void scan() {
        t = la;
        la = Scanner.next();
        sym = la.kind;
        errDist++;
		/*
		System.out.print("line " + la.line + ", col " + la.col + ": " + name[sym]);
		if (sym == ident) System.out.print(" (" + la.val + ")");
		if (sym == number || sym == charCon) System.out.print(" (" + la.numVal + ")");
		System.out.println();*/
    }

    private static void check(int expected) {
        if (sym == expected) scan();
        else error(name[expected] + " expected");
    }

    public static void error(String msg) { // syntactic error at token la
        if (errDist >= 3) {
            System.out.println("-- line " + la.line + " col " + la.col + ": " + msg);
            errors++;
        }
        errDist = 0;
    }

    //-------------- parsing methods (in alphabetical order) -----------------

    // Program = "program" ident {ConstDecl | ClassDecl | VarDecl} '{' {MethodDecl} '}'.
    private static void Program() {
        Tab.openScope();
        check(program_);
        check(ident);
        while (true) {
            if (sym == final_) {
                ConstDecl();
            }
            else if (sym == ident) {
                VarDecl();
            }
            else if (sym == class_) {
                ClassDecl();
            }
            else if (sym == lbrace || sym == eof) {break;}
            else {
                error("Invalid Start of Statement");
                while (!syncDecl.get(sym)) scan();
                errDist = 0;
            }
        }
        check(lbrace);
        while (sym == ident || sym == void_) {
            MethodDecl();
        }
        check(rbrace);
        Tab.dumpScope(Tab.curScope.locals);
        Tab.closeScope();

    }

    // ConstDecl = "final" Type ident "=" (number | charConst) ";".
    private static void ConstDecl(){
        Struct type;
        check(final_);
        type = Type();
        check(ident);

        Obj obj = Tab.insert(Obj.Con, t.val, type);

        check(assign);

        if (sym == number) {
            scan();
            if (type != Tab.intType){
                error("Expected Type Char");
            }
            obj.val = t.numVal;
        }
        else if (sym == charCon) {
            scan();
            if (type != Tab.charType){
                error("Expected Type Int");
            }
            obj.val = t.numVal;
        }
        else {
            error("Invalid Constant Declaration");
        }
        check(semicolon);
    }

    // VarDecl = Type ident {"," ident } ";".
    private static void VarDecl() {

        Struct type;

        type = Type();
        check(ident);
        Tab.insert(Obj.Var, t.val, type);
        while (true) {
            if (sym == comma) {
                scan();
                check(ident);
                Tab.insert(Obj.Var, t.val, type);
            } else {
                break;
            }
        }
        check(semicolon);
    }

    // ClassDecl = "class" ident "{" {VarDecl} "}".
    private static void ClassDecl(){
        Struct type;
        check(class_);
        check(ident);

        type = new Struct(Struct.Class);
        Tab.insert(Obj.Type, t.val, type);
        Tab.openScope();

        check(lbrace);
        while (sym == ident) {
            VarDecl();
        }

        type.fields = Tab.curScope.locals;
        type.nFields = Tab.curScope.nVars;

        check(rbrace);
        Tab.closeScope();
    }

    // MethodDecl = (Type | "void") ident "(" [FormPars] ")" {VarDecl} Block.
    private static void MethodDecl(){
        Struct s = Tab.noType;
        if (sym == ident) {
            s = Type();
        } else if (sym == void_) {
            scan();
        } else {
            error("Invalid Method Declaration.");
        }
        check(ident);

        curMethod = Tab.insert(Obj.Meth, t.val, s);

        Tab.openScope();
        check(lpar);
        if (sym == ident) {
            FormPars();
        }
        curMethod.nPars = Tab.curScope.nVars;
        check(rpar);
        while (sym == ident) {
            VarDecl();
        }
        curMethod.locals = Tab.curScope.locals;
        Block();
        Tab.closeScope();
    }

    // FormPars = Type ident {"," Type ident}.
    private static void FormPars(){
        Struct s;
        s = Type();
        check(ident);
        Tab.insert(Obj.Var, t.val, s);
        while (sym == comma) {
            scan();
            Type();
            check(ident);
            Tab.insert(Obj.Var, t.val, s);
        }
    }

    // Type = ident ["[" "]"].
    private static Struct Type(){
        check(ident);

        Obj obj = Tab.find(t.val);
        if (obj.kind != Obj.Type){
            error("Type Expected.");
        }
        Struct s = obj.type;

        if (sym == lbrack) {
            scan();
            check(rbrack);
            s = new Struct(Struct.Arr, s);
        }

        return s;
    }

    // Block = "{" {Statement} "}".
    private static void Block(){
        check(lbrace);
        while (sym != rbrace && sym != eof) {
            Statement();
        }
        check(rbrace);
    }

    //
    private static void Statement(){
        if (!firstStat.get(sym)) {
            error("Invalid Start of Statement");
            while (!syncStat.get(sym)) scan();
            errDist = 0;
        }
        // Designator ("=" Expr | ActPars) ";"
        if (sym == ident) {
            Designator();

            if (sym == assign) {
                scan();
                Expr();
            }
            else if (sym == lpar) {
                ActPars();
            }
            else {error("Invalid Assignment or Call.");}

            check(semicolon);
        }
        else if (sym == if_) {
            scan();
            check(lpar);
            Condition();
            check(rpar);
            Statement();
            if (sym == else_) {
                scan();
                Statement();
            }
        }
        else if (sym == while_) {
            scan();
            check(lpar);
            Condition();
            check(rpar);
            Statement();
        }
        else if (sym == return_) {
            scan();
            if (sym == minus || sym == ident) {
                Expr();
            }
            check(semicolon);
        }
        else if (sym == read_) {
            scan();
            check(lpar);
            Designator();
            check(rpar);
            check(semicolon);
        }
        else if (sym == print_) {
            scan();
            check(lpar);
            Expr();
            if (sym == comma) {
                scan();
                check(number);
            }
            check(rpar);
            check(semicolon);
        }
        else if (sym == lbrace) {
            Block();
        }
        else if (sym == semicolon) {
            scan();
        }
    }


    // ActPars = "(" [ Expr {"," Expr} ] ")".
    private static void ActPars(){
        check(lpar);
        if (sym == minus || FactorSet.get(sym)) {
            Expr();
            while (sym == comma) {
                scan();
                Expr();
            }
        }
        check(rpar);
    }

    // Condition = Expr Relop Expr.
    private static void Condition(){
        Expr();
        Relop();
        Expr();
    }

    // Relop = "==" | "!=" | ">" | ">=" | "<" | "<=".
    private static void Relop(){
        if (relop.get(sym)) {scan();}
        else error("Invalid Comparison.");
    }

    // Expr = ["-"] Term {Addop Term}.
    private static void Expr(){
        if (sym == minus){
            scan();
        }
        Term();
        while (sym == plus || sym == minus){
            Addop();
            Term();
        }
    }

    // Term = Factor {Mulop Factor}.
    private static void Term(){
        Factor();
        while (sym == times || sym == slash || sym == rem){
            Mulop();
            Factor();
        }
    }

    //
    private static void Factor(){
        if (FactorSet.get(sym)){
            if (sym == ident){
                Designator();
                if (sym == lpar){
                    ActPars();
                }
            }
            else if (sym == number){
                scan();
            }
            else if (sym == charCon){
                scan();
            }
            else if (sym == new_){
                scan();
                check(ident);
                Obj obj = Tab.find(t.val);
                if (obj.kind != Obj.Type){
                    error("Type Expected.");
                }
                if (sym == lbrack){
                    scan();
                    Expr();
                    check(rbrack);
                }
            }
            else if (sym == lpar){
                scan();
                Expr();
                check(rpar);
            }
        } else {
            error("Invalid Expression.");
        }
    }

    // Designator = ident {"." ident | "[" Expr "]"}.
    private static void Designator(){
        Operand x,y;
        Obj obj;

        check(ident);
        obj = Tab.find(t.val);
        x = new Operand(obj);

        while (true){
            if (sym == period) {
                if (x.type.kind == Struct.Class) {
                    scan();
                    check(ident);
                    Code.load(x);
                    Obj fld = Tab.findField(t.val, x.type);
                    x.kind = Operand.Fld;
                    x.adr = fld.adr;
                    x.type = fld.type;
                }else {error(name + "Is not An Object.");}
            }else if (sym == lbrack) {
                scan();

                String name = t.val;

                Obj obj_arr = Tab.find(name);

                y = Expr(); // comment out until Expr is done.
                Code.load(x);
                if (obj.type.kind == Struct.Arr) {
                    if (y.type != Tab.intType) { error("Index Must be of Type Int");}
                    Code.load(y);
                    x.kind = Operand.Elem;
                    x.type = x.type.elemType;
                } else { error("Expected Array");}
                check(rbrack);
            } else{
                break;
            }
        }
    }

    // Addop = "+" | "-".
    private static void Addop(){
        if (sym == plus || sym == minus){
            scan();
        }else{
            error("Invalid + or - Operation.");
        }
    }

    // Mulop = "*" | "/" | "%".
    private static void Mulop(){
        if (sym == times || sym == slash || sym == rem){
            scan();
        }else{
            error("Invalid * or / or % Operation.");
        }
    }

    public static void parse() {
        BitSet s;
        // initialize first/sync sets
        s = new BitSet(64); firstExpr = s;
        s.set(ident); s.set(number); s.set(charCon); s.set(new_); s.set(lpar); s.set(minus);

        s = new BitSet(64); firstStat = s;
        s.set(ident); s.set(if_); s.set(while_); s.set(read_);
        s.set(return_); s.set(print_); s.set(lbrace); s.set(semicolon);

        s = (BitSet)firstStat.clone(); syncStat = s;
        s.clear(ident); s.set(rbrace); s.set(eof);

        s = new BitSet(64); syncDecl = s;
        s.set(final_); s.set(class_); s.set(lbrace); s.set(void_); s.set(eof);

        s =  new BitSet(64); relop = s;
        relop.set(eql); relop.set(neq); relop.set(gtr); relop.set(geq); relop.set(lss); relop.set(leq);

        s = new BitSet(64); FactorSet = s;
        FactorSet.set(ident); FactorSet.set(number); FactorSet.set(charCon); FactorSet.set(new_); FactorSet.set(lpar);

        // start parsing
        errors = 0; errDist = 3;
        scan();
        Program();
        if (sym != eof) error("end of file found before end of program");
//        if (Code.mainPc < 0) error("program contains no 'main' method");
        //Tab.dumpScope(Tab.curScope.locals);
    }

}