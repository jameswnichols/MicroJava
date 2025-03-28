// MicroJava Virtual Machine
// -------------------------
// Syntax: java MJ.Run fileName [-debug]
//=============================================================================
package MJ;

import java.io.*;
import java.util.concurrent.ThreadLocalRandom;

public class Run {
    static byte code[];			// code array
    static int data[];			// global data
    static int heap[];			// dynamic heap
    static int stack[];			// expression stack
    static int local[];			// method stack
    static int dataSize;		// size of global data area
    static int startPC;			// address of main() method
    static int pc;					// program counter
    static int fp, sp;			// frame pointer, stack pointer on method stack
    static int esp;					// expression stack pointer
    static int free;				// next free heap address
    static boolean debug;		// debug output on or off

    static final int
            heapSize = 100000,		// size of the heap in words
            mStackSize = 50000,			// size of the method stack in words
            eStackSize = 50000;			// size of the expression stack in words

    static final int				// instruction codes
            load        =  1,
            load0       =  2,
            load1       =  3,
            load2       =  4,
            load3       =  5,
            store       =  6,
            store0      =  7,
            store1      =  8,
            store2      =  9,
            store3      = 10,
            getstatic   = 11,
            putstatic   = 12,
            getfield    = 13,
            putfield    = 14,
            const0      = 15,
            const1      = 16,
            const2      = 17,
            const3      = 18,
            const4      = 19,
            const5      = 20,
            const_m1    = 21,
            const_      = 22,
            add         = 23,
            sub         = 24,
            mul         = 25,
            div         = 26,
            rem         = 27,
            neg         = 28,
            shl         = 29,
            shr         = 30,
            new_        = 31,
            newarray    = 32,
            aload       = 33,
            astore      = 34,
            baload      = 35,
            bastore     = 36,
            arraylength = 37,
            pop         = 38,
            jmp         = 39,
            jeq         = 40,
            jne         = 41,
            jlt         = 42,
            jle         = 43,
            jgt         = 44,
            jge         = 45,
            call        = 46,
            return_     = 47,
            enter       = 48,
            exit        = 49,
            read        = 50,
            print       = 51,
            bread       = 52,
            bprint      = 53,
            trap		    = 54,
            rand        = 55,
            readc       = 56;

    static final int  // compare operators
            eq = 0,
            ne = 1,
            lt = 2,
            le = 3,
            gt = 4,
            ge = 5;

    static String[] opcode = {
            "???     ", "load    ", "load0   ", "load1   ", "load2   ",
            "load3   ", "store   ", "store0  ", "store1  ", "store2  ",
            "store3  ", "getstati", "putstati", "getfield", "putfield",
            "const0  ", "const1  ", "const2  ", "const3  ", "const4  ",
            "const5  ", "constm1 ", "const   ", "add     ", "sub     ",
            "mul     ", "div     ", "rem     ", "neg     ", "shl     ",
            "shr     ", "new     ", "newarray", "aload   ", "astore  ",
            "baload  ", "bastore ", "arraylen", "pop     ", "jmp     ",
            "jeq     ", "jne     ", "jlt     ", "jle     ", "jgt     ",
            "jge     ", "call    ", "return  ", "enter   ", "exit    ",
            "read    ", "print   ", "bread   ", "bprint  ", "trap    ",
            "rand    ", "readc   "
    };

    //----- expression stack

    static void push(int val) throws VMError {
        if (esp == eStackSize) throw new VMError("expression stack overflow");
        stack[esp++] = val;
    }

    static int pop() throws VMError {
        if (esp == 0) throw new VMError("expression stack underflow");
        return stack[--esp];
    }

    //----- method stack

    static void PUSH(int val) throws VMError {
        if (sp == mStackSize) throw new VMError("method stack overflow");
        local[sp++] = val;
    }

    static int POP() throws VMError {
        if (sp == 0) throw new VMError("method stack underflow");
        return local[--sp];
    }

    //----- instruction fetch

    static byte next() {
        return code[pc++];
    }

    static short next2() {
        return (short)(((next() << 8) + (next() & 0xff)) << 16 >> 16);
    }

    static int next4() {
        return (next2() << 16) + (next2() & 0xffff);
    }

    //----- VM internals

    static void load(String name) throws IOException, FormatException {
        int codeSize;
        byte sig[] = new byte[2];
        DataInputStream in = new DataInputStream(new FileInputStream(name));
        in.read(sig, 0, 2);
        if (sig[0] != 'M' || sig[1] != 'J') throw new FormatException("wrong marker");
        codeSize = in.readInt();
        if (codeSize <= 0) throw new FormatException("codeSize <= 0");
        dataSize = in.readInt();
        if (dataSize < 0) throw new FormatException("dataSize < 0");
        startPC = in.readInt();
        if (startPC < 0 || startPC >= codeSize) throw new FormatException("startPC out of code area");
        code = new byte[codeSize];
        in.read(code, 0, codeSize);
    }

    static int alloc(int size) throws VMError { // allocate heap block of size bytes
        int adr = free;
        free += size;
        if (free > heapSize) throw new VMError("heap overflow");
        return adr;
    }

    static byte getByte(int val, int n) { // retrieve byte n from val. Byte 0 is MSB
        return (byte)(val << (8*n) >>> 24);
    }

    static int setByte(int val, int n, byte b) { // replace byte n in val by b
        int delta = (3 - n) * 8;
        int mask = ~(255 << delta); // mask all 1 except on chosen byte
        int by = (((int)b) & 255) << delta;
        return (val & mask) ^ by;
    }

    static int readInt() throws IOException { // read int from standard input stream
        int val = 0;
        int prev = ' ';
        int b = System.in.read();
        while (b < '0' || b > '9') {
            prev = b; b = System.in.read();
        }
        while (b >= '0' && b <= '9') {
            val = 10 * val + b - '0';
            b = System.in.read();
        }
        if (prev == '-') val = -val;
        return val;
    }

    static int readChar() throws IOException {
        return System.in.read();
    }

    //----- debug output

    static void printNum(int val, int n) {
        String s = new Integer(val).toString();
        int len = s.length();
        while (len < n) {System.out.print(" "); len++;}
        System.out.print(s);
    }

    static int rand(int rmin, int rmax){
        return ThreadLocalRandom.current().nextInt(rmin, rmax + 1);
    }

    static void printInstr() {
        int op = code[pc - 1];
        String instr = op > 0 && op <= trap ? opcode[op] : "???     ";
        printNum(pc - 1, 4);
        System.out.print(": " + instr + "| ");
    }

    static void printStack() {
        for (int i = 0; i < esp; i++) System.out.print(stack[i] + " ");
        System.out.println();
    }

    //----- actual interpretation

    static void interpret() {
        int op, adr, val, val2, off, idx, len, i, ischar;
        pc = startPC;
        try {
            for (;;) { // terminated by return instruction
                op = next();
                if (debug) printInstr();
                switch((int)op) {

                    // load/store local variables
                    case load:
                        push(local[fp + next()]);
                        break;
                    case load0: case load1: case load2: case load3:
                        op -= load0; // mapping on range 0..3
                        push(local[fp + op]);
                        break;
                    case store:
                        local[fp + next()] = pop();
                        break;
                    case store0: case store1: case store2: case store3:
                        op -= store0; // mapping on range 0..3
                        local[fp + op] = pop();
                        break;

                    // load/store global variables
                    case getstatic:
                        push(data[next2()]);
                        break;
                    case putstatic:
                        data[next2()] = pop();
                        break;

                    // load/store object fields
                    case getfield:
                        adr = pop();
                        if (adr == 0) throw new VMError("null reference used");
                        push(heap[adr + next2()]);
                        break;
                    case putfield:
                        val = pop();
                        adr = pop();
                        if (adr == 0) throw new VMError("null reference used");
                        heap[adr + next2()] = val;
                        break;

                    // load constants
                    case const0: case const1: case const2: case const3: case const4: case const5:
                        push(op - const0); // map opcode to 0..5
                        break;
                    case const_m1:
                        push(-1);
                        break;
                    case const_:
                        push(next4());
                        break;

                    // arithmetic operations
                    case add:
                        push(pop() + pop());
                        break;
                    case sub:
                        push(-pop() + pop());
                        break;
                    case mul:
                        push(pop() * pop());
                        break;
                    case div:
                        val = pop();
                        if (val == 0) throw new VMError("division by zero");
                        push(pop() / val);
                        break;
                    case rem:
                        val = pop();
                        if (val == 0) throw new VMError("division by zero");
                        push(pop() % val);
                        break;
                    case neg:
                        push(-pop());
                        break;
                    case shl:
                        val = pop();
                        push(pop() << val);
                        break;
                    case shr:
                        val = pop();
                        push(pop() >> val);
                        break;

                    // object creation
                    case new_:
                        push(alloc(next2()));
                        break;
                    case newarray:
                        val = next();
                        len = pop();
                        if (val == 0) adr = alloc(1 + ((len+3)>>2)); else adr = alloc(1 + len);
                        heap[adr] = len;
                        push(adr);
                        break;

                    // array access
                    case aload:
                        idx = pop();
                        adr = pop();
                        if (adr == 0) throw new VMError("null reference used");
                        len = heap[adr];
                        if (idx < 0 || idx >= len) throw new VMError("index out of bounds");
                        push(heap[adr+1+idx]);
                        break;
                    case astore:
                        val = pop();
                        idx = pop();
                        adr = pop();
                        if (adr == 0) throw new VMError("null reference used");
                        len = heap[adr];
                        if (idx < 0 || idx >= len) throw new VMError("index out of bounds");
                        heap[adr+1+idx] = val;
                        break;
                    case baload:
                        idx = pop();
                        adr = pop();
                        if (adr == 0) throw new VMError("null reference used");
                        len = heap[adr];
                        if (idx < 0 || idx >= len) throw new VMError("index out of bounds");
                        push(getByte(heap[adr + 1 + idx/4], idx % 4));
                        break;
                    case bastore:
                        val = pop();
                        idx = pop();
                        adr = pop();
                        if (adr == 0) throw new VMError("null reference used");
                        len = heap[adr];
                        if (idx < 0 || idx >= len) throw new VMError("index out of bounds");
                        heap[adr + 1 + idx/4] = setByte(heap[adr + 1 + idx/4], idx % 4, (byte)val);
                        break;
                    case arraylength:
                        adr = pop();
                        if (adr==0) throw new VMError("null reference used");
                        push(heap[adr]);
                        break;

                    // stack manipulation
                    case pop:
                        pop();
                        break;

                    // jumps
                    case jmp:
                        adr = next2();
                        pc = adr;
                        break;
                    case jeq: case jne: case jlt: case jle: case jgt: case jge:
                        adr = next2();
                        val2 = pop(); val = pop();
                        boolean cond = false;
                        switch(op) {
                            case jeq: cond = val == val2; break;
                            case jne: cond = val != val2; break;
                            case jlt: cond = val < val2;  break;
                            case jle: cond = val <= val2; break;
                            case jgt: cond = val > val2;  break;
                            case jge: cond = val >= val2; break;
                        }
                        if (cond) pc = adr;
                        break;

                    // method calls
                    case call:
                        adr = next2();
                        PUSH(pc);
                        pc = adr;
                        break;
                    case return_:
                        if (sp == 0) return; else pc = POP();
                        break;
                    case enter:
                        int psize = next();
                        int lsize = next();
                        PUSH(fp);
                        fp = sp;
                        for (i = 0; i < lsize; i++) PUSH(0);
                        for (i = psize - 1; i >= 0; i--) local[fp + i] = pop();
                        break;
                    case exit:
                        sp = fp;
                        fp = POP();
                        break;

                    // IO
                    case read:
                        try {
                            val = readInt();
                            push(val);
                        } catch (IOException ex) {
                            throw new VMError("end of input");
                        }
                        break;
                    case readc:
                        try {
                            val = readChar();
                            push(val);
                        } catch (IOException ex) {
                            throw new VMError("end of input");
                        }
                        break;
                    case print:
                        ischar = pop();
                        len = pop();
                        val = pop();

                        String s = new Integer(val).toString();

                        if (ischar == 1){
                            char v = (char) val;
                            s = Character.toString(v);
                        }
                        len = len - s.length();
                        for (i = 0; i < len; i++) System.out.print(' ');
                        for (i = 0; i < s.length(); i++) System.out.print(s.charAt(i));
                        break;
                    case rand:
                        int min, max;
                        max = pop();
                        min = pop();
                        push(rand(min, max));
                        break;

                    case bread:
                        try {
                            push(System.in.read());
                        } catch (IOException ex) {
                            throw new VMError("end of input");
                        }
                        break;
                    case bprint:
                        len = pop() - 1;
                        val = pop();
                        for (i = 0; i < len; i++) System.out.print(' ');
                        System.out.print((char)val);
                        break;
                    case trap:
                        throw new VMError("trap(" + next() + ")");
                    default:
                        throw new VMError("wrong opcode " + op);
                }
                if (debug) printStack();
            }
        } catch (VMError e) {
            System.out.println("\n-- exception at address " + (pc-1) + ": " + e.getMessage());;
        }
    }

    public static void main(String[] arg) {
        String fileName = null;
        debug = false;
        for (int i = 0; i < arg.length; i++) {
            if (arg[i].equals("-debug")) debug = true;
            else fileName = arg[i];
        }
        if (fileName == null) {
            System.out.println("Syntax: java MJ.Run filename [-debug]");
            return;
        }
        try {
            load(fileName);
            heap  = new int[heapSize];			// fixed sized heap
            data  = new int[dataSize];			// global data as specified in classfile
            stack = new int[eStackSize];		// expression stack
            local = new int[mStackSize];		// method stack
            fp = 0; sp = 0;
            esp = 0;
            free = 1;												// no block should start at address 0
            long startTime = System.currentTimeMillis();
            interpret();
            System.out.print("\nCompletion took " + (System.currentTimeMillis()-startTime) + " ms");
        } catch (FileNotFoundException e) {
            System.out.println("-- file " + fileName + " not found");
        } catch (IOException e) {
            System.out.println("-- error reading file " + fileName);
        } catch (FormatException e) {
            System.out.println("-- corrupted object file " + fileName + ": " + e.getMessage());
        }
    }
}

class FormatException extends Exception {
    FormatException(String s) { super(s); }
}

class VMError extends Exception {
    VMError(String s) { super(s); }
}