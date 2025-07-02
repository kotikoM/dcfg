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
import util.CodeTranslation;

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
        code = "typedef uint' ptrunsigned;typedef uint[32] u;typedef uint[8] v;typedef struct {u GPR; v SPR} pcb;typedef pcb[3] PCBt;typedef uint[4] PTAt;typedef uint[3] PTOIt;typedef struct {uint usr; uint px} auxrec;typedef auxrec[2] IPTt;uint CP;bool ipf;PCBt PCB;PTAt PTA;PTOIt PTOI;uint i;uint IL;uint found;uint EVA;uint EVPX;bool ptle;bool psfull;uint nextup;IPTt ipt;int WOV;int readms(uint a){int tmp; gpr(1) = a; asm( lw 2 1 0 ); asm( sw 2 29 -4 ); return tmp}; void writems(uint x, uint a){gpr(1) = x; gpr(2) = a {1}; asm( sw 1 2 0 )}; void copyms(uint a, uint b, uint L){gpr(1) = a; gpr(2) = b {1}; gpr(3) = L {1, 2}; asm( blez 3 7 );asm( lw 4 1 0 ); asm( sw 4 2 0 );asm( addi 1 1 4 );asm( addi 2 2 4 );asm( addi 3 3 -1 );asm( blez 0 -6 )}; void readdisk(uint ppx, uint spx){int y; writems(spx, 8192u);writems(1u, 8196u);y = 1; while y!=0 {y = readms(8196u)}; copyms(4096u, ppx*4096u, 1024u)}; void writedisk(uint spx, uint ppx){int y; copyms(ppx*4096u, 4096u, 1024u); writems(spx, 8192u);writems(2u, 8196u);y = 2; while y!=0 {y = readms(8196u)}}; void swapIn(){uint PPXIN;uint SPAIN;uint PTEIIN;PPXIN = 16384u / 4096u + nextup;SPAIN = 266338304u + CP*1048576u + EVPX;PTEIIN = PTOI[CP] + EVPX;readdisk(PPXIN, SPAIN);PTA[PTEIIN] = PPXIN*4096u + 2048u;ipt[nextup].usr = CP;ipt[nextup].px = EVPX}; void swapOut(){uint PPXOUT;uint SPAOUT;uint PTEIOUT;PPXOUT = 16384u / 4096u + nextup;SPAOUT = 266338304u + ipt[nextup].usr*1048576u + ipt[nextup].px;PTEIOUT = PTOI[ipt[nextup].usr] + ipt[nextup].px;writedisk(SPAOUT, PPXOUT);PTA[PTEIOUT] = PTA[PTEIOUT]-2048u}; void ipfHandler(){if (!(bool)psfull) {swapIn()} else {swapOut(); swapIn()}; nextup = nextup + 1u; if nextup==2u {nextup = 0u; psfull = true}}; void scheduler(){CP = 1u}; int runvm(){gpr(1) = PCB[CP].GPR[0]&;asm( macro: restore-user );asm( macro: save-user );IL = 0u; i = 1u; found = 0u; while found==0u {IL = IL+1u; i = 2u*i; gpr(1) = i; gpr(2) = PCB[CP].SPR[2] {1}; asm( and 3 1 2 ); found = gpr(3)}; EVA = 0u; if IL==17u {EVA = PCB[CP].SPR[3]}; if IL==20u {EVA = PCB[CP].SPR[4]}; gpr(1) = EVA; asm( srl 1 1 12 ); EVPX = gpr(1); ptle = (EVPX>=PCB[CP].SPR[6]);ipf = (IL==17u||IL==20u)&&!(bool)ptle; if (bool)ipf {ipfHandler(); gpr(1) = PCB[CP].GPR[0]&; asm( macro: restore-user )};return 1}; int main(){uint i;i = 0u;while i<5u {PTA[i] = 0u;i = i + 1u};i = 1u;PTOI[0] = 0u;while i<3u {PTOI[i] = (i-1u)*1u;i = i + 1u};i = 1u;PCB[0].SPR[6] = 1u;while i<3u {PCB[i].SPR[6] = 1u;i = i + 1u};i = 1u;PCB[0].SPR[5] = 0u;while i<3u {gpr(1) = PTA[PTOI[i]]&;PCB[i].SPR[5] = gpr(1);i = i + 1u};psfull = false;nextup = 0u;gpr(1) = PCB[0].GPR[0]&;asm( sw 1 0 4096 );while true {scheduler(); WOV = runvm()}; return 1}~";
        // code = "int main(){int a; while a>0 {asm( macro: zero )}; return 1}~";
        // code = "int main(){int a; if a>0 {asm( macro: zero )}; return 1}~";
        // code = "int main(){int a; if a>0 {a = 0} else {a = 1}; return 1}~";
        // code = "typedef uint' ptrunsigned;typedef uint[32] u;typedef uint[8] v;typedef struct {u GPR; v SPR} pcb;typedef pcb[3] PCBt;typedef uint[4] PTAt;typedef uint[3] PTOIt;typedef struct {uint usr; uint px} auxrec;typedef auxrec[2] IPTt;uint CP;bool ipf;PCBt PCB;PTAt PTA;PTOIt PTOI;uint i;uint IL;uint found;uint EVA;uint EVPX;bool ptle;bool psfull;uint nextup;IPTt ipt;int WOV;int readms(uint a){int tmp;gpr(1) = a;asm( lw 2 1 0 );asm( sw 2 29 -4 );return tmp};void writems(uint x, uint a){gpr(1) = x;gpr(2) = a {1};asm( sw 1 2 0 )};void copyms(uint a, uint b, uint L){gpr(1) = a;gpr(2) = b {1};gpr(3) = L {1, 2};asm( blez 3 7 );asm( lw 4 1 0 );asm( sw 4 2 0 );asm( addi 1 1 4 );asm( addi 2 2 4 );asm( addi 3 3 -1 );asm( blez 0 -6 )};void readdisk(uint ppx, uint spx){int y; writems(spx, 8192u);writems(1u, 8196u);y = 1; while y!=0 {y = readms(8196u)}; copyms(4096u, ppx*4096u, 1024u)};void writedisk(uint spx, uint ppx){int y; copyms(ppx*4096u, 4096u, 1024u); writems(spx, 8192u);writems(2u, 8196u);y = 2; while y!=0 {y = readms(8196u)}};void swapIn(){uint PPXIN;uint SPAIN;uint PTEIIN;PPXIN = 16384u / 4096u + nextup;SPAIN = 266338304u + CP*1048576u + EVPX;PTEIIN = PTOI[CP] + EVPX;readdisk(PPXIN, SPAIN);PTA[PTEIIN] = PPXIN*4096u + 2048u;ipt[nextup].usr = CP;ipt[nextup].px = EVPX}; void swapOut(){uint PPXOUT;uint SPAOUT;uint PTEIOUT;PPXOUT = 16384u / 4096u + nextup;SPAOUT = 266338304u + ipt[nextup].usr*1048576u + ipt[nextup].px;PTEIOUT = PTOI[ipt[nextup].usr] + ipt[nextup].px;writedisk(SPAOUT, PPXOUT);PTA[PTEIOUT] = PTA[PTEIOUT]-2048u}; void ipfHandler(){if (!(bool)psfull) {swapIn()} else {swapOut(); swapIn()}; nextup = nextup + 1u; if nextup==2u {nextup = 0u; psfull = true}}; void scheduler(){CP = 1u}; int runvm(){gpr(1) = PCB[CP].GPR[0]&;asm( macro: restore-user );asm( macro: save-user );IL = 0u;i = 1u; found = 0u; while found==0u {IL = IL+1u; i = 2u*i; gpr(1) = i; gpr(2) = PCB[CP].SPR[2] {1}; asm( and 3 1 2 ); found = gpr(3)}; EVA = 0u; if IL==17u {EVA = PCB[CP].SPR[3]}; if IL==20u {EVA = PCB[CP].SPR[4]}; gpr(1) = EVA; asm( srl 1 1 12 ); EVPX = gpr(1); ptle = (EVPX>=PCB[CP].SPR[6]);ipf = (IL==17u||IL==20u)&&!(bool)ptle; if (bool)ipf {ipfHandler(); gpr(1) = PCB[CP].GPR[0]&; asm( macro: restore-user )};return 1}; int main(){uint i;i = 0u;while i<5u {PTA[i] = 0u;i = i + 1u};i = 1u;PTOI[0] = 0u;while i<3u {PTOI[i] = (i-1u)*2u;i = i + 1u};i = 1u;PCB[0].SPR[6] = 2u;while i<3u {PCB[i].SPR[6] = 2u;i = i + 1u};i = 1u;PCB[0].SPR[5] = 0u;while i<3u {gpr(1) = PTA[PTOI[i]]&;PCB[i].SPR[5] = gpr(1);i = i + 1u};psfull = false;nextup = 0u;gpr(1) = PCB[0].GPR[0]&;asm( sw 1 0 4096 );while true {scheduler(); WOV = runvm()}; return 1}~";
        // code = "typedef uint' ptrunsigned; typedef uint[32] u; typedef struct {u GPR} pcb; typedef pcb[2] PCBt; PCBt PCB; uint CP; bool ipf; void ipfHandler(){asm( macro: save-user )}; int main(){CP=0u; ipfHandler(); return 1}~";
        DTE parsedT = dk1.parseString(code);
        parsedT.printTree();
        fillTables(parsedT);

        CodeGenerator.getInstance().setGrammar(g);
        CodeGenerator.getInstance().generateCode();
        System.out.println("C0: " + code);
        CodeGenerator.getInstance().getInstructions(true);
        CodeGenerator.getInstance().printInstructions(true);
        System.out.println(Context.gammaAddress);
        // System.out.println(CodeGenerator.getInstance().totalProgramRealSize(true));
        

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
