public class Command {
  String opcode;
  Arg arg1;
  Arg arg2;
  Arg arg3;
  String tag;

  public Command(String opcode, Arg arg1, Arg arg2, Arg arg3) {
    super();
    this.opcode = opcode;
    this.arg1 = arg1.dup();
    this.arg2 = arg2.dup();
    this.arg3 = arg3.dup();
  }

  public Command(String opcode, Arg arg1, Arg arg2, Arg arg3, String tag) {
    super();
    this.opcode = opcode;
    this.arg1 = arg1.dup();
    this.arg2 = arg2.dup();
    this.arg3 = arg3.dup();
    this.tag = tag;
  }

  /**
   * Optimize the performance of this command
   */
  void simplify() {
    if (opcode.equals("SUB") && arg2.mode == 0) {
      opcode = "ADD";
      arg2 = arg2.dup();
      arg2.val *= -1;
    }
    if (isSymmetric()) {
      if (arg1.mode < arg2.mode) {
        Arg temp = arg1;
        arg1 = arg2;
        arg2 = temp;
      } else if (arg1.mode == arg2.mode && arg1.val < arg2.val) {
        Arg temp = arg1;
        arg1 = arg2;
        arg2 = temp;
      }
    }
    if (opcode.equals("MLZ") && arg1.mode == 0) {
      if (arg1.val < 0) {
        arg1.val = -1;
      } else {
        arg1.val = 0;
        arg2 = new Arg(0);
        arg3 = new Arg(0);
      }
    }
    if (opcode.equals("MNZ") && arg1.mode == 0) {
      opcode = "MLZ";
      if (arg1.val != 0) {
        arg1.val = -1;
      } else {
        arg2 = new Arg(0);
        arg3 = new Arg(0);
      }
    }
  }

  /**
   * @return Whether the opcode is symmetric
   */
  boolean isSymmetric() {
    return opcode.equals("ADD") || opcode.equals("AND") || opcode.equals("OR")
        || opcode.equals("XOR");
  }

  public String toString() {
    String res = opcode + " " + arg1 + " " + arg2 + " " + arg3 + ";";
    if (tag != null) {
      res += " " + tag;
    }
    return res;
  }
}
