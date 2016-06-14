import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Subroutine {

  String name;
  final Map<String, Integer> address = new HashMap<String, Integer>();
  int firstFreeRAM = 0;
  final Map<String, String> type = new HashMap<String, String>();
  final ArrayList<String> RAMmap = new ArrayList<String>();
  final ArrayList<ArrayList<Command>> inits = new ArrayList<ArrayList<Command>>();
  final ArrayList<String> args = new ArrayList<String>();
  final OpenLoop loop;

  Subroutine(String name, OpenLoop loop) {
    this.name = name;
    this.loop = loop;
    createWord("return");
    createWord("previous_call");
  }

  void print() {
    System.out.println(name);
    for (int j = 0; j < args.size(); j++) {
      System.out.println(args.get(j));
      for (int i = 0; i < inits.get(j).size(); i++) {
        System.out.println(i + ". " + inits.get(j).get(i));
      }
    }
  }

  void setRAM(int marker, String name) {
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

  /**
   * @param varname
   *          Name of word-type variable to create.
   */
  void createWord(String varname) {
    args.add(varname);
    ArrayList<Command> ROMpredefs = new ArrayList<Command>();
    inits.add(ROMpredefs);
    setRAM(firstFreeRAM, varname);
    type.put(varname, Compiler.wordType);
    firstFreeRAM++;
  }

  void createWord(String varname, int data) {
    args.add(varname);
    ArrayList<Command> ROMpredefs = new ArrayList<Command>();
    inits.add(ROMpredefs);
    setRAM(firstFreeRAM, varname);
    type.put(varname, Compiler.wordType);
    ROMpredefs.add(new Command("ADD", new Arg(1, name, 0),
        new Arg(firstFreeRAM), new Arg(Compiler.CallStackPointer, 0)));
    ROMpredefs.add(new Command("MLZ", new Arg(-1), new Arg(data), new Arg(1,
        Compiler.CallStackPointer, 0)));
    firstFreeRAM++;
  }

  /**
   * @param varname
   *          Name of array-type variable to create
   * @param size
   *          Reserved size of array
   */
  void createArray(String varname, int size) {
    args.add(varname);
    ArrayList<Command> ROMpredefs = new ArrayList<Command>();
    inits.add(ROMpredefs);
    setRAM(firstFreeRAM, varname);
    type.put(varname, Compiler.arrayType);
    firstFreeRAM++;
    for (int i = 0; i < size; i++) {
      setRAM(firstFreeRAM, varname + "[" + i + "]");
      firstFreeRAM++;
    }
    ROMpredefs.add(new Command("ADD", new Arg(1, name, 0), new Arg(address
        .get(varname)), new Arg(Compiler.CallStackPointer, 0)));
    ROMpredefs.add(new Command("ADD", new Arg(1, Compiler.CallStackPointer, 0),
        new Arg(1), new Arg(1, Compiler.CallStackPointer, 0)));
  }

  /**
   * @param varname
   *          Name of array-type variable to create
   * @param size
   *          Reserved size of array
   * @param data
   *          Initializer constants
   */
  void createArray(String varname, int size, ArrayList<Integer> data) {
    args.add(varname);
    ArrayList<Command> ROMpredefs = new ArrayList<Command>();
    inits.add(ROMpredefs);
    setRAM(firstFreeRAM, varname);
    type.put(varname, Compiler.arrayType);
    firstFreeRAM++;
    for (int i = 0; i < size; i++) {
      setRAM(firstFreeRAM, varname + "[" + i + "]");
      if (data.size() > 0) {
        int datum = data.remove(0);
        ROMpredefs.add(new Command("ADD", new Arg(1, name, 0), new Arg(
            firstFreeRAM), new Arg(Compiler.CallStackPointer, 0)));
        ROMpredefs.add(new Command("MLZ", new Arg(-1), new Arg(datum), new Arg(
            1, Compiler.CallStackPointer, 0)));
      }
      firstFreeRAM++;
    }
    int marker = firstFreeRAM;
    int index = size;
    while (data.size() > 0) {
      setRAM(marker, varname + "[" + index + "]");
      int datum = data.remove(0);
      if (datum != 0) {
        ROMpredefs.add(new Command("ADD", new Arg(1, name, 0), new Arg(marker),
            new Arg(Compiler.CallStackPointer, 0)));
        ROMpredefs.add(new Command("MLZ", new Arg(-1), new Arg(datum), new Arg(
            1, Compiler.CallStackPointer, 0)));
      }
      marker++;
      index++;
    }
    ROMpredefs.add(new Command("ADD", new Arg(1, name, 0), new Arg(address
        .get(varname)), new Arg(Compiler.CallStackPointer, 0)));
    ROMpredefs.add(new Command("ADD", new Arg(1, Compiler.CallStackPointer, 0),
        new Arg(1), new Arg(1, Compiler.CallStackPointer, 0)));
  }

  public static String rmStatement(ArrayList<String> tokens) {
    String res = "";
    while (tokens.size() > 0 && !tokens.get(0).equals(",")
        && !tokens.get(0).equals(")")) {
      res += " " + tokens.remove(0);
    }
    if (tokens.size() > 0
        && (tokens.get(0).equals(",") || tokens.get(0).equals(")"))) {
      res += " " + tokens.remove(0);
    }
    return res;
  }

  void compileDef(ArrayList<String> tokens) {
    ArrayList<Command> ROM = new ArrayList<Command>();
    String name = tokens.remove(0);
    if (address.containsKey(name)) {
      System.err.println("error: my " + name + rmStatement(tokens));
    } else if (Compiler.reserved.contains(name)) {
      System.err.println("error: reserved name at " + name
          + rmStatement(tokens));
    } else {
      String type = tokens.remove(0);
      if (type.equals("[")) {
        Integer size = Integer.parseInt(tokens.remove(0));
        tokens.remove(0); // ]
        String eq = tokens.remove(0); // ; or =
        if (eq.equals("=")) {
          tokens.remove(0); // {
          ArrayList<Integer> inits = new ArrayList<Integer>();
          Arg init = Compiler.compileRef(ROM, false);
          String div = tokens.remove(0);
          while (div.equals(",")) {
            if (init.mode == 0) {
              inits.add(init.val);
            } else {
              inits.add(null);
              System.err.println("Error: non-constant initilizer at my " + name
                  + "[" + size + "] = ... " + init);
            }
            init = Compiler.compileRef(ROM, false);
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
                + size + "] = ... " + init + rmStatement(tokens));
            createArray(name, size, inits);
          }
        } else {
          createArray(name, size);
        }
      } else if (type.equals(",") || type.equals(")")) {
        createWord(name);
      } else if (type.equals("=")) {
        Arg init = Compiler.compileRef(ROM, false);
        if (init.mode == 0
            && (tokens.get(0).equals(",") || tokens.get(0).equals(")"))) {
          createWord(name, init.val);
          tokens.remove(0); // ;
        } else {
          createWord(name);
          System.err.println("Error: non-constant initilizer at my " + name
              + " = " + init + rmStatement(tokens));
        }
      } else {
        System.err.println("error: my " + name + " " + type
            + rmStatement(tokens));
      }
    }
  }

}
