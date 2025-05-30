package codegen;

import config.Configuration;
import grammar.Symbol;
import model.VarReg;
import model.VarType;
import tree.DTE;

import java.util.List;

import static util.TypeUtils.checkTokenType;

public class ConstantEvaluator {
    private static CodeGenerator cg() {
        return CodeGenerator.getInstance();
    }

    public static VarReg evaluateNumberConstant(DTE constant) {
        checkTokenType(constant, "<C>");

        int register = Configuration.getInstance().getFirstFreeRegister();
        String value = constant.getBorderWord();

        long intValue;
        VarType type;
        String instr;

        if (value.charAt(value.length() - 1) != 'u') {
            intValue = Integer.parseInt(value);
            type = VarType.INT_TYPE;
            instr = "macro: gpr(" + register + ") = enc(" + intValue + ", int)";
        } else {
            intValue = Long.parseLong(value.substring(0, value.length() - 1));
            if (intValue > (1L << 32) - 1L){
                throw new IllegalArgumentException("Integer constant out of range: " + value); 
            }
            type = VarType.UINT_TYPE;
            instr = "macro: gpr(" + register + ") = enc(" + intValue + ", uint)";
        }

        cg().addInstruction(instr);
        return new VarReg(register, type);
    }

    public static VarReg evaluateBooleanConstant(DTE bc) {
        checkTokenType(bc, "<BC>");

        int register = Configuration.getInstance().getFirstFreeRegister();
        String value = bc.getFirstSon().labelContent(); 

        if (!value.equals("true") & !value.equals("false")){
            throw new IllegalArgumentException("Expected boolean constant, got " + value);
        }
        
        String instr = "macro: gpr(" + register + ") = enc(" + value + ", bool)";
        cg().addInstruction(instr);

        return new VarReg(register, VarType.BOOL_TYPE);
    }

    public static VarReg evaluateCharacterConstant(DTE charConstant) {
        String value = charConstant.getBorderWord();

        List<String> terminals = cg().g.getTerminals().stream().map(Symbol::getContent).filter(term -> term.length() == 1).toList();
        if (!terminals.contains(value)) {
            throw new IllegalArgumentException("Expected char constant, got " + value);
        }

        int register = Configuration.getInstance().getFirstFreeRegister();

        String instr = "macro: gpr(" + register + ") = enc(" + value.charAt(0) + ", char)";
        cg().addInstruction(instr);

        return new VarReg(register, VarType.CHAR_TYPE);
    }
}
