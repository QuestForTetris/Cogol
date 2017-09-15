import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

/**
 * @author PhiNotPi
 * 
 */
public class Compiler {

  /**
   * Fill in your desired source filename
   * 
   * @param args
   * @throws FileNotFoundException
   */
  public static void main(String[] args) throws FileNotFoundException {
    String sourcefile = "tetris.cgl";
    if (args.length > 0) {
      sourcefile = args[0];
    }
    Scanner in = new Scanner(new File(sourcefile));
    String outputfile = "tetris.qftasm";
    if (args.length > 1) {
      outputfile = args[1];
    }
    PrintWriter out = new PrintWriter(new File(outputfile));

    System.out.println("\nSource Cogol:");
    while (in.hasNextLine()) {
      String line = in.nextLine();
      System.out.println(line);
      tokenize(line);
    }
    in.close();
    System.out.println("\nTokens:");
    System.out.println(tokens);

    compile();
    joinParts();
    fillTags();
    simplify();
    adjustJumps();
    System.out.println("\nCompiled QFTASM:");
    for (int i = 0; i < mainROM.size(); i++) {
      System.out.println(i + ". " + mainROM.get(i));
      if (i > 0) {
        out.println();
      }
      out.print(i + ". " + mainROM.get(i));
    }

    System.out.println("\nRAM map:");
    for (int i = 0; i < RAMmap.size(); i++) {
      System.out.println(i + ": " + RAMmap.get(i));
    }

    for (String sub : subroutine.keySet()) {
      System.out.println("\n" + sub + " map:");
      for (int i = 0; i < subroutine.get(sub).RAMmap.size(); i++) {
        System.out.println(i + ": " + subroutine.get(sub).RAMmap.get(i));
      }
    }

    out.close();
  }

  static ArrayList<String> tokens = new ArrayList<String>();
  // characters that are their own token
  static final String singletons = ",.:;{}()[]$\\";
  // characters that are their own tokens, but repetitions and combinations are
  // grouped
  static final String reps = "<>&|!=+-^*";
  // separators, repetition is ignored
  static final String seps = " \t";
  static final String digits = "0123456789";
  static final String forbid = "~`@%/'";

  /**
   * splits text into tokens, appended to list is magic
   * 
   * @param input
   */
  public static void tokenize(String input) {
    char[] inchars = input.toLowerCase().toCharArray();
    // accumulator of characters for the current token
    String curtoken = "";
    // whether currently in quotes
    boolean quote = false;
    for (int i = 0; i < inchars.length; i++) {
      String curchar = Character.toString(inchars[i]);
      if (forbid.contains(curchar)) {
        System.err.println("error: forbidden character " + curchar);
      }
      if (quote) {
        if (curchar.equals("\"")) {
          // exiting quotes
          tokens.add(curtoken);
          curtoken = "";
          quote = false;
        } else {
          curtoken += curchar;
        }
      } else if (curchar.equals("#")) {
        // assumes tokenize is called seperately on each line
        break;
      } else if (curchar.equals("\"")) {
        // entering quotes
        quote = true;
      } else if (curchar.equals("-")) {
        if (curtoken.length() > 0) {
          tokens.add(curtoken);
          curtoken = "";
        }
        if (tokens.size() > 0) {
          String prev = tokens.get(tokens.size() - 1);
          if (prev.endsWith("-") || prev.endsWith("+")) {
            tokens.set(tokens.size() - 1, prev + "-");
          } else {
            tokens.add(curchar);
          }
        } else {
          tokens.add(curchar);
        }
      } else if (singletons.contains(curchar)) {
        // character is its own token
        if (curtoken.length() > 0) {
          tokens.add(curtoken);
          curtoken = "";
        }
        tokens.add(curchar);
      } else if (reps.contains(curchar)) {
        // character is its own token, repeated
        if (curtoken.length() > 0) {
          tokens.add(curtoken);
          curtoken = "";
        }
        String prevToken = null;
        if (tokens.size() > 0) {
          prevToken = tokens.get(tokens.size() - 1);
        }
        if (prevToken != null
            && reps.contains(prevToken.substring(prevToken.length() - 1))) {
          tokens.set(tokens.size() - 1, prevToken + curchar);
        } else {
          if (curtoken.length() > 0) {
            tokens.add(curtoken);
            curtoken = "";
          }
          tokens.add(curchar);
        }
      } else if (seps.contains(curchar) || curchar.equals("\n")) {
        // separators
        if (curtoken.length() > 0) {
          tokens.add(curtoken);
          curtoken = "";
        }
      } else {
        // accumulate
        if (digits.contains(curchar)) {
          if (tokens.size() > 1) {
            String yest = tokens.get(tokens.size() - 1);
            String ereyest = tokens.get(tokens.size() - 2)
                .substring(tokens.get(tokens.size() - 2).length() - 1);
            if (curtoken.equals("") && yest.equals("-")
                && (reps + seps).contains(ereyest)) {
              curtoken = tokens.remove(tokens.size() - 1);
            }
          }
        }
        curtoken += curchar;
      }
    }
    if (curtoken.length() > 0) {
      // end of string reached, whatever's left is a token
      tokens.add(curtoken);
      curtoken = "";
    }
  }

  static final Map<String, Integer> address = new HashMap<String, Integer>();
  static int firstFreeRAM = 0;
  static final ArrayList<Command> mainROM = new ArrayList<Command>();
  static final ArrayList<Command> ROMpredefs = new ArrayList<Command>();
  static final Map<String, String> type = new HashMap<String, String>();
  static final ArrayList<String> RAMmap = new ArrayList<String>();
  static final String ProgramCounter = "pc";
  static final String CallStackPointer = "call";
  static final String stdout = "display";
  static final String wordType = "word";
  static final String arrayType = "array";
  static final Set<String> reserved = new HashSet<String>();
  static String[] scratch = new String[1];
  static final ArrayList<OpenLoop> loops = new ArrayList<OpenLoop>();
  static final ArrayList<Subroutine> subs = new ArrayList<Subroutine>();
  static final Map<String, Subroutine> subroutine = new HashMap<String, Subroutine>();

  /**
   * @param name
   *          Name of word-type variable to create.
   */
  public static void createWord(String name) {
    setRAM(firstFreeRAM, name);
    type.put(name, wordType);
    firstFreeRAM++;
  }

  /**
   * @param name
   *          Name of word-type variable to create.
   * @param data
   *          Initializer constant.
   */
  public static void createWord(ArrayList<Command> ROM, String name,
      int data) {
    setRAM(firstFreeRAM, name);
    type.put(name, wordType);
    if (data != 0) {
      ROM.add(new Command("MLZ", new Arg(-1), new Arg(data),
          new Arg(firstFreeRAM)));
    }
    firstFreeRAM++;
  }

  /**
   * @param name
   *          Name of array-type variable to create
   * @param size
   *          Reserved size of array
   */
  public static void createArray(ArrayList<Command> ROM, String name,
      int size) {
    setRAM(firstFreeRAM, name);
    type.put(name, arrayType);
    firstFreeRAM++;
    for (int i = 0; i < size; i++) {
      setRAM(firstFreeRAM, name + "[" + i + "]");
      firstFreeRAM++;
    }
    ROM.add(new Command("MLZ", new Arg(-1), new Arg(address.get(name) + 1),
        new Arg(address.get(name))));
  }

  /**
   * @param name
   *          Name of array-type variable to create
   * @param size
   *          Reserved size of array
   * @param data
   *          Initializer constants
   */
  public static void createArray(ArrayList<Command> ROM, String name, int size,
      ArrayList<Integer> data) {
    setRAM(firstFreeRAM, name);
    type.put(name, arrayType);
    firstFreeRAM++;
    for (int i = 0; i < size; i++) {
      setRAM(firstFreeRAM, name + "[" + i + "]");
      if (data.size() > 0) {
        int datum = data.remove(0);
        if (datum != 0) {
          ROM.add(new Command("MLZ", new Arg(-1), new Arg(datum),
              new Arg(firstFreeRAM)));
        }
      }
      firstFreeRAM++;
    }
    int marker = firstFreeRAM;
    int index = size;
    while (data.size() > 0) {
      setRAM(marker, name + "[" + index + "]");
      int datum = data.remove(0);
      if (datum != 0) {
        ROM.add(
            new Command("MLZ", new Arg(-1), new Arg(datum), new Arg(marker)));
      }
      marker++;
      index++;
    }
    ROM.add(new Command("MLZ", new Arg(-1), new Arg(address.get(name) + 1),
        new Arg(address.get(name))));
  }

  /**
   * Creates name <-> location associations multiple names can be assigned to a
   * single location
   * 
   * @param marker
   *          Location of RAM to assign
   * @param name
   *          Variable name to assign to that location
   */
  static void setRAM(int marker, String name) {
    address.put(name, marker);
    while (RAMmap.size() - 1 < marker) {
      RAMmap.add("");
    }
    if (RAMmap.get(marker).equals("")) {
      RAMmap.set(marker, name);
    } else {
      RAMmap.set(marker, RAMmap.get(marker) + " " + name);
    }
  }

  // create hard-coded or system-reserved addresses
  static {
    createWord(ProgramCounter);
    reserved.add(ProgramCounter);
    createWord(stdout);
    for (int i = 0; i < scratch.length; i++) {
      scratch[i] = "free";
      createWord("scratch" + i);
      reserved.add("scratch" + i);
    }
    reserved.add(CallStackPointer);
  }

  static CallStatement prevCall;

  /**
   * Main compiler loop Iterates over each statement, identifies the statement
   * type, and then calls a more specialized compiler method
   */
  public static void compile() {
    ArrayList<CallStatement> calls = new ArrayList<CallStatement>();

    mainROM.add(new Command("MLZ", new Arg(-1), new Arg(CallStackPointer, 1),
        new Arg(CallStackPointer, 0), "preloadCallStack"));

    while (tokens.size() > 0) {
      clearS();
      if (tokens.get(0).equals("call")) {
        CallStatement call = new CallStatement(mainROM.size(),
            rmStatementTokens());
        if (call.pointerName() != null) {
          Subroutine isLocal = null;
          for (int i = subs.size() - 1; i >= 0; i--) {
            if (subs.get(i).args.contains(call.pointerName())) {
              isLocal = subs.get(i);
            }
          }
          if (call.returnsPointer) {
            if (isLocal == null) {
              type.put(call.pointerName(), call.subName());
            } else {
              isLocal.type.put(call.pointerName(), call.subName());
            }
          }
        }
        calls.add(0, call);
        prevCall = call;
      } else {
        int startsize = mainROM.size();
        if (tokens.get(0).equals("my")) {
          compileDef();
        } else if (tokens.get(0).equals("if")
            || tokens.get(0).equals("while")) {
          compileLoopStart(mainROM);
        } else if (tokens.get(0).equals("do")) {
          compileDoWhile(mainROM);
        } else if (tokens.get(0).equals("sub")) {
          compileSub(mainROM);
        } else if (tokens.get(0).equals("}")) {
          compileLoopStop(mainROM);
        } else if (tokens.get(0).equals("return")) {
          compileReturn(mainROM);
          mainROM.get(mainROM.size() - 1).tags.add("return");
        } else {
          compileMove(mainROM);
        }
        if (mainROM.size() > startsize) {
          prevCall = null;
        }
      }
    }
    for (int i = 0; i < calls.size(); i++) {
      clearS();
      compileCall(mainROM, calls.get(i));
    }
    setRAM(firstFreeRAM, CallStackPointer);
    type.put(CallStackPointer, arrayType);
    firstFreeRAM++;
  }

  static class CallStatement {
    int loc;
    ArrayList<String> statement;
    ArrayList<Subroutine> cursubs = new ArrayList<Subroutine>();
    ArrayList<String> tags = new ArrayList<String>();
    boolean returnsPointer;

    public CallStatement(int loc, ArrayList<String> statement) {
      super();
      this.loc = loc;
      this.statement = statement;
      cursubs.addAll(subs);
      returnsPointer = statement.lastIndexOf(")") >= statement
          .lastIndexOf(".");
    }

    String pointerName() {
      if (eqIndex() > -1) {
        return statement.get(1);
      } else {
        return null;
      }
    }

    String subName() {
      int eqloc = eqIndex();
      if (eqloc > -1) {
        return statement.get(eqloc + 1);
      } else {
        return statement.get(1);
      }
    }

    int eqIndex() {
      for (int i = 0; i < statement.size(); i++) {
        if (statement.get(i).endsWith("=")) {
          return i;
        }
      }
      return -1;
    }

  }

  /**
   * Add the constant initializer code to the start of the program This can be
   * replaced by more complex joining code
   */
  public static void joinParts() {
    mainROM.addAll(0, ROMpredefs);
  }

  /**
   * Fills in argument values based on tags
   */
  public static void fillTags() {
    Map<String, Integer> tagLocs = new HashMap<String, Integer>();
    for (int i = 0; i < mainROM.size(); i++) {
      Command c = mainROM.get(i);
      for (String tag : c.tags) {
        tagLocs.put(tag, i);
      }
    }
    for (int i = 0; i < mainROM.size(); i++) {
      Command c = mainROM.get(i);
      fillTags(c.arg1, tagLocs);
      fillTags(c.arg2, tagLocs);
      fillTags(c.arg3, tagLocs);
    }
  }

  static void fillTags(Arg arg, Map<String, Integer> tagLocs) {
    if (arg.sub == null || arg.sub.equals("")) {
      if (address.containsKey(arg.tag)) {
        arg.val = address.get(arg.tag) + arg.tagoffset;
      }
    } else {
      Subroutine s = subroutine.get(arg.sub);
      if (s == null || !s.address.containsKey(arg.tag)) {
        System.err.println("error: invalid subroutine name at " + arg);
      } else {
        arg.val = s.address.get(arg.tag) + arg.tagoffset;
      }
    }
    if (tagLocs.containsKey(arg.tag)) {
      arg.val = tagLocs.get(arg.tag) + arg.tagoffset;
    }
  }

  /**
   * Optimize individual commands for speed
   */
  static void simplify() {
    for (Command c : mainROM) {
      c.simplify();
    }
    for (int i = 0; i < mainROM.size() - 1; i++) {
      Command c = mainROM.get(i);
      if (c.isEquivalent("MLZ", 0, -1, 0, null, 0, 0)) {
        Command d = mainROM.get(i + 1);
        if (d.isEquivalent("MLZ", 0, 0, 0, 0, 0, 0)) {
          if (c.arg2.val < mainROM.size()) {
            Command replacement = mainROM.get(c.arg2.val).dupWithoutTags();
            replacement.tags.addAll(d.tags);
            mainROM.set(i + 1, replacement);
            c.arg2.val++;
          }
        }
      }
    }
  }

  /**
   * Changes all jump statements (like MLZ _ N 0) to point to the N-1 spot in
   * ROM
   */
  static void adjustJumps() {
    mainROM.add(0, new Command("MLZ", new Arg(0), new Arg(0), new Arg(0)));
  }

  /**
   * Removes everything up to and including the next semicolon Used to generate
   * error messages
   * 
   * @return removed tokens
   */
  public static String rmStatement() {
    String res = "";
    while (tokens.size() > 0 && !tokens.get(0).equals(";")) {
      res += " " + tokens.remove(0);
    }
    if (tokens.size() > 0 && tokens.get(0).equals(";")) {
      res += " " + tokens.remove(0);
    }
    return res;
  }

  /**
   * Removes everything up to and including the next semicolon Used to generate
   * error messages
   * 
   * @return removed tokens
   */
  public static ArrayList<String> rmStatementTokens() {
    ArrayList<String> res = new ArrayList<String>();
    while (tokens.size() > 0 && !tokens.get(0).equals(";")) {
      res.add(tokens.remove(0));
    }
    if (tokens.size() > 0 && tokens.get(0).equals(";")) {
      res.add(tokens.remove(0));
    }
    return res;
  }

  /**
   * Responsible for creating WHILE and IF statements It calculates the commands
   * for the beginning and end of the loop The ending is stored in an OpenLoop
   * to be added when then loop is closed
   * 
   * @param ROM
   *          Command list to modify
   */
  public static void compileLoopStart(ArrayList<Command> ROM) {
    String type = tokens.remove(0);
    OpenLoop loop = new OpenLoop(type);
    loops.add(0, loop);
    tokens.remove(0); // (

    ArrayList<Command> cond = new ArrayList<Command>();

    Arg arg1 = compileRef(cond, false);
    String op = tokens.remove(0);
    Arg arg2 = new Arg(0);
    if (op.equals(")")) {
      op = "!=";
    } else {
      arg2 = compileRef(cond, false);
      tokens.remove(0); // )
    }
    String oBrace = tokens.remove(0);
    while (!oBrace.equals("{")) {
      loop.name += "_" + oBrace;
      oBrace = tokens.remove(0);
    }

    if (type.equals("while")) {
      Arg test = null;
      if (arg2.mode == 0 && op.equals("<=")) {
        arg2.val++;
        op = "<";
      }
      if (arg1.mode == 0 && op.equals("<=")) {
        arg1.val--;
        op = "<";
      }
      if (arg2.mode == 0 && op.equals(">=")) {
        arg2.val--;
        op = ">";
      }
      if (arg1.mode == 0 && op.equals(">=")) {
        arg1.val++;
        op = ">";
      }
      ROM.add(new Command("MLZ", new Arg(-1), new Arg("end" + loop, 0),
          new Arg(address.get(ProgramCounter)), "begin" + loop));
      if (op.equals("<") || op.equals(">") || op.equals("<=")
          || op.equals(">=")) {
        if (op.startsWith(">")) {
          Arg temp = arg1;
          arg1 = arg2;
          arg2 = temp;
        }
        if (arg2.mode == 0 && arg2.val == 0) {
          test = arg1;
        } else {
          if (!op.endsWith("=")) {
            freeS(arg1);
          }
          freeS(arg2);
          Arg testdest = mallocS();
          test = testdest.dup();
          test.mode++;
          if (op.endsWith("=")) {
            cond.add(new Command("ADD", arg2, new Arg(1), testdest));
            cond.add(new Command("SUB", arg1, test, testdest));
          } else {
            cond.add(new Command("SUB", arg1, arg2, testdest));
          }
        }
        cond.add(new Command("MLZ", test, new Arg("begin" + loop, 2),
            new Arg(address.get(ProgramCounter))));
      } else if (op.equals("!=")) {
        if (arg2.mode == 0 && arg2.val == 0) {
          test = arg1;
        } else if (arg1.mode == 0 && arg1.val == 0) {
          test = arg2;
        } else {
          freeS(arg1);
          freeS(arg2);
          Arg testdest = mallocS();
          test = testdest.dup();
          test.mode++;
          cond.add(new Command("SUB", arg1, arg2, testdest));
        }
        cond.add(new Command("MNZ", test, new Arg("begin" + loop, 2),
            new Arg(address.get(ProgramCounter))));
      } else {
        System.err
            .println("error: condition " + type + " " + op + " not supported");
      }
      compileDelaySlot(ROM);
      int endloc = cond.size() - 1;
      if (endloc > 1) {
        endloc = 1;
      }
      cond.get(endloc).tags.add("end" + loop);
      for (int i = 0; i < endloc; i++) {
        ROM.set(ROM.size() - 1 + i, cond.get(i));
      }
      compileDelaySlot(cond);
      loop.commands = cond;

    } else if (type.equals("if")) {
      ROM.addAll(cond);
      Arg test = null;
      if (arg2.mode == 0 && op.equals("<")) {
        arg2.val--;
        op = "<=";
      }
      if (arg1.mode == 0 && op.equals("<")) {
        arg1.val++;
        op = "<=";
      }
      if (arg2.mode == 0 && op.equals(">")) {
        arg2.val++;
        op = ">=";
      }
      if (arg1.mode == 0 && op.equals(">")) {
        arg1.val--;
        op = ">=";
      }

      if (op.equals("==")) {
        if (arg2.mode == 0 && arg2.val == 0) {
          test = arg1;
        } else if (arg1.mode == 0 && arg1.val == 0) {
          test = arg2;
        } else {
          freeS(arg1);
          freeS(arg2);
          Arg testdest = mallocS();
          test = testdest.dup();
          test.mode++;
          ROM.add(new Command("SUB", arg1, arg2, testdest));
        }
        ROM.add(new Command("MNZ", test, new Arg("end" + loop, 1),
            new Arg(address.get(ProgramCounter))));
        compileDelaySlot(ROM);
        ROM.get(ROM.size() - 2).tags.add("begin" + loop);
      } else if (op.equals(">=") || op.equals("<=")) {
        if (op.equals("<=")) {
          Arg temp = arg1;
          arg1 = arg2;
          arg2 = temp;
        }
        if (arg2.mode == 0 && arg2.val == 0) {
          test = arg1;
        } else {
          freeS(arg1);
          freeS(arg2);
          Arg testdest = mallocS();
          test = testdest.dup();
          test.mode++;
          ROM.add(new Command("SUB", arg1, arg2, testdest));
        }
        ROM.add(new Command("MLZ", test, new Arg("end" + loop, 1),
            new Arg(address.get(ProgramCounter))));
        compileDelaySlot(ROM);
        ROM.get(ROM.size() - 2).tags.add("begin" + loop);
      } else if (op.equals(">") || op.equals("<")) {
        if (op.equals("<")) {
          Arg temp = arg1;
          arg1 = arg2;
          arg2 = temp;
        }

        freeS(arg2);
        Arg testdest = mallocS();
        test = testdest.dup();
        test.mode++;

        ROM.add(new Command("ADD", arg1, new Arg(1), testdest));
        ROM.add(new Command("SUB", test, arg2, testdest));
        ROM.add(new Command("MLZ", test, new Arg("end" + loop, 1),
            new Arg(address.get(ProgramCounter))));
        compileDelaySlot(ROM);
        ROM.get(ROM.size() - 2).tags.add("begin" + loop);
      } else {
        System.err
            .println("error: condition " + type + " " + op + " not supported");
      }
    }
  }

  /**
   * Appends the loop ending that was computed during loop creation It also
   * handles ELSE statements by creating another loop
   * 
   * @param ROM
   */
  public static void compileLoopStop(ArrayList<Command> ROM) {
    tokens.remove(0); // }
    OpenLoop loop = loops.remove(0);
    int startloc = ROM.size();
    ROM.addAll(loop.commands);
    if (loop.type.equals("if")) {
      if (tokens.get(0).equals("else")) {
        tokens.remove(0); // else
        OpenLoop loop2 = new OpenLoop("else");
        loops.add(0, loop2);
        String oBrace = tokens.remove(0);
        while (!oBrace.equals("{")) {
          loop2.name += "_" + oBrace;
          oBrace = tokens.remove(0);
        }
        ROM.add(new Command("MLZ", new Arg(-1), new Arg("end" + loop2, 1),
            new Arg(address.get(ProgramCounter)), "begin" + loop2));
        compileDelaySlot(ROM);
      }
    }
    if (loop.type.equals("doWhile")) {
      tokens.remove(0); // while
      tokens.remove(0); // (
      Arg arg1 = compileRef(ROM, false);
      String op = tokens.remove(0);
      Arg arg2 = new Arg(0);
      if (op.equals(")")) {
        op = "!=";
      } else {
        arg2 = compileRef(ROM, false);
        tokens.remove(0); // )
      }
      tokens.remove(0); // ;
      Arg test = null;
      if (arg2.mode == 0 && op.equals("<=")) {
        arg2.val++;
        op = "<";
      }
      if (arg1.mode == 0 && op.equals("<=")) {
        arg1.val--;
        op = "<";
      }
      if (arg2.mode == 0 && op.equals(">=")) {
        arg2.val--;
        op = ">";
      }
      if (arg1.mode == 0 && op.equals(">=")) {
        arg1.val++;
        op = ">";
      }
      if (op.equals("<") || op.equals(">") || op.equals("<=")
          || op.equals(">=")) {
        if (op.startsWith(">")) {
          Arg temp = arg1;
          arg1 = arg2;
          arg2 = temp;
        }
        if (arg2.mode == 0 && arg2.val == 0) {
          test = arg1;
        } else {
          if (!op.endsWith("=")) {
            freeS(arg1);
          }
          freeS(arg2);
          Arg testdest = mallocS();
          test = testdest.dup();
          test.mode++;
          if (op.endsWith("=")) {
            ROM.add(new Command("ADD", arg2, new Arg(1), testdest));
            ROM.add(new Command("SUB", arg1, test, testdest));
          } else {
            ROM.add(new Command("SUB", arg1, arg2, testdest));
          }
        }
        ROM.add(new Command("MLZ", test, new Arg("begin" + loop, 1),
            new Arg(address.get(ProgramCounter))));
      } else if (op.equals("!=")) {
        if (arg2.mode == 0 && arg2.val == 0) {
          test = arg1;
        } else if (arg1.mode == 0 && arg1.val == 0) {
          test = arg2;
        } else {
          freeS(arg1);
          freeS(arg2);
          Arg testdest = mallocS();
          test = testdest.dup();
          test.mode++;
          ROM.add(new Command("SUB", arg1, arg2, testdest));
        }
        ROM.add(new Command("MNZ", test, new Arg("begin" + loop, 1),
            new Arg(address.get(ProgramCounter))));
      } else {
        System.err
            .println("error: condition doWhile " + op + " not supported");
      }
      compileDelaySlot(ROM);
    }
    if (loop.type.equals("if") || loop.type.equals("else")
        || loop.type.equals("sub") || loop.type.equals("doWhile")) {
      if (prevCall == null || ROM.size() > startloc) {
        ROM.get(ROM.size() - 1).tags.add("end" + loop);
      } else {
        prevCall.tags.add("end" + loop);
      }
    }
    if (loop.type.equals("sub")) {
      subs.remove(0);
    }
  }

  /**
   * Parses variable declarations
   * 
   * @param ROM
   *          command list to modify
   */
  public static void compileDef() {
    // used to test for constant initializers
    ArrayList<Command> ROM = new ArrayList<Command>();
    tokens.remove(0); // my
    String name = tokens.remove(0);
    if (address.containsKey(name)) {
      System.err.println("error: my " + name + rmStatement());
    } else if (reserved.contains(name)) {
      System.err.println("error: reserved name at my " + name + rmStatement());
    } else {
      String type = tokens.remove(0);
      if (type.equals("[")) {
        Integer size = Integer.parseInt(tokens.remove(0));
        tokens.remove(0); // ]
        String eq = tokens.remove(0); // ; or =
        if (eq.equals("=")) {
          tokens.remove(0); // {
          ArrayList<Integer> inits = new ArrayList<Integer>();
          Arg init = compileRef(ROM, false);
          String div = tokens.remove(0);
          while (div.equals(",")) {
            if (init.mode == 0) {
              inits.add(init.val);
            } else {
              inits.add(null);
              System.err.println("Error: non-constant initilizer at my " + name
                  + "[" + size + "] = ... " + init);
            }
            init = compileRef(ROM, false);
            div = tokens.remove(0);
          }
          if (div.equals("}")) {
            if (init.mode == 0) {
              inits.add(init.val);
            } else {
              inits.add(null);
              System.err.println("Error: non-constant initilizer at my " + name
                  + "[" + size + "] = ... " + init);
            }
            createArray(ROMpredefs, name, size, inits);
            tokens.remove(0); // ;
          } else {
            System.err.println("Error: invalid initilizer at my " + name + "["
                + size + "] = ... " + init + rmStatement());
            createArray(ROMpredefs, name, size, inits);
          }
        } else {
          createArray(ROMpredefs, name, size);
        }
      } else if (type.equals(";")) {
        createWord(name);
      } else if (type.equals("=")) {
        Arg init = compileRef(ROM, false);
        if (init.mode == 0 && tokens.get(0).equals(";")) {
          createWord(ROMpredefs, name, init.val);
          tokens.remove(0); // ;
        } else {
          createWord(name);
          System.err.println("Error: non-constant initilizer at my " + name
              + " = " + init + rmStatement());
        }
      } else {
        System.err.println("error: my " + name + " " + type + rmStatement());
      }
    }
  }

  /**
   * Parses a reference to a variable or constant
   * 
   * @param ROM
   *          command list to modify
   * @param isDest
   *          whether the reference will be used as an lvalue, to change Arg
   *          mode accordingly
   * @return An argument pointing to
   */
  public static Arg compileRef(ArrayList<Command> ROM, boolean isDest) {
    int slash = 0;
    String name = tokens.remove(0);
    while (name.equals("\\") || name.equals("$")) {
      if (name.equals("\\")) {
        slash--;
      } else {
        slash++;
      }
      name = tokens.remove(0);
    }
    Arg arg1 = null;
    if (reserved.contains(name)) {
      System.err
          .println("error: reserved address at: " + name + rmStatement());
      return null;
    }
    try {
      Integer value = Integer.parseInt(name);
      if (isDest) {
        System.err.println("error: bare number at: " + name + rmStatement());
        return null;
      } else {
        return new Arg(0, value);
      }
    } catch (Exception e) {
    }
    Subroutine isLocal = null;
    for (int i = subs.size() - 1; i >= 0; i--) {
      if (subs.get(i).args.contains(name)) {
        isLocal = subs.get(i);
      }
    }
    if (!address.containsKey(name) && isLocal == null
        && !tokens.get(0).equals(".")) {
      System.err
          .println("error: undeclared variable at: " + name + rmStatement());
      return null;
    }
    String reftype = tokens.get(0);
    if (reftype.equals("[")) {
      tokens.remove(0); // [
      Arg index = compileRef(ROM, false);
      if (isLocal == null) {
        if (arrayType.equals(type.get(name))) {
          if (index.mode == 0) {
            arg1 = new Arg(1, address.get(name) + 1 + index.val);
          } else {
            Arg temp = null;
            if (index.scratches.size() > 0) {
              temp = index.scratches.get(0);
            } else {
              temp = mallocS();
            }
            ROM.add(new Command("ADD", new Arg(address.get(name) + 1), index,
                temp));
            arg1 = new Arg(2, temp.val);
            arg1.scratches.add(temp);
          }
        } else {
          Arg temp = null;
          if (index.scratches.size() > 0) {
            temp = index.scratches.get(0);
          } else {
            temp = mallocS();
          }
          ROM.add(
              new Command("ADD", new Arg(1, address.get(name)), index, temp));
          arg1 = new Arg(2, temp.val);
          arg1.scratches.add(temp);
        }
      } else {
        if (arrayType.equals(isLocal.type.get(name))) {
          if (index.mode == 0) {
            Arg temp = null;
            if (index.scratches.size() > 0) {
              temp = index.scratches.get(0);
            } else {
              temp = mallocS();
            }
            ROM.add(new Command("ADD", new Arg(1, isLocal.name, 0),
                new Arg(isLocal.address.get(name) + 1 + index.val), temp));
            arg1 = new Arg(2, temp.val);
            arg1.scratches.add(temp);
          } else {
            Arg temp = null;
            if (index.scratches.size() > 0) {
              temp = index.scratches.get(0);
            } else {
              temp = mallocS();
            }
            ROM.add(new Command("ADD", new Arg(1, isLocal.name, 0),
                new Arg(isLocal.address.get(name) + 1), temp));
            ROM.add(new Command("ADD", new Arg(1, temp.val), index, temp));
            arg1 = new Arg(2, temp.val);
            arg1.scratches.add(temp);
          }
        } else {
          Arg temp = null;
          if (index.scratches.size() > 0) {
            temp = index.scratches.get(0);
          } else {
            temp = mallocS();
          }
          ROM.add(
              new Command("ADD", new Arg(1, address.get(name)), index, temp));
          arg1 = new Arg(2, temp.val);
          arg1.scratches.add(temp);
        }
      }
      tokens.remove(0); // ]
    } else if (reftype.equals(".")) {
      tokens.remove(0); // .
      if (isLocal == null) {
        Arg temp = mallocS();
        ArrayList<String> line = getRefTokens();
        String varname = "";
        String vartype = type.get(name);
        if (wordType.equals(vartype)) {
          vartype = name;
        }
        for (String s : line) {
          varname += s;
        }
        ROM.add(new Command("ADD", new Arg(1, address.get(name)),
            new Arg(varname, 0, vartype), temp));
        arg1 = new Arg(2, temp.val);
        arg1.scratches.add(temp);
      } else {
        Arg temp = mallocS();
        ArrayList<String> line = getRefTokens();
        String varname = "";
        String vartype = isLocal.type.get(name);
        if (wordType.equals(vartype)) {
          vartype = name;
        }
        for (String s : line) {
          varname += s;
        }
        ROM.add(new Command("ADD", new Arg(1, isLocal.name, 0),
            new Arg(isLocal.address.get(name)), temp));
        ROM.add(new Command("ADD", new Arg(2, temp.val),
            new Arg(varname, 0, vartype), temp));
        arg1 = new Arg(2, temp.val);
        arg1.scratches.add(temp);
      }
    } else {
      if (isLocal == null) {
        if (arrayType.equals(type.get(name))) {
          System.err.println("warning: " + name + " of incorrect type");
        }
        arg1 = new Arg(1, address.get(name));
      } else {
        if (arrayType.equals(isLocal.type.get(name))) {
          System.err.println("warning: " + name + " of incorrect type");
        }
        Arg temp = mallocS();
        ROM.add(new Command("ADD", new Arg(1, isLocal.name, 0),
            new Arg(isLocal.address.get(name)), temp));
        arg1 = new Arg(2, temp.val);
        arg1.scratches.add(temp);
      }
    }
    arg1.mode += slash;
    if (isDest) {
      arg1.mode--;
    }
    return arg1;
  }

  static ArrayList<String> getRefTokens() {
    ArrayList<String> res = new ArrayList<String>();
    res.add(tokens.remove(0));
    if (tokens.get(0).equals(".")) {
      res.add(tokens.remove(0)); // .
      res.addAll(getRefTokens());
    } else if (tokens.get(0).equals("[")) {
      res.add(tokens.remove(0)); // [
      res.addAll(getRefTokens());
      res.add(tokens.remove(0)); // ]
    }
    return res;
  }

  /**
   * Compiles "regular" statements: basic operations and conditional moves
   * 
   * @param ROM
   *          command list to modify
   */
  public static void compileMove(ArrayList<Command> ROM) {
    Arg arg3 = compileRef(ROM, true);
    checkBounds(arg3, false);
    String eq = tokens.remove(0); // =
    Arg arg1 = null;
    String op = "";
    if (eq.equals("=")) {
      if (tokens.get(0).equals("-")) {
        arg1 = new Arg(0);
      } else {
        arg1 = compileRef(ROM, false);
      }
      op = tokens.remove(0);
    } else if (eq.endsWith("=")) {
      arg1 = arg3.dup();
      arg1.mode++;
      op = eq.substring(0, eq.length() - 1);
    } else if (eq.equals("++")) {
      arg1 = arg3.dup();
      arg1.mode++;
      ROM.add(new Command("ADD", arg1, new Arg(1), arg3));
      tokens.remove(0); // ;
      return;
    } else if (eq.equals("--")) {
      arg1 = arg3.dup();
      arg1.mode++;
      ROM.add(new Command("ADD", arg1, new Arg(-1), arg3));
      tokens.remove(0); // ;
      return;
    }
    checkBounds(arg1, false);

    if (op.equals(";")) {
      ROM.add(new Command("MLZ", new Arg(-1), arg1, arg3));
    } else if (op.equals("if")) {
      Cond cond = compileCond(ROM);
      tokens.remove(0); // ;
      if (cond.type == 0) {
        ROM.add(new Command("MNZ", cond.address, arg1, arg3));
      } else if (cond.type == 1) {
        ROM.add(new Command("MLZ", cond.address, arg1, arg3));
      }
    } else {
      Arg arg2 = compileRef(ROM, false);
      tokens.remove(0); // ;
      checkBounds(arg2, false);

      if (op.equals("+") || op.equals("--")) {
        ROM.add(new Command("ADD", arg1, arg2, arg3));
      } else if (op.equals("-") || op.equals("+-")) {
        ROM.add(new Command("SUB", arg1, arg2, arg3));
      } else if (op.equals("&")) {
        ROM.add(new Command("AND", arg1, arg2, arg3));
      } else if (op.equals("|")) {
        ROM.add(new Command("OR", arg1, arg2, arg3));
      } else if (op.equals("^")) {
        ROM.add(new Command("XOR", arg1, arg2, arg3));
      } else if (op.equals("&!")) {
        ROM.add(new Command("ANT", arg1, arg2, arg3));
      } else if (op.equals("<<")) {
        ROM.add(new Command("SL", arg1, arg2, arg3));
      } else if (op.equals(">>>")) {
        ROM.add(new Command("SRL", arg1, arg2, arg3));
      } else if (op.equals(">>")) {
        ROM.add(new Command("SRA", arg1, arg2, arg3));
      } else if (op.equals("*")) {
        int ID = OpenLoop.nextLoopID++;
        Arg tempA = null;
        if (arg1.scratches == null || arg1.scratches.size() == 0) {
          tempA = mallocS();
        } else {
          tempA = arg1.scratches.get(0);
        }
        ROM.add(new Command("SUB", new Arg(0), arg1, tempA, "beginMult" + ID));
        Arg tempB = null;
        if (arg2.scratches == null || arg2.scratches.size() == 0) {
          tempB = mallocS();
        } else {
          tempB = arg2.scratches.get(0);
        }
        ROM.add(new Command("ADD", new Arg(0), arg2, tempB));

        Arg tempAr = tempA.dup();
        tempAr.mode++;
        Arg tempBr = tempB.dup();
        tempBr.mode++;

        ROM.add(new Command("MLZ", tempAr, new Arg("endMult" + ID, -1),
            new Arg(address.get(ProgramCounter))));
        ROM.add(new Command("MLZ", new Arg(-1), new Arg(0), arg3));
        ROM.add(new Command("SUB", new Arg(0), tempAr, tempA));

        ROM.add(new Command("MLZ", new Arg(-1), new Arg("endMult" + ID, -1),
            new Arg(address.get(ProgramCounter))));
        ROM.add(new Command("SUB", new Arg(0), tempBr, tempB));

        Arg arg3r = arg3.dup();
        arg3r.mode++;
        ROM.add(new Command("ADD", arg3r, tempBr, arg3));
        ROM.add(new Command("MLZ", tempAr, new Arg("endMult" + ID, -2),
            new Arg(address.get(ProgramCounter))));
        ROM.add(new Command("ADD", tempAr, new Arg(1), tempA, "endMult" + ID));
      } else {
        System.err.println("error: unrecognized operation " + op);
      }
    }
  }

  /**
   * Generated by compileCond Contains the information necessary to create a
   * conditional move
   */
  static class Cond {
    int type; // 0 = MNZ, 1 = MLZ
    Arg address;
  }

  /**
   * @param ROM
   *          command list to modify
   * @return The information necessary to create a conditional move
   */
  public static Cond compileCond(ArrayList<Command> ROM) {
    Arg arg1 = compileRef(ROM, false);
    String op = tokens.remove(0);
    Arg arg2 = compileRef(ROM, false);

    Cond res = new Cond();
    if (op.equals("!=")) {
      res.type = 0;
    } else if (op.equals("<")) {
      res.type = 1;
    } else if (op.equals(">")) {
      Arg temp = arg1;
      arg1 = arg2;
      arg2 = temp;
      res.type = 1;
    } else {
      System.err.println("error: in-line condition " + op + " not supported");
    }
    if (arg1.mode == 0 && arg2.mode == 0) {
      System.err.println(
          "warning: constant condition at " + arg1 + " " + op + " " + arg2);
      if (res.type == 0) {
        if (arg1.val == arg2.val) {
          res.type = -1;
        } else {
          res.type = 1;
          res.address = new Arg(-1);
        }
      } else if (res.type == 1) {
        if (arg1.val >= arg2.val) {
          res.type = -1;
        } else {
          res.address = new Arg(-1);
        }
      }
    } else if (arg2.mode == 0 && arg2.val == 0) {
      res.address = arg1;
    }

    return res;
  }

  public static void compileDoWhile(ArrayList<Command> ROM) {
    OpenLoop loop = new OpenLoop("doWhile");
    tokens.remove(0); // do
    String oBrace = tokens.remove(0);
    while (!oBrace.equals("{")) {
      loop.name += "_" + oBrace;
      oBrace = tokens.remove(0);
    }
    loops.add(0, loop);
    if (prevCall == null) {
      if (ROM.size() == 0) {
        compileDelaySlot(ROM); // kinda hacky
      }
      ROM.get(ROM.size() - 1).tags.add("begin" + loop);
    } else {
      prevCall.tags.add("begin" + loop);
    }

  }

  /**
   * @param ROM
   *          command sequence to add delay slots to
   */
  public static void compileDelaySlot(ArrayList<Command> ROM) {
    ROM.add(new Command("MLZ", new Arg(0), new Arg(0), new Arg(0)));
  }

  /**
   * Declare a subroutine
   * 
   * @param ROM
   *          command sequence to modify
   */
  public static void compileSub(ArrayList<Command> ROM) {
    tokens.remove(0); // sub
    String name = tokens.remove(0);
    if (subroutine.get(name) != null) {
      System.err.println("error: duplicate subroutine " + name);
    }
    createWord(name);
    OpenLoop loop = new OpenLoop("sub");
    Subroutine sub = new Subroutine(name, loop);
    loop.name = "_" + name;
    loops.add(0, loop);
    subs.add(0, sub);
    subroutine.put(name, sub);
    tokens.remove(0); // (
    if (tokens.get(0).equals(")")) {
      tokens.remove(0); // )
    }
    while (!tokens.get(0).equals("{")) {
      sub.compileDef(tokens);
    }
    ROM.add(new Command("MLZ", new Arg(-1), new Arg("end" + loop, 1),
        new Arg(ProgramCounter, 0)));
    compileDelaySlot(ROM);
    ROM.get(ROM.size() - 1).tags.add("begin" + loop);
    Arg temp = mallocS();
    loop.commands.add(new Command("MLZ", new Arg(-1), new Arg(1, name, 0),
        new Arg(CallStackPointer, 0)));
    loop.commands
        .add(new Command("ADD", new Arg(1), new Arg(1, name, 0), temp));
    loop.commands.add(new Command("MLZ", new Arg(-1), new Arg(2, name, 0),
        new Arg(address.get(ProgramCounter))));
    loop.commands.add(new Command("MLZ", new Arg(-1), new Arg(2, temp.val),
        new Arg(0, name, 0)));
    freeS(temp);
    tokens.remove(0); // {
  }

  /**
   * @param ROM
   * @param call
   */
  public static void compileCall(ArrayList<Command> ROM, CallStatement call) {
    ArrayList<Subroutine> oldsubs = new ArrayList<Subroutine>();
    oldsubs.addAll(subs);
    subs.clear();
    subs.addAll(call.cursubs);
    ArrayList<Command> tempROM = new ArrayList<Command>();
    ArrayList<Command> pointerROM = new ArrayList<Command>();
    tokens.addAll(0, call.statement);
    tokens.remove(0); // call
    Arg pointer = null;
    Arg theOGpointer = null;
    String eq = null;
    if (call.pointerName() == null) {
      pointer = mallocS();
    } else {
      pointer = compileRef(pointerROM, true);
      theOGpointer = pointer;
      eq = tokens.remove(0); // =
      if (call.returnsPointer) {
        tempROM.addAll(pointerROM);
      } else {
        pointer = mallocS();
      }
    }
    String subName = tokens.remove(0);
    Subroutine sub = subroutine.get(subName);
    if (sub == null) {
      System.err.print(
          "error: undeclared subroutine at call " + subName + rmStatement());
      return;
    }
    Arg temp = mallocS();
    int ID = OpenLoop.nextLoopID++;
    tempROM.add(
        new Command("ADD", new Arg(1, CallStackPointer, 0), new Arg(1), temp));
    tempROM.add(new Command("MLZ", new Arg(-1), new Arg(1, subName, 0),
        new Arg(1, temp.val)));
    freeS(temp);
    tempROM.add(new Command("MLZ", new Arg(-1),
        new Arg(1, CallStackPointer, 0), pointer));

    tokens.remove(0); // (
    if (tokens.get(0).equals(")")) {
      tokens.remove(0); // )
    }
    int argnum = 2; // the first two are call return and previous instance
    // holds commands until after change-of-scope
    ArrayList<Command> defArgROM = new ArrayList<Command>();
    for (; !tokens.get(0).equals(";")
        && !tokens.get(0).equals("."); argnum++) {
      if (tokens.get(0).equals(",")) {
        tokens.remove(0);
        defArgROM.addAll(sub.inits.get(argnum));
      } else {
        String argName = sub.args.get(argnum);

        String varName = tokens.get(0);
        if (tokens.get(1).equals("[")) {
          System.err
              .println("error: unsupported argument type at " + rmStatement());
        } else if (arrayType.equals(varName)) {
          System.err
              .println("error: unsupported argument type at " + rmStatement());
        } else {
          Arg source = compileRef(tempROM, false);
          tokens.remove(0); // , or )

          temp = mallocS();

          tempROM
              .add(new Command("ADD", new Arg(pointer.mode + 1, pointer.val),
                  new Arg(argName, 0, subName), temp));
          tempROM.add(
              new Command("MLZ", new Arg(-1), source, new Arg(1, temp.val)));

          freeS(temp);
        }
      }
    }
    // change of scope
    tempROM.add(new Command("MLZ", new Arg(-1),
        new Arg(pointer.mode + 1, pointer.val), new Arg(subName, 0)));
    tempROM.add(new Command("MLZ", new Arg(-1),
        new Arg("call" + ID + "_" + subName, 1), new Arg(1, subName, 0)));
    freeS(pointer);
    tempROM.addAll(defArgROM);
    for (; argnum < sub.args.size(); argnum++) {
      tempROM.addAll(sub.inits.get(argnum));
    }
    tempROM.add(new Command("MLZ", new Arg(-1), new Arg("begin" + sub.loop, 1),
        new Arg(ProgramCounter, 0)));
    tempROM.add(new Command("ADD", new Arg(sub.firstFreeRAM),
        new Arg(1, sub.name, 0), new Arg(CallStackPointer, 0)));
    tempROM.get(tempROM.size() - 1).tags.add("call" + ID + "_" + subName);

    if (tokens.remove(0).equals(".")) {
      String varname = "";
      while (!tokens.get(0).equals(";")) {
        varname += tokens.remove(0);
      }
      tokens.remove(0); // ;
      tempROM.addAll(pointerROM);
      temp = mallocS();
      Arg theOGpointerR = theOGpointer.dup();
      theOGpointerR.mode++;
      tempROM.add(new Command("ADD", new Arg(1, CallStackPointer, 0),
          new Arg(varname, 0, sub.name), temp));
      if (eq.equals("=")) {
        tempROM.add(new Command("MLZ", new Arg(-1), new Arg(2, temp.val),
            theOGpointer));
      } else if (eq.equals("+=")) {
        tempROM.add(new Command("ADD", theOGpointerR, new Arg(2, temp.val),
            theOGpointer));
      } else if (eq.equals("-=")) {
        tempROM.add(new Command("SUB", theOGpointerR, new Arg(2, temp.val),
            theOGpointer));
      } else if (eq.equals("&=")) {
        tempROM.add(new Command("AND", theOGpointerR, new Arg(2, temp.val),
            theOGpointer));
      } else if (eq.equals("|=")) {
        tempROM.add(new Command("OR", theOGpointerR, new Arg(2, temp.val),
            theOGpointer));
      } else if (eq.equals("^=")) {
        tempROM.add(new Command("XOR", theOGpointerR, new Arg(2, temp.val),
            theOGpointer));
      } else if (eq.equals("&!=")) {
        tempROM.add(new Command("ANT", theOGpointerR, new Arg(2, temp.val),
            theOGpointer));
      } else if (eq.equals("<<=")) {
        tempROM.add(new Command("SL", theOGpointerR, new Arg(2, temp.val),
            theOGpointer));
      } else if (eq.equals(">>>=")) {
        tempROM.add(new Command("SRL", theOGpointerR, new Arg(2, temp.val),
            theOGpointer));
      } else if (eq.equals(">>=")) {
        tempROM.add(new Command("SRA", theOGpointerR, new Arg(2, temp.val),
            theOGpointer));
      } else {
        System.err.println("error: call operator not supported: " + eq);
      }
      freeS(temp);

    }

    tempROM.get(tempROM.size() - 1).tags.addAll(call.tags);
    ROM.addAll(call.loc, tempROM);

    subs.clear();
    subs.addAll(oldsubs);
  }

  static void compileReturn(ArrayList<Command> ROM) {
    tokens.remove(0); // return
    if (subs.size() == 0) {
      System.err.println("error: invalid return at " + rmStatement());
    }
    Subroutine sub = subs.get(0);
    if (tokens.get(0).equals(";")) {
      tokens.remove(0);
      for (Command c : sub.loop.commands) {
        ROM.add(c.dupWithoutTags());
      }
    }

  }

  /**
   * @param a
   *          An argument to check for validity
   * @param isDest
   *          whether it is used as a destination (third argument)
   * @return Whether it is a valid address, with constant arguments being signed
   *         and addresses unsigned
   */
  public static boolean checkBounds(Arg a, boolean isDest) {
    if (a == null) {
      System.err.println("error: null argument");
      return false;
    }
    if (a.mode < 0 || a.mode > 3) {
      System.err.println("error: impossible mode of " + a);
      return false;
    }
    if (a.mode == 0 && !isDest) {
      if (a.val > 65535 || a.val < Short.MIN_VALUE) {
        System.err
            .println("warning: potential 2's complement overflow at " + a);
        return false;
      }
    } else if (a.val > 65535 || a.val < 0) {
      System.err.println("warning: potential unsigned overflow at " + a);
      return false;
    }
    return true;
  }

  /**
   * Allocate scratch addresses to hold intermediate values Generates new
   * addresses if all existing addresses are bust
   * 
   * @return An argument pointing to a scratch address that can be used
   */
  public static Arg mallocS() {
    for (int i = 0; i < scratch.length; i++) {
      if (scratch[i].equals("free")) {
        scratch[i] = "busy";
        return new Arg(address.get("scratch" + i));
      }
    }
    String[] grown = new String[scratch.length + 1];
    for (int i = 0; i < scratch.length; i++) {
      grown[i] = scratch[i];
    }
    scratch = grown;
    scratch[scratch.length - 1] = "busy";
    createWord("scratch" + (scratch.length - 1));
    return new Arg(address.get("scratch" + (scratch.length - 1)));
  }

  /**
   * Frees all scratch addresses
   */
  public static void clearS() {
    for (int i = 0; i < scratch.length; i++) {
      scratch[i] = "free";
    }
  }

  /**
   * Frees a single scratch address
   * 
   * @param s
   *          argument pointing to a scratch address number
   */
  public static void freeS(Arg s) {
    for (int i = 0; i < scratch.length; i++) {
      if (address.get("scratch" + i) == s.val) {
        scratch[i] = "free";
      }
    }
  }

}
