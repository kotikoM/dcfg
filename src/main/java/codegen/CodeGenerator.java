package codegen;

import config.Configuration;
import config.FunctionCall;
import grammar.Grammar;
import model.Fun;
import model.VarReg;
import model.Variable;
import table.FunctionTable;
import tree.DTE;

import java.util.*;
import java.util.stream.Collectors;

import static codegen.ConstantEvaluator.evaluateCharacterConstant;
import static codegen.ExpressionEvaluator.evaluateBooleanExpression;
import static codegen.ExpressionEvaluator.evaluateExpression;
import static codegen.IdEvaluator.evaluateId;
import static codegen.MemoryHelper.increaseHeapPointer;
import static codegen.MemoryHelper.increaseStackPointer;
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

    public void addInstruction(String instr) {
        functionInstructions
                .get(Configuration.getInstance().currentFunction().getName())
                .add(instr);
    }

    public void addInstruction(int index, String instr) {
        functionInstructions
                .get(Configuration.getInstance().currentFunction().getName())
                .add(index, instr);
    }

    public int instructionsSize() {
        return functionInstructions
                .get(Configuration.getInstance().currentFunction().getName())
                .size();
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
            int reg = Configuration.getInstance().getFirstFreeRegister();
            addInstruction(Instruction.sw(31, SPT, -(call.getFunction().getSize() + 8)));
            Configuration.getInstance().freeRegister(reg);
        }

        if (body.getFirstSon().isType("<rSt>")) {
            generateRSt(body.getFirstSon(), call);
        } else {
            generateStS(body.getFirstSon());
            generateRSt(body.getNthSon(3), call);
        }
    }

    private void generateCodeVoidFunctionCall(FunctionCall call) throws Exception {
        DTE StS = call.getFunction().getBody();
        checkTokenType(StS, "<StS>");

        int reg = Configuration.getInstance().getFirstFreeRegister();
        addInstruction(Instruction.addi(reg, 0, -call.getFunction().getSize() - 4));
        addInstruction(Instruction.sw(RA, reg, 0));
        Configuration.getInstance().freeRegister(reg);

        generateStS(StS);
        int frameSize = call.getFunction().getSize() + 4;

        addInstruction(Instruction.lw(1, SPT, -frameSize) + " # start of return from " + call.getFunction().getName());
        addInstruction(Instruction.addi(SPT, SPT, -frameSize));
        addInstruction(Instruction.jr(1) + " # end of return from " + call.getFunction().getName());

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
        // <id> = gpr(<DiS>) | gpr(<DiS>) = <id> {<NuS>}
        else if (st.getNthSon(3).isType("gpr")){
            DTE id = st.getFirstSon();
            DTE registerIdx = st.getNthSon(5);

            log("Read gpr: " + registerIdx.getBorderWord() + " into id: " + id.getBorderWord());
            generateReadGPR(id, registerIdx); 
        }

        // <id> = <E> | <id> = <BE> | <id> = <CC> | <id> = <Na>(<PaS>?) | <id> = new <Na>* | <id> = gpr(<Dis>) | <id> = gpr(<Dis>) {<NuS}
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

            log("Function call: " + fun.getBorderWord());
            generateVoidFun(fun); 
        }
        

        // Invalid statement | Unhandled case
        else {
            throw new IllegalArgumentException("Grammar error on \"" + st.getBorderWord() + "\"");
        }
    }

    public void generateInlineASM(DTE asmContent){

        // asm( <ASM> )
        checkTokenType(asmContent, "<ASM>");
        String content = asmContent.getBorderWord();

        addInstruction(content);

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

        if (id.getNthBrother(1) != null){
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
        if (id.getNthBrother(6) != null){
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
        
        return; 

    }

    public void generateVoidFun(DTE fun) throws Exception {
        // <Na>(<PaS>?)
        checkTokenType(fun, "<Na>");

        String functionName = fun.getBorderWord();

        Fun function = FunctionTable.getInstance().getFunction(functionName);
        increaseStackPointer(function.getSize());

        if (fun.getNthBrother(2).isType("<PaS>")) {
            log("found parameters: " + fun.getNthBrother(2).getBorderWord());
            setParameters(function, fun.getNthBrother(2));
        }

        initializeLocalVariables(function);
        addInstruction(Instruction.jal("_" + functionName));

        if (!functionInstructions.containsKey(functionName)) {
            functionInstructions.put(functionName, new LinkedList<>());
            FunctionCall call = Configuration.getInstance().callFunction(functionName);
            generateCodeVoidFunctionCall(call);
        }

        return ;
    }

    public void generateAssignment(DTE id, DTE value) throws Exception {
        // <id> = value
        // E -> T -> F -> C -> DiS -> Di -> 1
        VarReg varRegId;
        VarReg varRegValue;
        
        if (value.isType("<Na>")){
            // id = Na() | id = Na(PaS) left
            // checkTokenType(value, "<Na>");

            String functionName = value.getBorderWord();
            log("function call: " + functionName);

            Fun function = FunctionTable.getInstance().getFunction(functionName);
            increaseStackPointer(function.getSize());

            varRegId = evaluateId(id, true);
            addInstruction(Instruction.sw(varRegId.register, SPT, -(function.getSize() + 4)));
            Configuration.getInstance().freeRegister(varRegId.register);


            if (value.getNthBrother(2).isType("<PaS>")) {
                log("found parameters: " + value.getNthBrother(2).getBorderWord());
                setParameters(function, value.getNthBrother(2));
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
        varRegId = evaluateId(id, true);
        // case split for type of value
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

    public void printInstructions() {
        System.out.println("\n\n------ GENERATED INSTRUCTIONS ------");
        List<String> mainInstructions = functionInstructions.get("main");
        System.out.println("_main:");
        mainInstructions.forEach(System.out::println);
        System.out.println();

        functionInstructions.forEach((key, value) -> {
            if (!key.equals("main")) {
                System.out.println("_" + key);
                value.forEach(System.out::println);
                System.out.println();
            }
        });
        System.out.println("--------------------------");
    }

    public String getInstructions() {
        StringBuilder res = new StringBuilder();
        res.append("_main:\n");
        List<String> mainInstructions = functionInstructions.get("main");
        res.append(String.join("\n", mainInstructions))
                .append("\n");

        functionInstructions.forEach((key, value) -> {
            if (!key.equals("main")) {
                res.append("\n_").append(key).append(":\n")
                        .append(String.join("\n", value))
                        .append("\n");
            }
        });
        return res.toString();
    }

    public void generateLoop(DTE dte) throws Exception {
        checkTokenType(dte, "while");

        // while <E> { <StS> }

        DTE expressionNode = dte.getBrother();
        assert expressionNode != null && expressionNode.getBrother() != null;

        DTE bodyNode = dte.getNthBrother(3);
        assert bodyNode != null;


        int before = instructionsSize();
        VarReg expression = evaluateBooleanExpression(expressionNode);
        int expressionCodeSize = instructionsSize() - before;


        // need to add branch jump of size |code(whileBody)| + 2
        before = instructionsSize();
        generateStS(bodyNode);
        int bodySize = instructionsSize() - before;

        String instr = Instruction.beqz(expression.register, bodySize + 2);
        addInstruction(before, instr);

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

        int before = instructionsSize();
        generateStS(ifPart);
        int ifPartSize = instructionsSize() - before;

        addInstruction(before, Instruction.beqz(ifCondition.register, ifPartSize + 1));
    }

    public void generateIfElseStatement(DTE ifElse) throws Exception {
        checkTokenType(ifElse, "if");
        checkTokenType(ifElse.getNthBrother(5), "else");

        DTE ifConditionNode = ifElse.getBrother();
        DTE ifPart = ifElse.getNthBrother(3);
        DTE elsePart = ifElse.getNthBrother(7);

        VarReg ifCondition = evaluateBooleanExpression(ifConditionNode);

        // need to add branch jump of size |code(ifPart)| + 2
        int before = instructionsSize();
        generateStS(ifPart);
        int ifPartSize = instructionsSize() - before;

        addInstruction(before, Instruction.beqz(ifCondition.register, ifPartSize + 2));

        before = instructionsSize();
        generateStS(elsePart);
        int elsePartSize = instructionsSize() - before;

        addInstruction(before, Instruction.beq(0, elsePartSize + 1));
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
            addInstruction(Instruction.sw(expr.register, SPT, -(frameSize + 4)) + " # start of return from " + call.getFunction().getName());

            addInstruction(Instruction.lw(1, SPT, -(frameSize + 8)));
            addInstruction(Instruction.addi(SPT, SPT, -(frameSize + 8)));
            addInstruction(Instruction.jr(1) + " # end of return from " + call.getFunction().getName());

            retainedRegister = -1;
        } else { // return from `main`
            addInstruction("HALT");
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