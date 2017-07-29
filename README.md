# Cogol
"C of Game of Life" is a higher-level language for use with the Game of Life processor.  It compiles to QFTASM, which can be executed here: http://play.starmaninnovations.com/qftasm/.

## Syntax

### General

This language is case-*insensitive*.  Comments are in the form of `#`, which comments out everything until the end of the line.  Other than that, newlines have no effect.  Spaces separate tokens, although in many cases spaces are not needed.

### Declaring Global Variables

Variables can be either global and local.  Global variables are declared with a `my` statement somewhere in the code, and this declaration must precede the use of the variable.  There are two main types of variable, word and array.  Words are declared by name, arrays are declared by name and size.

    my alpha;
    my beta[14];

Variables can optionally contain pre-loaded values, which are designated by constants after an equals sign.  The number of constants provided for an array does not need to match the size of the array.  The "size" of the array tells the size of the *exclusively* reserved memory space, although data can be accessed outside this range.

    my alpha = 123;
    my beta[14] = {3,5,7,8};
As an important note, all pre-loaded values are loaded *once* during the first part of program execution, *before* all other code executes. Even if a variable declaration is placed inside a loop, it will not be re-evaluated. The order of declarations determines the order that the global variables are stored in memory.

When a word is declared, the next available RAM address is assigned to it and is given that name.  When an array is declared, a RAM address is assigned to the array name, and then the next several RAM addresses are assigned to hold the contents of the array.  The first array address (the bare name of the array), is pre-loaded with the address location of the array's first address.  This first address can also serve as a stack pointer.  Here is a typical portion of a RAM map showing a single word and a single array:

    3: sigma
    4: theta     # pre-loaded with the number 5 in this case
    5: theta[0]
    6: theta[1]
    7: theta[2]
    8: theta[3]

Local variables are declared in the "signature" of subroutines, which are explained later.  Subroutines also have their own memory map.

### Referencing Simple Variables

Word variables are referenced by name, while array variables are referenced by name and index.  Depending on whether the variable is used as an argument/operand or as an lvalue (left-hand side of an assignment), the variable refers to either the contents of the variable or to the location of the variable.  Consider this following example:

    beta[7] = alpha;
    alpha = beta[alpha];
    alpha = beta;
The first line of this code stores the contents of `alpha` in the address location of `beta[7]`. The exact location of `beta[7]` is calcuated by taking the RAM location of `beta`, adding 1, and then adding `7`.  In the case of a constant, this is precomputed.  The next line takes a value from the array and stores it in a word.  In the case of `beta[alpha]`, it requires an additional command to perform the pointer addition.

Array references can be nested, like `arr[arr[7]]`.

Words variables can also be used with an index, like `alpha[3]`.  In this case, it uses the contents of `alpha` as a pointer to indicate the location of `alpha[0]`, and then uses that value for pointer addition.

Variables that are of a special type can be accessed with dot notation, like `tree.res`.  More detail will be provided in the subroutines section.

### Simple Statements

Statements can take several forms, listed below.  Any of the destinations or variables cen be replaced by array references instead of word references.  Any of the operands (the `var`s) can be replaced by literal numbers.

    dest = var;
    dest = var if cond; # in-line conditional
    dest = -var;
    dest = var1 + var2;
    dest += var;
    dest++;
    dest = var1 - var2;   
    dest -= var;
    dest--;
    dest = var1 & var2;  # bitwise AND
    dest &= var;
    dest = var1 | var2;  # bitwise OR
    dest |= var2;
    dest = var1 ^ var2;   # bitwise XOR
    dest ^= var;
    dest = var1 &! var2;  # bitwise AND-NOT
    dest &!= var;
    dest = var1 << var2;  # shift left
    dest <<= var;
    dest = var1 >> var2;  # shift right arithmetic
    dest >>= var;
    dest = var1 >>> var2;  # shift right logical
    dest >>>= var;

Conditions are of the form `var3 (op) var4` or simply `var3`, where the operation is any of `< <= == != => >`.  When the operation and second argument are omitted, the comparison is `!= 0`. Support for conditionals is not complete for some of the more complex cases.  Loop conditionals are nearly complete, while in-line conditionals are severely lacking and should be avoided at this time.

### IF statements

`If` statements are of the form

    if (cond) optional label {
      ... body ...
    }
The condition can be (almost) any conditional statement, as describe above.  The optional label has no effect on execution, other than providing a comment in the compiled QFTASM indicated the starting/ending positions of the block.

### WHILE statements

`While` statements are of the form

    while (cond) optional label {
      ... body ...
    }
Similarly to IF statements, the condition can be (almost) any conditional statement.  The label also has no effect.

### DO-WHILE statements

These are similar to a while loop, except the body is always executed at least once.

    do optional label {
      ... body ...
    } while (cond);

They are constructed very similarly to while loops and have similar suppport for conditions.

### Subroutine declarations

Subroutines are declared in the following syntax.

    sub example(lambda = 5, omega, gamma[2] = {3,6}, delta[7]){
      ... body ...
    }
The variables declared in the signature are local variables, and can be accessed inside of the subroutine just like global variables.  Local variables override global variables of the same name.  The initializers are of similar format to the global variable initializers, and serve as the default values of the arguments.  The pre-loading occurs when a new instance is called, but they are only loaded when a corresponding argument is not provided.  Importantly, a local variable that does not have a default value, and which is not provided as an argument, can potentially take on *any* value, determined by the most recently-executed subroutine to use that same address space.

### Subroutine calls

Subroutines are called using the following syntax.

    call myPointer = example(args); # returns a pointer
    call example(args); # returns nothing, useful for changing global variables
    call myResult = example(args).somevariable; # returns the value of the desired local variable
    call mySum += example(args).res; # adds result.  Also supported are -= &= |= ^= &!= <<= >>>= >>=
This runs the subroutine `example` with the specified arguments.  The variable `myPointer` is loaded with a pointer to the results.  The variable `myPointer` must have been previously declared, either as a global or local variable.  Curently, only words can serve as pointers, not array indices.

*At this time, only words as arguments in calls are supported, so things like `example(arg,arg2)` or `example()` work.*

This call also changes `myPointer` to a special type of `example`.  The local variables of sunroutine can then be read using dot notation.  For example, `myPointer.omega` references the `omega` local variable of that subroutine call.

It is important to remember that the results of the subroutine are overwritten by later calls (of any other subroutine), so it is advised to declare enough local variables to hold the desired results, and then use dot notation to access the reults in the process of copying them over to local variables.

I will eventually add support for "objects," which will basically be subroutine calls whose local variables are permanently "appended" to the local variables of the parent call (in cases when subroutines call other subroutines).
