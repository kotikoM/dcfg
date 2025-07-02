package codegen;

import config.Configuration;
import model.VarReg;
import model.VarType;
import model.VarType.TypeClass;
import tree.DTE;

import static codegen.ConstantEvaluator.evaluateBooleanConstant;
import static codegen.ConstantEvaluator.evaluateNumberConstant;
import static codegen.IdEvaluator.evaluateId;
import static util.Logger.log;
import static util.TypeUtils.checkSameTypes;
import static util.TypeUtils.checkTokenType;

import java.lang.reflect.Type;
import java.util.List;
import java.util.LinkedList;

public class ExpressionEvaluator {
    private static CodeGenerator cg() {
        return CodeGenerator.getInstance();
    }

    public static VarReg evaluateExpression(DTE expression) throws Exception {
        checkTokenType(expression, "<E>", "<T>", "<F>");

        log("Evaluating expression: " + expression.getBorderWord());

        // any of <E>, <T>, <F> token's derivations has at most 3 children.
        DTE first;
        DTE second = null;
        DTE third = null;

        first = expression.getFirstSon();
        if (first != null && first.getBrother() != null) {
            second = first.getBrother();
        }
        if (second != null && second.getBrother() != null) {
            third = second.getBrother();
        }

        if (third != null) {
            // e' binOp e'' OR (E)

            // handle (<E>) separately
            if (first.isType("(")) {
                return evaluateExpression(second);
            }

            VarReg leftOperand = evaluateExpression(first);
            VarReg rightOperand = evaluateExpression(third);

            return evaluateBinaryOperation(leftOperand, rightOperand, second);
        }

        if (second != null) { // only possible case is -F (unary minus on factor)
            log("Unary minus detected.");

            checkTokenType(first, "-");

            VarReg f = evaluateExpression(second);
            // perform unary minus operation
            String instr = Instruction.sub(f.register, 0, f.register);
            cg().addInstruction(instr);

            return f;
        }

        if (first != null) { // T | F | id | C
            checkTokenType(first, "<T>", "<F>", "<id>", "<C>");

            if (first.isType("<T>") || first.isType("<F>")) {
                return evaluateExpression(first);
            }

            if (first.isType("<id>")) {
                return evaluateId(first, false);
            }

            return evaluateNumberConstant(first);
        }

        throw new Exception("Grammar error on: " + expression.getBorderWord());
    }

    public static VarReg evaluateBooleanExpression(DTE be) throws Exception {
        checkTokenType(be, "<BE>", "<BT>", "<BF>");

        DTE first;
        DTE second;
        DTE third;

        first = be.getFirstSon();
        assert first != null;

        if (first.isType("(") && first.getBrother().isType("bool")) {
            first = first.getNthBrother(3);
            return evaluateId(first, false);
        }

        if (first.isType("<Atom>")) {
            return evaluateAtom(first);
        }

        if (first.isType("<BF>")) {
            return evaluateBooleanExpression(first);
        }

        if (first.isType("<BE>")) { // <BE> || <BT>
            second = first.getBrother();
            checkTokenType(second, "||");

            third = second.getBrother();
            checkTokenType(third, "<BT>");

            VarReg left = evaluateBooleanExpression(first);
            VarReg right = evaluateBooleanExpression(third);

            String instr = Instruction.or(left.register, left.register, right.register);
            cg().addInstruction(instr);

            Configuration.getInstance().freeRegister(right.register);
            return new VarReg(left.register, VarType.BOOL_TYPE);
        }

        if (first.isType("<BT>")) { // Either <BT> or <BT> && <BF>
            if (first.getBrother() == null) return evaluateBooleanExpression(first);

            second = first.getBrother();
            checkTokenType(second, "&&");

            third = second.getBrother();
            checkTokenType(third, "<BF>");

            VarReg left = evaluateBooleanExpression(first);
            VarReg right = evaluateBooleanExpression(third);

            String instr = Instruction.and(left.register, left.register, right.register);
            cg().addInstruction(instr);

            Configuration.getInstance().freeRegister(right.register);
            return new VarReg(left.register, VarType.BOOL_TYPE);
        }

        if (first.isType("!")) {
            second = first.getBrother();
            checkTokenType(second, "<BF>");

            VarReg factor = evaluateBooleanExpression(second);


            cg().addInstruction(Instruction.subi(factor.register, factor.register, 1));
            cg().addInstruction(Instruction.slti(factor.register, factor.register, 0)); //  "set lt " + factor.register + " 0");

            return factor;
        }

        if (first.isType("(")) {
            second = first.getBrother();
            checkTokenType(second, "<BE>");

            return evaluateBooleanExpression(second);
        }

        throw new IllegalArgumentException("Grammar error! \"" + be.getBorderWord() + "\"");
    }

    private static VarReg evaluateBinaryOperation(VarReg left, VarReg right, DTE binOp) {
        checkSameTypes(left.type, right.type);

        int leftRegister = left.register;
        int rightRegister = right.register;
        VarType expressionType = left.type;

        return switch (expressionType.typeClass) {
            case INT -> evaluateSignedBinaryOperation(leftRegister, rightRegister, binOp);
            case UINT -> evaluateUnsignedBinaryOperation(leftRegister, rightRegister, binOp);
            default -> throw new IllegalArgumentException("Expected INT/UINT, " + " got " + expressionType.typeClass);
        };
    }

    private static VarReg evaluateSignedBinaryOperation(int leftRegister, int rightRegister, DTE binOp) {
        int freeReg = Configuration.getInstance().getFirstFreeRegister();
        String instr = switch (binOp.labelContent()) {
            case "+" -> Instruction.add(leftRegister, leftRegister, rightRegister);
            case "-" -> Instruction.sub(leftRegister, leftRegister, rightRegister);
            case "*" -> "macro: mul(" + leftRegister + ", " + leftRegister + ", " + rightRegister + ")";
            case "/" -> "macro: divt(" + freeReg + ", " + leftRegister + ", " + rightRegister + ")";
            default -> throw new IllegalArgumentException("Expected binary operator, got " + binOp.labelContent());
        };

        cg().addInstruction(instr);
        Configuration.getInstance().freeRegister(rightRegister);
        if (binOp.labelContent().equals("/")){
            Configuration.getInstance().freeRegister(leftRegister);
            return new VarReg(freeReg, VarType.INT_TYPE);
        }
        return new VarReg(leftRegister, VarType.INT_TYPE);
    }

    private static VarReg evaluateUnsignedBinaryOperation(int leftRegister, int rightRegister, DTE binOp) {
        int freeReg = Configuration.getInstance().getFirstFreeRegister();
        String instr = switch (binOp.labelContent()) {
            case "+" -> Instruction.addu(leftRegister, leftRegister, rightRegister);
            case "-" -> Instruction.subu(leftRegister, leftRegister, rightRegister);
            case "*" -> "macro: mul(" + leftRegister + ", " + leftRegister + ", " + rightRegister + ")";
            case "/" -> "macro: divu(" + freeReg + ", " + leftRegister + ", " + rightRegister + ")";
            default -> throw new IllegalArgumentException("Expected binary operator, got " + binOp.labelContent());
        };

        cg().addInstruction(instr);
        Configuration.getInstance().freeRegister(rightRegister);
        if (binOp.labelContent().equals("/")){
            Configuration.getInstance().freeRegister(leftRegister);
            return new VarReg(freeReg, VarType.UINT_TYPE);
        }
        return new VarReg(leftRegister, VarType.UINT_TYPE);
    }

    private static VarReg evaluateAtom(DTE atom) throws Exception {
        checkTokenType(atom, "<Atom>");

        DTE left, op, right;

        left = atom.getFirstSon();
        assert left != null;

        if (left.isType("<BC>")) {
            return evaluateBooleanConstant(left);
        }

        op = left.getBrother();
        assert op != null;
        right = op.getBrother();
        assert right != null;

        checkTokenType(left, "<E>");
        checkTokenType(right, "<E>");

        VarReg leftVar = evaluateExpression(left);
        VarReg rightVar = evaluateExpression(right);
        int leftReg = leftVar.register;
        int rightReg = rightVar.register;
        TypeClass expressionType = leftVar.type.typeClass;
        String binOp = op.labelContent();     
        
        checkSameTypes(leftVar.type, rightVar.type);

        boolean intType = expressionType.equals(TypeClass.INT);
        boolean uintType = expressionType.equals(TypeClass.UINT);
        boolean numbers = intType || uintType; 
        boolean simple = numbers || expressionType.equals(TypeClass.POINTER) || expressionType.equals(TypeClass.BOOL) || expressionType.equals(TypeClass.CHAR);


        if ((binOp.equals("==") || binOp.equals("!=")) && simple){
            return evaluateEqualNot(leftReg, rightReg, binOp);
        }
        else if (intType){
            return evaluateSignedAtom(leftReg, rightReg, binOp);
        }
        else if (uintType){
            return evaluateUnsignedAtom(leftReg, rightReg, binOp);
        }
        else {
            throw new IllegalArgumentException("Grammar error on \"" + atom.getBorderWord() + "\"");
        }

    }

    public static VarReg evaluateSignedAtom(int leftReg, int rightReg, String binOp){

        List<String> inst = new LinkedList<>();

        switch(binOp){
            case "<" -> inst.add(Instruction.slt(leftReg, leftReg, rightReg));
            case ">" -> inst.add(Instruction.slt(leftReg, rightReg, leftReg));
            case "<=" -> {
                        inst.add(Instruction.slt(leftReg, rightReg, leftReg));
                        inst.add(Instruction.xori(leftReg, leftReg, 1));
                }
            case ">=" -> {
                        inst.add(Instruction.slt(leftReg, leftReg, rightReg));
                        inst.add(Instruction.xori(leftReg, leftReg, 1));
                }
            default -> throw new IllegalArgumentException("Expected Atom, got " + binOp);   
        }

        inst.forEach(instr -> cg().addInstruction(instr));
        Configuration.getInstance().freeRegister(rightReg);
        return new VarReg(leftReg, VarType.BOOL_TYPE);
    }

    public static VarReg evaluateUnsignedAtom(int leftReg, int rightReg, String binOp){

        List<String> inst = new LinkedList<>();

        switch(binOp){
            case "<" -> inst.add(Instruction.sltu(leftReg, leftReg, rightReg));
            case ">" -> inst.add(Instruction.sltu(leftReg, rightReg, leftReg));
            case "<=" -> {
                        inst.add(Instruction.sltu(leftReg, rightReg, leftReg));
                        inst.add(Instruction.xori(leftReg, leftReg, 1));
                }
            case ">=" -> {
                        inst.add(Instruction.sltu(leftReg, leftReg, rightReg));
                        inst.add(Instruction.xori(leftReg, leftReg, 1));
                }
            default -> throw new IllegalArgumentException("Expected Atom, got " + binOp);   
        }

        inst.forEach(instr -> cg().addInstruction(instr));
        Configuration.getInstance().freeRegister(rightReg);
        return new VarReg(leftReg, VarType.BOOL_TYPE);
    }

    public static VarReg evaluateEqualNot(int leftReg, int rightReg, String binOp){

        List<String> inst = new LinkedList<>();

        switch(binOp) {
            case "==" -> inst.add(Instruction.bne(leftReg, rightReg, 3));
            case "!=" -> inst.add(Instruction.beq(leftReg, rightReg, 3));
            default -> throw new IllegalArgumentException("Expected Atom, got " + binOp);
        }
        
        inst.add(Instruction.addi(leftReg, 0, 1));
        inst.add(Instruction.blez(0, 2));
        inst.add(Instruction.add(leftReg, 0, 0));
        
        inst.forEach(instr -> cg().addInstruction(instr));
        Configuration.getInstance().freeRegister(rightReg);
        return new VarReg(leftReg, VarType.BOOL_TYPE);

    }
    

    

}
