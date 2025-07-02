package codegen;

import config.Configuration;
import config.FunctionCall;
import exceptions.function.FunctionException;
import exceptions.memory.MemoryStructException;
import grammar.Grammar;
import model.Fun;
import model.VarReg;
import model.Variable;
import table.FunctionTable;
import table.MemoryTable;
import tree.DTE;
import util.Context;

import java.util.*;
import java.util.stream.Collectors;

import static codegen.ConstantEvaluator.evaluateCharacterConstant;
import static codegen.ExpressionEvaluator.evaluateBooleanExpression;
import static codegen.ExpressionEvaluator.evaluateExpression;
import static codegen.IdEvaluator.evaluateId;
import static codegen.MemoryHelper.increaseHeapPointer;
import static codegen.MemoryHelper.increaseStackPointer;
import static codegen.MemoryHelper.increaseVoidStackPointer;
import static util.Context.*;
import static util.Logger.log;
import static util.TypeUtils.checkSameTypes;
import static util.TypeUtils.checkTokenType;

public class CodeGenerator {
    private static CodeGenerator INSTANCE = null;
    private int retainedRegister = -1;

    private CodeGenerator() {
    }

    private final Map<String, List<String>> functionInstructions = new HashMap<>();
    private final Set<String> generatedFunctions = new HashSet<>();
    private static final Map<String, Integer> instructionRealSize = Map.of(
        "restore-user", 45,
        "save-user", 46,
        "gpr", 2,
        "ssave", 1,
        "srestore", 1,
        "divt", 58,
        "divu", 32,
        "mul", 10,
        "zero", 4
    );
    private final Map<String, Integer> instructionsRealSize = new HashMap();

    public void addInstruction(String instr) {
        String currFun = Configuration.getInstance().currentFunction().getName();
        functionInstructions.get(currFun).add(instr);

        int currSize = instructionsRealSize.getOrDefault(currFun, 0);
        int newSize = currSize + instructionRealSize(instr);

        instructionsRealSize.put(currFun, newSize);

    }

    public void addInstruction(int index, String instr) {
        String currFun = Configuration.getInstance().currentFunction().getName();
        functionInstructions.get(currFun).add(index, instr);

        int currSize = instructionsRealSize.getOrDefault(currFun, 0);
        int newSize = currSize + instructionRealSize(instr);

        instructionsRealSize.put(currFun, newSize);
        
    }

    public int instructionsSize() {
        return functionInstructions
                .get(Configuration.getInstance().currentFunction().getName())
                .size();
    }

    public int instructionsRealSize(){
        return instructionsRealSize.getOrDefault(Configuration.getInstance().currentFunction().getName(), 0);
    }

    public static int instructionRealSize(String instr){
        String[] split = instr.split("[\\s(),]+");
        if (split[0].equals("macro:")){
            return instructionRealSize.get(split[1]);
        }
        return 1;
    }

    public int totalProgramRealSize(boolean program){
        int size = 0;

        if (program){
            size += Context.programInit;
        }

        for (int v : instructionsRealSize.values()) {
            size += v;  
        }
        return size; 
    }

    public Grammar g;

    public void setGrammar(Grammar g) {
        this.g = g;
    }

    public static CodeGenerator getInstance() {
        if (INSTANCE == null) INSTANCE = new CodeGenerator();
        return INSTANCE;
    }

    public void generateCode() {

        log("initialized instructions list");
        log("starting generation for `main`");

        try {
            FunctionCall mainCall = Configuration.getInstance().callFunction("main");
            functionInstructions.put("main", new LinkedList<>());
            generateCodeForFunctionCall(mainCall);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void generateCodeForFunctionCall(FunctionCall call) throws Exception {
        DTE body = call.getFunction().getBody();
        checkTokenType(body, "<body>");

        // all calls, except `main`, will store return address on address SPT - (size($f)+4), from register 31
        // function is not main, if result destination is defined
        if (!call.getFunction().getName().equals("main")) {
            addInstruction(Instruction.sw(RA, SPT, -(call.getFunction().getSize() + 8)));
        }

        if (body.getFirstSon().isType("<rSt>")) {
            generateRSt(body.getFirstSon(), call);
        } else {
            generateStS(body.getFirstSon());
            generateRSt(body.getNthSon(3), call);
        }
    }

    private void generateCodeVoidFunction(FunctionCall call) throws Exception {
        DTE StS = call.getFunction().getBody();
        checkTokenType(StS, "<StS>");

        addInstruction(Instruction.sw(RA, SPT, -(call.getFunction().getSize() + 4)));

        generateStS(StS);
        int frameSize = call.getFunction().getSize();

        addInstruction(Instruction.lw(1, SPT, -(frameSize + 4)));
        addInstruction(Instruction.addi(SPT, SPT, -(frameSize + 4)));
        addInstruction(Instruction.jr(1));

        Configuration.getInstance().popStack();

    }

    private void generateStS(DTE sts) throws Exception {
        checkTokenType(sts, "<StS>");

        List<DTE> statements = sts.getFlattenedSequence();
        int counter = 0;
        for (DTE statement : statements) {
            log("Statement #" + counter++ + ": " + statement.getBorderWord());
            generateSt(statement);

            Configuration.getInstance().freeAllRegisters(retainedRegister);
        }
    }

    private void generateSt(DTE st) throws Exception {
        checkTokenType(st, "<St>");

        // gpr(<DiS>) = <id> | gpr(<DiS>) = <id> {<NuS>} 
        if (st.getFirstSon().isType("gpr")) {
            
            DTE registerIdx = st.getNthSon(3); 
            DTE id = st.getNthSon(6);

            log("Write gpr: " + registerIdx.getBorderWord() + " from id: " + id.getBorderWord());
            generateWriteGPR(registerIdx, id);
        }
        // <id> = gpr(<DiS>) | <id> = gpr(<DiS>) {<NuS>}
        else if (st.getNthSon(3).isType("gpr")){
            DTE id = st.getFirstSon();
            DTE registerIdx = st.getNthSon(5);

            log("Read gpr: " + registerIdx.getBorderWord() + " into id: " + id.getBorderWord());
            generateReadGPR(id, registerIdx); 
        }

        // <id> = <E> | <id> = <BE> | <id> = <CC> | <id> = <Na>(<PaS>?) | <id> = new <Na>* 
        else if (st.getNthSon(2).isType("=")) {
            DTE id = st.getFirstSon();
            DTE exp = st.getNthSon(3);

            log("Assignment of form <id> = <E> -> \n id: " + id.getBorderWord() + "\n E: " + exp.getBorderWord());
            generateAssignment(id, exp);
        }

        // while <BE> { <StS> }
        else if (st.getFirstSon().isType("while")) {
            generateLoop(st.getFirstSon());
        }

        // if <BE> { <StS> } | if <BE> { <StS> } else { <StS> }
        else if (st.getFirstSon().isType("if")) {
            generateIfStatement(st.getFirstSon());
        }

        // asm( <ASM> ) 
        else if (st.getFirstSon().isType("asm")){

            DTE asmContent = st.getNthSon(3);
            generateInlineASM(asmContent);
        }

        // <Na>(<PaS>?)
        else if (st.getNthSon(2).isType("(")) {

            DTE fun = st.getFirstSon();

            log("void function call: " + fun.getBorderWord());
            checkTokenType(fun, "<Na>");

            String functionName = fun.getBorderWord();

            Fun function = FunctionTable.getInstance().getFunction(functionName);
            List<Integer> registers = new LinkedList<>();

            if (fun.getNthBrother(2).isType("<PaS>")) {
                log("found parameters: " + fun.getNthBrother(2).getBorderWord());
                registers = getParametersRegisterList(function, fun.getNthBrother(2));
            }

            increaseVoidStackPointer(function.getSize());


            if (fun.getNthBrother(2).isType("<PaS>")) {
                log("found parameters: " + fun.getNthBrother(2).getBorderWord());
                setParametersRegisterList(registers, function, fun.getNthBrother(2));
            }

            if (function.getNumLocalVariables() > 0){
                initializeLocalVariables(function);
            }
            
            addInstruction(Instruction.jal("_" + functionName));            

            if (!functionInstructions.containsKey(functionName)) {
                functionInstructions.put(functionName, new LinkedList<>());
                FunctionCall call = Configuration.getInstance().callFunction(functionName);
                generateCodeVoidFunction(call);
            }

        }
        

        // Invalid statement | Unhandled case
        else {
            throw new IllegalArgumentException("Grammar error on \"" + st.getBorderWord() + "\"");
        }
    }

    public void generateInlineASM(DTE instrNode){

        // asm( <ASM> )
        checkTokenType(instrNode, "<ASM>");
        String instruction = instrNode.getBorderWord();
        log("Inline ASM: " + instruction);
        addInstruction(instruction);

        return; 

    }

    public void generateWriteGPR(DTE registerIdx, DTE id) throws Exception {
    
        // gpr(<DiS>) = <id> | gpr(<DiS>) = <id> {<NuS>} 
        String content = registerIdx.getBorderWord();
        String restrictedRegister = "0";
        int idx = Integer.parseInt(content);

        if (idx > 31){
            throw new IllegalArgumentException("Register index - " + idx + " is out of bound");
        }
        if (idx == 0){
            throw new IllegalArgumentException("Register index - " + idx + " is reserved");
        }

        if (id.getSiblingCount() > 1){
            restrictedRegister = id.getNthBrother(2).getBorderWord();
        }
        
        List<Integer> restrictedRegisterList = Arrays.stream(restrictedRegister.split(",")).map(Integer::parseInt).collect(Collectors.toList());
        for (int reg : restrictedRegisterList) {
            Configuration.getInstance().occupyRegister(reg);
        }    
        VarReg varRegId = evaluateId(id, false);
        addInstruction(Instruction.addi(idx, varRegId.register, 0));
        for (int reg : restrictedRegisterList) {
            Configuration.getInstance().freeRegister(reg);
        }

        Configuration.getInstance().freeRegister(varRegId.register);
        
        return ;

    }

    public void generateReadGPR(DTE id, DTE registerIdx) throws Exception {
        
        // <id> = gpr(<DiS>) | <id> = gpr(<DiS>) {<NuS>}
        String content = registerIdx.getBorderWord();
        String restrictedRegister = content;
        int idx = Integer.parseInt(content);
        if (idx > 31){
            throw new IllegalArgumentException("Register Index - " + idx + " is out of bound");
        }
        if (id.getSiblingCount() > 6){
            restrictedRegister = content + "," + id.getNthBrother(7).getBorderWord();
        }
    
        List<Integer> restrictedRegisterList = Arrays.stream(restrictedRegister.split(",")).map(Integer::parseInt).collect(Collectors.toList());
        for (int reg : restrictedRegisterList) {
            Configuration.getInstance().occupyRegister(reg);
        }   
        VarReg varRegId = evaluateId(id, true);
        addInstruction(Instruction.sw(idx, varRegId.register, 0));
        for (int reg : restrictedRegisterList) {
            Configuration.getInstance().freeRegister(reg);
        }
        
        Configuration.getInstance().freeRegister(varRegId.register);
        return; 

    }

    public void generateAssignment(DTE id, DTE value) throws Exception {
        // <id> = value
        // E -> T -> F -> C -> DiS -> Di -> 1
        VarReg varRegId;
        VarReg varRegValue;
        varRegId = evaluateId(id, true);
        
        // case split for type of value
        if (value.isType("<Na>")){
            // id = Na() | id = Na(PaS) left
            // checkTokenType(value, "<Na>");

            String functionName = value.getBorderWord();
            log("function call: " + functionName);

            Fun function = FunctionTable.getInstance().getFunction(functionName);
            List<Integer> registers = new LinkedList<>();

            if (value.getNthBrother(2).isType("<PaS>")) {
                log("found parameters: " + value.getNthBrother(2).getBorderWord());
                registers = getParametersRegisterList(function, value.getNthBrother(2));
            }

            increaseStackPointer(function.getSize());

            addInstruction(Instruction.sw(varRegId.register, SPT, -(function.getSize() + 4)));
            Configuration.getInstance().freeRegister(varRegId.register);

            if (value.getNthBrother(2).isType("<PaS>")) {
                log("found parameters: " + value.getNthBrother(2).getBorderWord());
                setParametersRegisterList(registers, function, value.getNthBrother(2));
            }

            if (function.getNumLocalVariables() > 0){
                initializeLocalVariables(function);
            }
            
            
            addInstruction(Instruction.jal("_" + functionName));

            if (!functionInstructions.containsKey(functionName)) {
                functionInstructions.put(functionName, new LinkedList<>());
                FunctionCall call = Configuration.getInstance().callFunction(functionName);
                generateCodeForFunctionCall(call);
            }

            return;
        }

        if (value.isType("<E>")) {
            varRegValue = evaluateExpression(value);
        } else if (value.isType("<BE>")) {
            varRegValue = evaluateBooleanExpression(value);
        } else if (value.isType("<CC>")) {
            varRegValue = evaluateCharacterConstant(value);
        } else  {
            checkTokenType(value, "new");
            addInstruction(Instruction.sw(HPT, varRegId.register, 0));

            int varSize = varRegId.type.size;
            increaseHeapPointer(varSize);

            return;
        }

        checkSameTypes(varRegId.type, varRegValue.type);

        String instr = Instruction.sw(varRegValue.register, varRegId.register, 0);
        addInstruction(instr);
        Configuration.getInstance().freeRegister(varRegValue.register);

        Configuration.getInstance().freeRegister(varRegId.register);
    }

    public void printInstructions(boolean program) throws FunctionException {
        System.out.println("\n\n------ GENERATED INSTRUCTIONS ------");
        Variable gm = null;
        int gmSize = 0; 
        String headFunction = "main";
        Fun function =  FunctionTable.getInstance().getFunction(headFunction);

        try {
            gm = MemoryTable.getInstance().gm();
            gmSize = gm.getType().size;
        }
        catch(MemoryStructException e){
            gmSize = 0;
        }
        
        if (program){
            System.out.println("macro: gpr(" + HPT + ") = enc(" + HBASE + ", uint)");
            System.out.println("macro: gpr(" + BPT + ") = enc(" + SBASE + ", uint)");
            System.out.println("addiu " + SPT + " " + BPT + " " + (function.getSize() + 8 + gmSize));
            System.out.println("subu 1 " + SPT + " " + BPT);
            System.out.println("srl 1 1 2");
            System.out.println("macro: zero(" + BPT + ", 1)");  
            System.out.println("macro: gpr(" + BPT + ") = enc(" + SBASE + ", uint)");
            System.out.println("j _" + headFunction);
            System.out.println();
        }

        List<String> mainInstructions = functionInstructions.get(headFunction);
        System.out.println("_" + headFunction + ":");
        mainInstructions.forEach(System.out::println);
        System.out.println();

        functionInstructions.forEach((key, value) -> {
            if (!key.equals(headFunction)) {
                System.out.println("_" + key + ":");
                value.forEach(System.out::println);
                System.out.println();
            }
        });
        System.out.println("--------------------------");
    }

    public String getInstructions(boolean program) throws FunctionException {
        Variable gm = null;
        int gmSize = 0; 
        String headFunction = "main";
        Fun function = FunctionTable.getInstance().getFunction(headFunction);

        try {
            gm = MemoryTable.getInstance().gm();
            gmSize = gm.getType().size;
        }
        catch(MemoryStructException e){
            gmSize = 0;
        }

        int gammaAddress = 0; 
        StringBuilder res = new StringBuilder();
        if (program){
            res.append("macro: gpr(" + HPT + ") = enc(" + HBASE + ", uint)\n");
            res.append("macro: gpr(" + BPT + ") = enc(" + SBASE + ", uint)\n");
            res.append("addiu " + SPT + " " + BPT + " " + (function.getSize() + 8 + gmSize) + "\n");
            res.append("subu 1 " + SPT + " " + BPT + "\n");
            res.append("srl 1 1 2\n");
            res.append("macro: zero(" + BPT + ", 1)\n");
            res.append("macro: gpr(" + BPT + ") = enc(" + SBASE + ", uint)\n");
            res.append("j _" + headFunction + "\n\n");
            gammaAddress = Context.programInit;
        }

        res.append(headFunction + "\n");
        List<String> mainInstructions = functionInstructions.get(headFunction);
        res.append(String.join("\n", mainInstructions))
                .append("\n");
        
        for(String instr: mainInstructions){
            if (instr.equals("macro: save-user")) {
                Context.gammaAddress = gammaAddress;
            }
            gammaAddress += instructionRealSize(instr);
        } 
        System.out.println(gammaAddress);

        functionInstructions.forEach((key, value) -> {
            if (!key.equals(headFunction)) {
                res.append("\n_").append(key).append(":\n")
                        .append(String.join("\n", value))
                        .append("\n");
            }
        });

        // gamma address bootloader jumps in case no reset. 
        for (Map.Entry<String, List<String>> entry : functionInstructions.entrySet()) {
            for (String instr : entry.getValue()) {
                if (instr.equals("macro: save-user")) {
                    Context.gammaAddress = gammaAddress;
                }
                gammaAddress += instructionRealSize(instr);
            }
        }
        return res.toString();
    }

    public List<String> getInstructionsList(boolean program) throws FunctionException {
        List<String> res = new LinkedList<>();
        Variable gm = null;
        int gmSize = 0; 
        String headFunction = "main";
        Fun function = FunctionTable.getInstance().getFunction(headFunction);

        try {
            gm = MemoryTable.getInstance().gm();
            gmSize = gm.getType().size;
        }
        catch(MemoryStructException e){
            gmSize = 0;
        }

        if (program){
            res.add("macro: gpr(" + HPT + ") = enc(" + HBASE + ", uint)\n");
            res.add("macro: gpr(" + BPT + ") = enc(" + SBASE + ", uint)\n");
            res.add("addiu " + SPT + " " + BPT + " " + (function.getSize() + 8 + gmSize) + "\n");
            res.add("subu 1 " + SPT + " " + BPT + "\n");
            res.add("srl 1 1 2\n");
            res.add("macro: zero(" + BPT + ", 1)\n");
            res.add("macro: gpr(" + BPT + ") = enc(" + SBASE + ", uint)\n");
            res.add("j _" + headFunction + "\n");
        }

        

        functionInstructions.forEach((key, value) -> {
            if (!key.equals(headFunction)) {
                res.add("\n_" + key + ":\n");
                res.add(String.join("\n", value));
                res.add("\n");
            }
        });

        res.add("\n");
        res.add("_" + headFunction + ":\n");
        List<String> mainInstructions = functionInstructions.get(headFunction);
        for(String inst : mainInstructions){
            res.add(inst + "\n");
        }

        return res; 
    }


    public void generateLoop(DTE dte) throws Exception {
        checkTokenType(dte, "while");

        // while <E> { <StS> }

        DTE expressionNode = dte.getBrother();
        assert expressionNode != null && expressionNode.getBrother() != null;

        DTE bodyNode = dte.getNthBrother(3);
        assert bodyNode != null;


        int before = instructionsRealSize();
        VarReg expression = evaluateBooleanExpression(expressionNode);
        int expressionCodeSize = instructionsRealSize() - before;

        Configuration.getInstance().freeRegister(expression.register);
        // need to add branch jump of size |code(whileBody)| + 2
        before = instructionsRealSize();
        int index = instructionsSize();
        generateStS(bodyNode);
        int bodySize = instructionsRealSize() - before;

        String instr = Instruction.beqz(expression.register, bodySize + 2);
        addInstruction(index, instr);

        // jump back to start of loop
        int jumpBackSize = -(expressionCodeSize + bodySize + 1);
        addInstruction(Instruction.blez(0, jumpBackSize));
    }

    public void generateIfStatement(DTE dte) throws Exception {
        checkTokenType(dte, "if");

        if (dte.getSiblingCount() >= 9) {
            generateIfElseStatement(dte);
            return;
        }

        DTE ifConditionNode = dte.getBrother();
        DTE ifPart = dte.getNthBrother(3);


        VarReg ifCondition = evaluateBooleanExpression(ifConditionNode);

        int before = instructionsRealSize();
        int index = instructionsSize();
        Configuration.getInstance().freeRegister(ifCondition.register);
        generateStS(ifPart);
        int ifPartSize = instructionsRealSize() - before;

        addInstruction(index, Instruction.beqz(ifCondition.register, ifPartSize + 1));
    }

    public void generateIfElseStatement(DTE ifElse) throws Exception {
        checkTokenType(ifElse, "if");
        checkTokenType(ifElse.getNthBrother(5), "else");

        DTE ifConditionNode = ifElse.getBrother();
        DTE ifPart = ifElse.getNthBrother(3);
        DTE elsePart = ifElse.getNthBrother(7);

        VarReg ifCondition = evaluateBooleanExpression(ifConditionNode);

        // need to add branch jump of size |code(ifPart)| + 2
        int before = instructionsRealSize();
        int index = instructionsSize();
        Configuration.getInstance().freeRegister(ifCondition.register);
        generateStS(ifPart);
        int ifPartSize = instructionsRealSize() - before;

        addInstruction(index, Instruction.beqz(ifCondition.register, ifPartSize + 2));

        before = instructionsRealSize();
        index = instructionsSize();
        generateStS(elsePart);
        int elsePartSize = instructionsRealSize() - before;

        addInstruction(index, Instruction.blez(0, elsePartSize + 1));
    }

    private void generateRSt(DTE rSt, FunctionCall call) throws Exception {
        checkTokenType(rSt, "<rSt>");
        // assert call.getResultDestination() != null;

        VarReg expr;
        DTE node = rSt.getNthSon(2);

        log("return exp: " + node.getBorderWord());
        if (node.isType("<E>")) {
            expr = evaluateExpression(node);
        } else if (node.isType("<BE>")) {
            expr = evaluateBooleanExpression(node);
        } else if (node.isType("<CC>")) {
            expr = evaluateCharacterConstant(node);
        } else throw new IllegalArgumentException("Grammar error on " + rSt.getBorderWord());


        // get the result address, decrease stack pointer, and return
        if (!call.getFunction().getName().equals("main")) {
            int frameSize = call.getFunction().getSize();
            int reg = Configuration.getInstance().getFirstFreeRegister();
            addInstruction(Instruction.lw(reg, SPT, -(frameSize + 4)));
            addInstruction(Instruction.sw(expr.register, reg, 0));
            Configuration.getInstance().freeRegister(reg);
            addInstruction(Instruction.lw(1, SPT, -(frameSize + 8)));
            addInstruction(Instruction.addi(SPT, SPT, -(frameSize + 8)));
            addInstruction(Instruction.jr(1));

            retainedRegister = -1;
        } else { // return from `main`
            addInstruction(Instruction.sysc());
        }

        Configuration.getInstance().popStack();
    }

    private void setParameters(Fun function, DTE paS) throws Exception {
        checkTokenType(paS, "<PaS>");
        List<Map.Entry<String, Variable>> params = function
                .getMemoryStruct()
                .getType()
                .getStructComponentNamesSortedByDisplacement();
//                .subList(0, function.getNumParameters());

        int index = 0;
        List<DTE> paSFlattened = paS.getFlattenedSequence();
        if (paSFlattened.size() != function.getNumParameters()) {
            throw new IllegalArgumentException("Incorrect number of params! expected: " + function.getNumParameters() + ", got: " + paSFlattened.size());
        }
        for (DTE pa : paSFlattened) {
            checkTokenType(pa, "<Pa>");
            VarReg expr;
            pa = pa.getFirstSon();
            if (pa.isType("<E>")) {
                expr = evaluateExpression(pa);
            } else if (pa.isType("<BE>")) {
                expr = evaluateBooleanExpression(pa);
            } else if (pa.isType("<CC>")) {
                expr = evaluateCharacterConstant(pa);
            } else throw new IllegalArgumentException("Grammar error on " + paS.getBorderWord());

            Variable parameter = params.get(index).getValue();

            checkSameTypes(parameter.getType(), expr.type);

            int imm = -function.getSize() + parameter.getDisplacement();
            CodeGenerator.getInstance().addInstruction(Instruction.sw(expr.register, SPT, imm));
            Configuration.getInstance().freeRegister(expr.register);
            index++;
        }
    }

    private void setParametersRegisterList(List<Integer> registers, Fun function, DTE paS) throws Exception {
        checkTokenType(paS, "<PaS>");
        List<Map.Entry<String, Variable>> params = function
                .getMemoryStruct()
                .getType()
                .getStructComponentNamesSortedByDisplacement();
//                .subList(0, function.getNumParameters());

        int index = 0;
        List<DTE> paSFlattened = paS.getFlattenedSequence();
        if (paSFlattened.size() != function.getNumParameters()) {
            throw new IllegalArgumentException("Incorrect number of params! expected: " + function.getNumParameters() + ", got: " + paSFlattened.size());
        }
        for (DTE pa : paSFlattened) {
    
            Variable parameter = params.get(index).getValue();
            Integer reg = registers.get(index);
            int imm = -function.getSize() + parameter.getDisplacement();
            CodeGenerator.getInstance().addInstruction(Instruction.sw(reg, SPT, imm));
            Configuration.getInstance().freeRegister(reg);
            index++;
        }
    }


    private List<Integer> getParametersRegisterList(Fun function, DTE paS) throws Exception {
        checkTokenType(paS, "<PaS>");
        List<Map.Entry<String, Variable>> params = function
                .getMemoryStruct()
                .getType()
                .getStructComponentNamesSortedByDisplacement();
//                .subList(0, function.getNumParameters());

        int index = 0;
        List<Integer> registers = new LinkedList<>();
        List<DTE> paSFlattened = paS.getFlattenedSequence();
        if (paSFlattened.size() != function.getNumParameters()) {
            throw new IllegalArgumentException("Incorrect number of params! expected: " + function.getNumParameters() + ", got: " + paSFlattened.size());
        }
        for (DTE pa : paSFlattened) {
            checkTokenType(pa, "<Pa>");
            VarReg expr;
            pa = pa.getFirstSon();
            if (pa.isType("<E>")) {
                expr = evaluateExpression(pa);
            } else if (pa.isType("<BE>")) {
                expr = evaluateBooleanExpression(pa);
            } else if (pa.isType("<CC>")) {
                expr = evaluateCharacterConstant(pa);
            } else throw new IllegalArgumentException("Grammar error on " + paS.getBorderWord());

            Variable parameter = params.get(index).getValue();

            checkSameTypes(parameter.getType(), expr.type);

            registers.add(expr.register);

            int imm = -function.getSize() + parameter.getDisplacement();
    
            index++;
        }
        return registers;
    }

    public void initializeLocalVariables(Fun function) {

        List<Map.Entry<String, Variable>> localVariables = function
                .getMemoryStruct()
                .getType()
                .getStructComponentNamesSortedByDisplacement();

        if (localVariables.size() == function.getNumParameters()) return;

        int displacement = localVariables.get(function.getNumParameters()).getValue().getDisplacement();
        int rt = -function.getSize() + displacement;

        int memoryWordsOccupied = localVariables
                .subList(function.getNumParameters(), localVariables.size())
                .stream()
                .mapToInt(entry -> entry.getValue().getType().size)
                .sum();

        int firstReg = Configuration.getInstance().getFirstFreeRegister();
        int secondReg = Configuration.getInstance().getFirstFreeRegister();

        CodeGenerator.getInstance().addInstruction(Instruction.addi(firstReg, SPT, rt));
        CodeGenerator.getInstance().addInstruction(Instruction.addi(secondReg, 0, memoryWordsOccupied / 4));
        CodeGenerator.getInstance().addInstruction("macro: zero(" + firstReg + ", " + secondReg + ")");

        Configuration.getInstance().freeRegister(firstReg);
        Configuration.getInstance().freeRegister(secondReg);
    }

    public static void reset() {
        INSTANCE = null;
    }
}