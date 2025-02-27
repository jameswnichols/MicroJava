/*  MicroJava Parser (HM 23-03-08)
    ================
*/
package MJ;

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

        Struct type = Tab.noType;
        String name;
        int n = 0;


        if (sym == ident) {
            type = Type();
        } else if (sym == void_) {
            scan();

        } else {
            error("Invalid Method Declaration.");
        }
        if (type.isRefType()) {error("Methods may Only Return Type Int or Char");}


        check(ident);
        name = t.val;
        curMethod = Tab.insert(Obj.Meth, name, type);
        Tab.openScope();

        check(lpar);
        
        if (sym == ident) {
            n = FormPars();
        }

        check(rpar);
        curMethod.nPars = n;

        if (name.equals("main")) {
            Code.mainPc = Code.pc;
            if (curMethod.type != Tab.noType) {error("Method Main Must be Void");}
            if (curMethod.nPars != 0) {error("Main Must not Have Parameters");}
        }

        while (sym == ident) {
            VarDecl();
        }

        curMethod.locals = Tab.curScope.locals;
        curMethod.adr = Code.pc;
        Code.put(Code.enter);
        Code.put(curMethod.nPars);
        Code.put(Tab.curScope.nVars);

        Block();
        if (curMethod.type == Tab.noType) {
            Code.put(Code.exit);
            Code.put(Code.return_);
        } else {
            Code.put(Code.trap);
            Code.put(1);
        }
        Tab.closeScope();
    }

    // FormPars = Type ident {"," Type ident}.
    private static int FormPars(){
        int n = 0;
        Struct s;
        s = Type();
        check(ident);
        Tab.insert(Obj.Var, t.val, s);
        n++;
        while (sym == comma) {
            scan();
            Type();
            check(ident);
            Tab.insert(Obj.Var, t.val, s);
            n++;
        }
        return n;
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
        Operand x, y;
        if (!firstStat.get(sym)) {
            error("Invalid Start of Statement");
            while (!syncStat.get(sym)) scan();
            errDist = 0;
        }
        // Designator ("=" Expr | ActPars) ";"
        if (sym == ident) {
            x = Designator();

            if (sym == assign) {
                scan();
                y = Expr();
                if (y.type.assignableTo(x.type)){
                    Code.load(y);
                    Code.assignTo(x);
                }
                else{
                    error("Incompatible Types In Assignment");
                }
            }
            else if (sym == lpar) {
                ActPars(x);
                Code.put(Code.call);
                Code.put2(x.adr);
                if (x.type != Tab.noType){
                    Code.put(Code.pop);
                }
            }
            else {error("Invalid Assignment or Call.");}

            check(semicolon);
        }
        else if (sym == if_) {
            scan();

            check(lpar);
            int op = Condition();
            Code.putFalseJump(op, 0);
            int adr = Code.pc-2;
            check(rpar);
            Statement();
            if (sym == else_) {
                scan();
                Code.putJump(0);
                int adr2 = Code.pc -2;
                Code.fixup(adr);
                Statement();
                Code.fixup(adr2);
            }
            Code.fixup(adr);
        }
        else if (sym == while_) {
            int op;
            scan();
            int top = Code.pc;
            check(lpar);
            op = Condition();
            check(rpar);
            Code.putFalseJump(op, 0);
            int adr = Code.pc - 2;
            Statement();
            Code.putJump(top);
            Code.fixup(adr);
        }
        else if (sym == return_) {
            scan();
            if (sym == minus || sym == ident) {
                x= Expr();
                Code.load(x);
                if (curMethod.type == Tab.noType){
                    error("Void Method Must Not Return A Value");
                }
                else if (!x.type.assignableTo(curMethod.type)){
                    error("Type Of Return Value Must Match Method Type");
                }
            }
            if (curMethod.type != Tab.noType){
                error("Return Value Expected");
            }
            Code.put(Code.exit);
            Code.put(Code.return_);
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
    private static void ActPars(Operand m){
        Operand ap;
        check(lpar);
        if (m.kind != Operand.Meth) {
            error("Not a Method.");
            m.obj = Tab.noObj;
        }
        int aPars = 0;
        int fPars = m.obj.nPars;
        Obj fp = m.obj.locals;
        if (sym == minus || FactorSet.get(sym)) {
            ap = Expr();
            Code.load(ap);
            aPars++;
            if (fp != null){
                if (!ap.type.assignableTo(fp.type)) {
                    error("Parameter Type Mismatch.");
                    fp = fp.next;
                }
            }
            while (sym == comma) {
                scan();
                ap = Expr();
                Code.load(ap);
                aPars++;
                if (fp != null){
                    if (!ap.type.assignableTo(fp.type)) {
                        error("Parameter Type Mismatch.");
                        fp = fp.next;
                    }
                }
            }
        }
        if (aPars > fPars){
            error("Too Many Actual Parameters.");
        } else if (aPars < fPars) {
            error("Too Few Actual Parameters.");
        }
        check(rpar);
    }

    // Condition = Expr Relop Expr.
    private static int Condition(){
        int op;
        Operand x, y;

        x = Expr();

        Code.load(x);

        op = Relop();

        y = Expr();
        Code.load(y);

        if (!x.type.compatibleWith(y.type)) {error("Type Mismatch");}
        if (x.type.isRefType() && op != Code.eq && op != Code.ne) { error("Invalid Compare");}
        return op;
    }

    // Relop = "==" | "!=" | ">" | ">=" | "<" | "<=".
    // eql       = 9,
    //            neq       = 10,
    //            lss       = 11,
    //            leq       = 12,
    //            gtr       = 13,
    //            geq       = 14,
    private static int Relop(){
        // relop.set(eql); relop.set(neq); relop.set(gtr); relop.set(geq); relop.set(lss); relop.set(leq);
        switch (sym) {
            case 9: // eql
                scan();
                return 0; // 0 == eql in codegen
            case 10: // not eql
                scan();
                return 1;
            case 11: //less
                scan();
                return 2;
            case 12: // less or equal
                scan();
                return 3;
            case 13: // greater
                scan();
                return 4;
            case 14: // greater or equal
                scan();
                return 5;
            default:
                error("Invalid Comparison");
                return -1;
        }
    }

    // Expr = ["-"] Term {Addop Term}.
    private static Operand Expr(){
        Operand x, y;
        int op;
        boolean Minus = false;

        if (sym == minus){
            scan();
            Minus = true;
        }
        x = Term();
        if (Minus) {
            if (x.type != Tab.intType) {
                error("Operand Must be of Type Int.");
            }
            if (x.kind == Operand.Con){
                x.val = -x.val;
            }
            else {
                Code.load(x);
                Code.put(Code.neg);
            }
        }

        while (sym == plus || sym == minus){
            op = Addop();
            Code.load(x);
            y = Term();
            Code.load(y);
            if (x.type != Tab.intType || y.type != Tab.intType) {
                error("Operands Must be of Type Int.");
            }
            Code.put(op);
        }
        return x;
    }

    // Term = Factor {Mulop Factor}.
    private static Operand Term(){
        Operand x, y;
        int op;

        x = Factor();
        while (sym == times || sym == slash || sym == rem){
            op = Mulop();
            Code.load(x);
            y = Factor();
            Code.load(y);
            if (x.type != Tab.intType || y.type != Tab.intType){
                error("Operands Must Be Of Type Int");
            }
            Code.put(op);
        }
        return x;
    }

    //
    private static Operand Factor(){
        Operand x;

        if (sym == ident){
            x = Designator();
            if (sym == lpar){
                ActPars(x);
                if (x.type == Tab.noType){
                    error("Procedural Called As Function");
                }
                if (x.obj == Tab.ordObj || x.obj == Tab.chrObj){}
                else if (x.obj == Tab.lenObj){
                    Code.put(Code.arraylength);
                }
                else{
                    Code.put(Code.call);
                    Code.put2(x.adr);
                }
                x.kind = Operand.Stack;
            }
        }
        else if (sym == number){
            scan();
            x = new Operand(t.numVal);
        }
        else if (sym == charCon){
            scan();
            x = new Operand(t.numVal);
            x.type = Tab.charType;
        }
        else if (sym == new_){
            scan();
            check(ident);
            Obj obj = Tab.find(t.val);
            Struct type = obj.type;

            if (sym == lbrack){
                scan();
                if (obj.kind != Obj.Type){
                    error("Type Expected");
                }
                x = Expr();
                check(rbrack);
                if (x.type != Tab.intType){
                    error("Array Size Must Be Of Type Int");
                }
                Code.load(x);
                Code.put(Code.newarray);
                if (type == Tab.charType){
                    Code.put(0);
                }
                else{
                    Code.put(1);
                }
                type = new Struct(Struct.Arr, type);
            }
            else {
                if (obj.kind != Obj.Type || type.kind != Struct.Class) {
                    error("Class Type Expected");
                }
                Code.put(Code.new_);
                Code.put2(type.nFields);
            }
            x = new Operand(Operand.Stack, 0, type);
        }
        else if (sym == lpar){
            scan();
            x = Expr();
            check(rpar);
        }
        else {
            error("Invalid Factor.");
            x = new Operand(Operand.Stack);
        }


        return x;
    }

    // Designator = ident {"." ident | "[" Expr "]"}.
    private static Operand Designator(){
        String name;
        Operand x,y;
        Obj obj;

        check(ident);
        name = t.val;
        obj = Tab.find(name);

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

                Code.load(x);

                y = Expr(); // comment out until Expr is done.

                if (x.type.kind == Struct.Arr) {
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
        return x;
    }

    // Addop = "+" | "-".
    private static int Addop(){
        if (sym == plus || sym == minus){
            scan();
            switch (t.kind){
                case plus: return Code.add;
                case minus: return Code.sub;
            }
        }else{
            error("Invalid + or - Operation.");
        }
        return -1;
    }

    // Mulop = "*" | "/" | "%".
    private static int Mulop(){
        if (sym == times || sym == slash || sym == rem){
            scan();
            switch (t.kind){
                case times: return Code.mul;
                case slash: return Code.div;
                case rem: return Code.rem;
            }
        }else{
            error("Invalid * or / or % Operation.");
        }
        return -1;
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