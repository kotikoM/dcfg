import codegen.CodeGenerator;
import config.Configuration;
import dk.DK1;
import dk.GenerateAutomaton;
import grammar.Grammar;
import grammar.Symbol.SymbolType;
import model.VarReg;
import grammar.Symbol;
import table.FunctionTable;
import table.MemoryTable;
import table.TypeTable;
import tree.DTE;
import util.Context;
import util.TypeUtils;

import java.io.*;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

import static util.Context.DEBUG;
import static util.Logger.log;

public class Main {

    /*
    Some Test Program Codes:

    bool benjamin; bool c; int main(){benjamin=(bool)c||false;return 1}~
    char c; int main(){c=t; return 1}~
    int x; int main(){x=-14; return 1}~
    int x; int main(){x=2; if true {x=4} else {x=9};return 3}~
    typedef int[6] arr; arr a;int main(){a[0]=5;return 1}~

     */

    public static void fillTables(DTE program) throws Exception {
        TypeUtils.checkTokenType(program, "<prog>");

        DTE current = program.getFirstSon();
        if (current.isType("<TyDS>")) {
            TypeTable.getInstance().fillTable(current);
            current = current.getNthBrother(2);
        }

        if (current.isType("<VaDS>")) {
            MemoryTable.getInstance().fillTable(current);
            current = current.getNthBrother(2);
        }

        if (current.isType("<FuDS>")) {
            FunctionTable.getInstance().fillTable(current);
        }

        if (DEBUG) {
            TypeTable.getInstance().printTable();
            MemoryTable.getInstance().printTable();
            FunctionTable.getInstance().printTable();
        }
    }

    public static void main(String[] args) throws Exception {

        String grammarFilePath = "src/main/java/grammar/Grammar.txt";
        String terminalsFilePath = "src/main/java/grammar/Terminals.txt";

        DEBUG = true;


        Grammar g = new Grammar(grammarFilePath, terminalsFilePath);

        System.out.println(g);

        DK1 dk1 = GenerateAutomaton.generateAutomaton(grammarFilePath, terminalsFilePath, g);

        String code = "";
        code = "void cool(){asm( cool )}; int main(){cool(); return 1}~";
        DTE parsedT = dk1.parseString(code);
        parsedT.printTree();
        fillTables(parsedT);

        CodeGenerator.getInstance().setGrammar(g);
        CodeGenerator.getInstance().generateCode();
        System.out.println("C0: " + code);
        CodeGenerator.getInstance().printInstructions(true);
        

        // TypeTable.getInstance().printTable();
        // MemoryTable.getInstance().printTable();
        // FunctionTable.getInstance().printTable();

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        // while (true) {
        //     System.out.println("code:");
        //     String path = reader.readLine();
        //     File file = new File(path);
        //     Scanner sc = new Scanner(file);
        //     StringBuilder code = new StringBuilder();
        //     String outputFileName = path.replace(".c0", ".asm");


        //     BufferedWriter writer = new BufferedWriter(new FileWriter(outputFileName));

        //     while (sc.hasNext()) {
        //         code.append(sc.nextLine()).append("\n");
        //     }

        //     try {
        //         DTE parsedTree = dk1.parseString(code.toString());

        //         // Print the ParsedTree
        //         log("The Parse Tree: ");
        //         parsedTree.printTree();

        //         fillTables(parsedTree);

        //         CodeGenerator.getInstance().setGrammar(g);
        //         CodeGenerator.getInstance().generateCode();

        //         System.out.println("C0: " + code);
        //         CodeGenerator.getInstance().printInstructions();

        //         writer.write(CodeGenerator.getInstance().getInstructions());
        //         writer.close();

        //     } catch (Exception e) {
        //         e.printStackTrace();
        //     }

        //     TypeTable.reset();
        //     MemoryTable.reset();
        //     FunctionTable.reset();
        //     Configuration.reset();
        //     CodeGenerator.reset();
        // }

    }
}
