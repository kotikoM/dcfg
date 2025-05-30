import codegen.CodeGenerator;
import config.Configuration;
import dk.DK1;
import grammar.Grammar;
import grammar.Symbol.SymbolType;
import model.VarReg;
import grammar.Symbol;
import table.FunctionTable;
import table.MemoryTable;
import table.TypeTable;
import tree.DTE;
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

        DK1 dk1 = new DK1(g);

        log("number of states: " + dk1.getStates().size());
        log("-----------------------");
        log("DK1 test passed = " + dk1.dk1Test());
        log("-----------------------");
        log("\n");

        String code = "int a; int b; char c; int main(){gpr(1) = b; gpr(2) = a {1}; gpr(3) = c {2, 1}; return 0}~";
        code = "typedef struct {int va1; int va2} arr; typedef arr[7] parr; typedef parr[5] pparr; pparr a; int b; int cool(int c, int d){int a; return 1}; int main(){b = a[4][2 - 1].va1; b = cool(2, 3); a[1][2].va2 = 2; b = 2 / 0; return 1}~"; 
        // code = "int i; bool b; uint c; char d; int main(){b = false; i = 2147483647; c = 4294967295u; d = !; return 1}~";
        code = "int i; int a; int main(){a = 1; i = 2; while a<2 {i = a / 2; a = a + 1}; asm( sw 1 2 3 ); asm( sw 1 1 1 ); return 1}~";
        code = "int a; int b; void cool(int b){b = 1}; int main(){a = 1; cool(a); gpr(1) = b {2}; asm( macro: save-user ); return 1}~";
        code = "int a; int b; int cool(int b){b = 1; return 1}; int gg(int c){return 2}; int main(){a = 1; b = cool(a); b = gg(1); return 1}~";
        // code = "typedef uint[32] u; typedef uint[8] v; typedef struct{u gpr; v spr} pcb; typedef pcb[2] PCBt; typedef uint[1] PTAt; typedef uint[2] PTOIt; struct {uint usr, uint px} auxrec; auxrec[1] iptt; uint CP; bool ipf; PCBt PCB; PTAt PTA; PTOIt PTOI; uint i; uint IL; uint found; uint EVA; uint EVPX; bool ptle; int WOV; void ipfHandler(){asm( syscall )}; int runvm(){gpr(1) = PCB[CP]&; asm( macro: restore-user ); asm( macro: save-user ); IL = 0; i = 1; found = 0; while found==0 {IL = IL + 1; i = 2 * i; gpr(1) = i; gpr(2) = PCB[CP].spr[2] {1}; asm( and 3 1 2 ); found = gpr(3)};EVA = 0; if il==17 {EVA = PCB[CP].spr[3]}; if il==29 {EVA = PCB[CP].spr[4]}; gpr(1) = EVA; asm( srl 1 1 12 ); EVPX = gpr(1);ipf = ( il==17 || il==20 ) && !ptle; if ipf then {ipfHandler(); gpr(1) = PCB[CP]&; asm( macro: restore-user )}; return 1};int main(){gpr(1) = PCB[0]&; asm( sw 1 0 4096 );while true {WOV = runvm()};return 1}~";
        // code = "typedef uint[32] u; typedef uint[8] v; typedef struct {u GPR; v SPR} pcb; typedef pcb[2] PCBt; typedef uint[1] PTAt; typedef uint[2] PTOIt; typedef struct {uint usr; uint px} auxrec; typedef auxrec[1] iptt; uint CP; bool ipf; PCBt PCB; PTAt PTA; PTOIt PTOI; uint i; uint IL; uint found; uint EVA; uint EVPX; bool ptle; int WOV; void ipfHandler(){asm( syscall )}; int runvm(){gpr(1) = PCB[CP]&; asm( macro: restore-user ); asm( macro: save-user ); IL = 0; i = 1; found = 0; while found==0 {IL = IL + 1; i = 2 * i; gpr(1) = i; gpr(2) = PCB[CP].SPR[2] {1}; asm( and 3 1 2 ); found = gpr(3)}; EVA = 0; if il==17 {EVA = PCB[CP].SPR[3]}; if il==29 {EVA = PCB[CP].SPR[4]}; gpr(1) = EVA; asm( srl 1 1 12 ); EVPX = gpr(1); ipf = ( il==17 || il==20 ) && !ptle; if ipf then {ipfHandler(); gpr(1) = PCB[CP]&; asm( macro: restore-user )}; return 1};int main(){gpr(1) = PCB[0]&; asm( sw 1 0 4096 );while true {WOV = runvm()};return 1}~";
        // code = "typedef uint' ptruns; typedef uint[32] u; typedef uint[8] v; typedef struct {u GPR; v SPR} pcb; typedef pcb[2] PCBt; typedef uint[1] PTAt; typedef uint[2] PTOIt; typedef struct {uint usr; uint px} auxrec; typedef auxrec[1] iptt; uint CP; bool ipf; PCBt PCB; PTAt PTA; PTOIt PTOI; uint i; uint IL; uint found; uint EVA; uint EVPX; bool ptle; int WOV; uint cool; int suspect(){gpr(1) = PCB[CP]&; asm( macro: restore-user ); asm( macro: save-user ); IL = 0; i = 1; found = 0; while found==0 {IL = IL+1; i = 2*i; gpr(1) = i; gpr(2) = PCB[CP].SPR[2] {1}; asm( and 3 1 2 ); found = gpr(3)}; EVA = 0; if il==17 {EVA = PCB[CP].SPR[3]}; if il==29 {EVA = PCB[CP].SPR[4]}; gpr(1) = EVA; asm( srl 1 1 12 ); EVPX = gpr(1); ipf = (il==17||il==20)&&!(bool)ptle; if (bool)ipf {ipfHandler(); gpr(1) = PCB[CP]&; asm( macro: restore-user )}; return 1}; int main(){cool = PCB[0].GPR[0]; gpr(1) = cool&; asm( sw 1 0 4096 ); WOV = suspect(); return 1}~";
        // code = "bool a; bool b; bool c; int main(){a = (bool)b||(bool)c&&!(bool)b; if (bool)ipf {asm( macro: restore-user )}; return 1}~";
        // code = "typedef uint' ptr; typedef uint[2] arr; arr a; ptr b; uint c; int main(){c = a[0]; b = c&; return 1}~";
        // code = "int a; int main(){a = runvm(); return 1}; int runvm(){asm( macro: restore-user ); return 1}~";
        code = "int a; int b; int cool(int b){b = 1; return 1}; int gg(){return 2}; int main(){a = 1; b = cool(a); b = gg(); return 1}~";
        DTE parsedT = dk1.parseString(code);
        parsedT.printTree();
        fillTables(parsedT);

        CodeGenerator.getInstance().setGrammar(g);
        CodeGenerator.getInstance().generateCode();

        System.out.println("C0: " + code);
        CodeGenerator.getInstance().printInstructions();

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
