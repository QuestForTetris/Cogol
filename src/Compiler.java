import java.io.File;
import java.io.FileNotFoundException;
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
    Scanner in = new Scanner(new File("primes.cgl"));
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
    System.out.println("\nCompiled QFTASM:");
    for (int i = 0; i < mainROM.size(); i++) {
      System.out.println(i + ". " + mainROM.get(i));
    }

    System.out.println("\nRAM map:");
    for (int i = 0; i < RAMmap.size(); i++) {
      System.out.println(i + ": " + RAMmap.get(i));
    }
  }

  static ArrayList<String> tokens = new ArrayList<String>();
  // characters that are their own token
  static final String singletons = ",;{}()[]$\\";
  // characters that are their own tokens, but repetitions and combinations are
  // grouped
  static final String reps = "<>&|!=+-^";
  // separators, repetition is ignored
  static final String seps = " :";
  static final String digits = "0123456789";

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
            String ereyest = tokens.get(tokens.size() - 2).substring(
                tokens.get(tokens.size() - 2).length() - 1);
            if (curtoken.equals("") && yest.equals("-")
                && (singletons + reps + seps).contains(ereyest)) {
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
  static final String stdout = "display";
  static final String wordType = "word";
  static final String arrayType = "array";
  static final Set<String> reserved = new HashSet<String>();
  static String[] scratch = new String[1];
  static final ArrayList<OpenLoop> loops = new ArrayList<OpenLoop>();
  static int nextLoopID = 0;
  static final int delaySlots = 1;
  static final boolean optimizeDelay = true;

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
  public static void createWord(String name, int data) {
    setRAM(firstFreeRAM, name);
    type.put(name, wordType);
    if (data != 0) {
      ROMpredefs.add(new Command("MLZ", new Arg(-1), new Arg(data), new Arg(
          firstFreeRAM)));
    }
    firstFreeRAM++;
  }

  /**
   * @param name
   *          Name of array-type variable to create
   * @param size
   *          Reserved size of array
   */
  public static void createArray(String name, int size) {
    setRAM(firstFreeRAM, name);
    type.put(name, arrayType);
    firstFreeRAM++;
    for (int i = 0; i < size; i++) {
      setRAM(firstFreeRAM, name + "[" + i + "]");
      firstFreeRAM++;
    }
    ROMpredefs.add(new Command("MLZ", new Arg(-1), new Arg(
        address.get(name) + 1), new Arg(address.get(name))));
  }

  /**
   * @param name
   *          Name of array-type variable to create
   * @param size
   *          Reserved size of array
   * @param data
   *          Initializer constants
   */
  public static void createArray(String name, int size, ArrayList<Integer> data) {
    setRAM(firstFreeRAM, name);
    type.put(name, arrayType);
    firstFreeRAM++;
    for (int i = 0; i < size; i++) {
      setRAM(firstFreeRAM, name + "[" + i + "]");
      if (data.size() > 0) {
        int datum = data.remove(0);
        if (datum != 0) {
          ROMpredefs.add(new Command("MLZ", new Arg(-1), new Arg(datum),
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
        ROMpredefs.add(new Command("MLZ", new Arg(-1), new Arg(datum), new Arg(
            marker)));
      }
      marker++;
      index++;
    }
    ROMpredefs.add(new Command("MLZ", new Arg(-1), new Arg(
        address.get(name) + 1), new Arg(address.get(name))));
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
  }

  /**
   * Main compiler loop Iterates over each statement, identifies the statement
   * type, and then calls a more specialized compiler method
   */
  public static void compile() {
    while (tokens.size() > 0) {
      clearS();
      if (tokens.get(0).equals("my")) {
        compileDef(mainROM);
      } else if (tokens.get(0).equals("if") || tokens.get(0).equals("while")) {
        compileLoopStart(mainROM);
      } else if (tokens.get(0).equals("}")) {
        compileLoopStop(mainROM);
      } else {
        compileMove(mainROM);
      }
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
      if (c.tag != null) {
        tagLocs.put(c.tag, i);
      }
    }
    for (int i = 0; i < mainROM.size(); i++) {
      Command c = mainROM.get(i);
      if (tagLocs.containsKey(c.arg1.tag)) {
        c.arg1.val = tagLocs.get(c.arg1.tag) + c.arg1.tagoffset;
      }
      if (tagLocs.containsKey(c.arg2.tag)) {
        c.arg2.val = tagLocs.get(c.arg2.tag) + c.arg2.tagoffset;
      }
      if (tagLocs.containsKey(c.arg3.tag)) {
        c.arg3.val = tagLocs.get(c.arg3.tag) + c.arg3.tagoffset;
      }
    }
  }

  /**
   * Optimize individual commands for speed
   */
  static void simplify() {
    for (Command c : mainROM) {
      c.simplify();
    }
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
   * Responsible for creating WHILE and IF statements
   * It calculates the commands for the beginning and end of the loop
   * The ending is stored in an OpenLoop to be added when then loop is closed
   * @param ROM Command list to modify
   */
  public static void compileLoopStart(ArrayList<Command> ROM) {
    String type = tokens.remove(0);
    OpenLoop loop = new OpenLoop(type, nextLoopID);
    nextLoopID++;
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
          }
          cond.add(new Command("SUB", arg1, arg2, testdest));
        }
        cond.add(new Command("MLZ", test, new Arg("begin" + loop,
            delaySlots + 1), new Arg(address.get(ProgramCounter))));
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
        cond.add(new Command("MNZ", test, new Arg("begin" + loop,
            delaySlots + 1), new Arg(address.get(ProgramCounter))));
      }
      if (optimizeDelay) {
        compileDelaySlots(ROM);
        int endloc = cond.size() - 1;
        if (endloc > delaySlots) {
          endloc = delaySlots;
        }
        cond.get(endloc).tag = "end" + loop;
        for (int i = 0; i < endloc; i++) {
          ROM.set(ROM.size() - delaySlots + i, cond.get(i));
        }
        compileDelaySlots(cond);
        loop.commands = cond;
      } else {
        compileDelaySlots(ROM);
        cond.get(0).tag = "end" + loop;
        compileDelaySlots(cond);
        loop.commands = cond;
      }

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
        ROM.add(new Command("MNZ", test, new Arg("end" + loop, 1), new Arg(
            address.get(ProgramCounter))));
        compileDelaySlots(ROM);
        ROM.get(ROM.size() - delaySlots - 1).tag = "begin" + loop;
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
        ROM.add(new Command("MLZ", test, new Arg("end" + loop, 1), new Arg(
            address.get(ProgramCounter))));
        compileDelaySlots(ROM);
        ROM.get(ROM.size() - delaySlots - 1).tag = "begin" + loop;
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
        ROM.add(new Command("MLZ", test, new Arg("end" + loop, 1), new Arg(
            address.get(ProgramCounter))));
        compileDelaySlots(ROM);
        ROM.get(ROM.size() - delaySlots - 1).tag = "begin" + loop;
      }
    }
  }

  /**
   * Appends the loop ending that was computed during loop creation
   * It also handles ELSE statements by creating another loop
   * @param ROM
   */
  public static void compileLoopStop(ArrayList<Command> ROM) {
    tokens.remove(0); // }
    OpenLoop loop = loops.remove(0);
    ROM.addAll(loop.commands);
    if (loop.type.equals("if")) {
      if (tokens.get(0).equals("else")) {
        String type = tokens.remove(0); // else
        OpenLoop loop2 = new OpenLoop(type, nextLoopID);
        nextLoopID++;
        loops.add(0, loop2);
        String oBrace = tokens.remove(0);
        while (!oBrace.equals("{")) {
          loop2.name += "_" + oBrace;
          oBrace = tokens.remove(0);
        }
        ROM.add(new Command("MLZ", new Arg(-1), new Arg("end" + loop2, 1),
            new Arg(address.get(ProgramCounter)), "begin" + loop2));
        compileDelaySlots(ROM);
      }
      ROM.get(ROM.size() - 1).tag = "end" + loop;
    }
    if (loop.type.equals("else")) {
      ROM.get(ROM.size() - 1).tag = "end" + loop;
    }
  }

  /**
   * Parses variable declarations
   * 
   * @param ROM
   *          command list to modify
   */
  public static void compileDef(ArrayList<Command> ROM) {
    tokens.remove(0); // my
    String name = tokens.remove(0);
    if (address.containsKey(name)) {
      System.err.println("error: my " + name + rmStatement());
    } else {
      String type = tokens.remove(0);
      if (type.equals("[")) {
        Integer size = Integer.parseInt(tokens.remove(0));
        tokens.remove(0); // ]
        String eq = tokens.remove(0); // ; or =
        System.out.println("A");
        if (eq.equals("=")) {
          System.out.println("B");
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
            createArray(name, size, inits);
            tokens.remove(0); // ;
          } else {
            System.err.println("Error: invalid initilizer at my " + name + "["
                + size + "] = ... " + init + rmStatement());
            createArray(name, size, inits);
          }
        } else {
          createArray(name, size);
        }
      } else if (type.equals(";")) {
        createWord(name);
      } else if (type.equals("=")) {
        Arg init = compileRef(ROM, false);
        if (init.mode == 0 && tokens.get(0).equals(";")) {
          createWord(name, init.val);
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
    if (!address.containsKey(name)
        && (isDest || getAddress(ROM, name).val == null)) {
      System.err.println("error: undeclared variable at: " + name
          + rmStatement());
    } else if (reserved.contains(name)) {
      System.err.println("error: reserved address at: " + name + rmStatement());
    } else {
      String type = tokens.remove(0);
      if (type.equals("[")) {
        arg1 = getAddress(ROM, name, type, tokens.remove(0), tokens.remove(0));
      } else {
        arg1 = getAddress(ROM, name);
        tokens.add(0, type); // putting terminator back on
      }
      arg1.mode += slash;
      if (isDest) {
        arg1.mode--;
      }
    }
    return arg1;
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
      if(tokens.get(0).equals("-")){
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
      } else if (op.equals("&&")) {
        ROM.add(new Command("AND", arg1, arg2, arg3));
      } else if (op.equals("||")) {
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
    }
    if (arg1.mode == 0 && arg2.mode == 0) {
      System.err.println("warning: constant condition at " + arg1 + " " + op
          + " " + arg2);
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

  /**
   * @param ROM
   *          command sequence to add delay slots to
   */
  public static void compileDelaySlots(ArrayList<Command> ROM) {
    for (int i = 0; i < delaySlots; i++) {
      ROM.add(new Command("MLZ", new Arg(0), new Arg(0), new Arg(0)));
    }
  }

  /**
   * @param ROM
   *          command sequence to modify
   * @param strings
   *          A set of strings corresponding to a constant, a variable, or an
   *          indexed variable
   * @return An argument, either type 0, 1, or 2, that points the desired value
   */
  public static Arg getAddress(ArrayList<Command> ROM, String... strings) {
    String var = "";
    for (String s : strings) {
      var += s;
    }
    if (strings.length != 1 && strings.length != 4) {
      System.err.println("error: bad tokens at " + var);
      return null;
    }
    if (strings.length == 1) {
      try {
        Integer value = Integer.parseInt(var);
        return new Arg(0, value);
      } catch (Exception e) {
      }
    }

    if (address.get(strings[0]) == null) {
      System.err.println("error: undeclared name at " + var);
    } else if (address.get(var) != null) {
      if (strings.length == 1 && !wordType.equals(type.get(strings[0]))) {
        System.err.println("warning: " + strings[0] + "of incorrect type");
      } else if (strings.length == 4 && !arrayType.equals(type.get(strings[0]))) {
        System.err.println("warning: " + strings[0] + "of incorrect type");
      }
      return new Arg(1, address.get(var));
    } else if (strings.length == 4 && strings[1].equals("[")
        && strings[3].equals("]")) {
      Arg index = getAddress(ROM, strings[2]);
      if (index.mode == 0) {
        return new Arg(1, address.get(strings[0] + "[0]") + index.val);
      } else if (index.mode == 1) {
        Arg temp = mallocS();
        ROM.add(new Command("ADD", new Arg(address.get(strings[0]) + 1), index,
            temp));
        return new Arg(2, temp.val);
      }

    }
    return null;
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
    if (a.mode < 0 || a.mode > 3) {
      System.err.println("error: impossible mode of " + a);
      return false;
    }
    if (a.mode == 0 && !isDest) {
      if (a.val > Short.MAX_VALUE || a.val < Short.MIN_VALUE) {
        System.err.println("warning: overflow at " + a);
        return false;
      }
    } else if (a.val > 65535 || a.val < 0) {
      System.err.println("warning: overflow at " + a);
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
