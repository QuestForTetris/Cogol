import java.util.ArrayList;


public class OpenLoop {
  String type;
  int id;
  String name;
  ArrayList<Command> commands;

  public OpenLoop(String type, int id) {
    super();
    this.type = type;
    this.id = id;
    this.name = "";
    this.commands = new ArrayList<Command>();
  }
  
  public String toString(){
    return capitalize(type) + id + name;
  }
  
  private String capitalize(final String line) {
    return Character.toUpperCase(line.charAt(0)) + line.substring(1);
 }
}
