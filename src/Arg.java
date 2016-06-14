import java.util.ArrayList;

public class Arg {

  /**
   * @param mode
   *          Addressing mode
   * @return
   */
  public static String getModePrefix(int mode) {
    switch (mode) {
    case 0: // constant
      return "";
    case 1: // address
      return "A";
    case 2: // dereference
      return "B";
    case 3: // uber-dereference
      return "C";
    default: // invalid mode
      return "(" + mode + ")";
    }
  }

  int mode;
  Integer val;
  String tag;
  int tagoffset;
  String sub;
  ArrayList<Arg> scratches = new ArrayList<Arg>();

  Arg(Integer val) {
    this.val = val;
    this.tag = "";
  }

  Arg(String tag, int tagoffset) {
    this.tag = tag;
    this.tagoffset = tagoffset;
  }

  Arg(String tag, int tagoffset, String sub) {
    this.tag = tag;
    this.tagoffset = tagoffset;
    this.sub = sub;
  }

  Arg(int mode, Integer val) {
    this.mode = mode;
    this.val = val;
    this.tag = "";
  }

  Arg(int mode, String tag, int tagoffset) {
    this.mode = mode;
    this.tag = tag;
    this.tagoffset = tagoffset;
  }

  Arg(int mode, String tag, int tagoffset, String sub) {
    this.mode = mode;
    this.tag = tag;
    this.tagoffset = tagoffset;
    this.sub = sub;
  }

  Arg(int mode, Integer val, String tag, int tagoffset, String sub) {
    this.mode = mode;
    this.val = val;
    this.tag = tag;
    this.tagoffset = tagoffset;
    this.sub = sub;
  }

  public String toString() {
    String res = getModePrefix(mode);
    if (val != null) {
      res += val;
    } else {
      res += "(";
      if (sub != null) {
        res += sub + ".";
      }
      res += tag;
      if (tagoffset < 0) {
        res += tagoffset;
      } else if (tagoffset > 0) {
        res += "+" + tagoffset;
      }
      res += ")";
    }
    return res;
  }

  Arg dup() {
    return new Arg(mode, val, tag, tagoffset, sub);
  }
}
