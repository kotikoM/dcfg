package codegen;

import config.Configuration;

import java.util.List;

import static util.Context.*;

public class MemoryHelper {
    public static void increaseHeapPointer(int size) {
        List.of(
                Instruction.addi(HPT, HPT, size),
                Instruction.subi(1, HPT, HMAX),
                Instruction.bltz(1, 4),
                "macro: gpr(1) = enc(42, uint)",
                Instruction.sysc(),
                Instruction.addi(1, HPT, -size),
                Instruction.addi(2, 0, size / 4),
                "macro: zero(1, 2)"
        ).forEach(CodeGenerator.getInstance()::addInstruction);
    }

    public static void increaseStackPointer(int size) {
        int reg = Configuration.getInstance().getFirstFreeRegister();
        List.of(
                Instruction.addi(reg, SPT, size),
                Instruction.subi(reg, reg, SMAX),
                Instruction.blez(reg, 4),
                "macro: gpr(1) = enc(41, uint)",
                Instruction.sysc(),
                Instruction.addi(SPT, SPT, size + 8)
        ).forEach(CodeGenerator.getInstance()::addInstruction);
        Configuration.getInstance().freeRegister(reg);
    }

    public static void increaseVoidStackPointer(int size){
        int reg = Configuration.getInstance().getFirstFreeRegister();
        List.of(
                Instruction.addi(reg, SPT, size),
                Instruction.subi(reg, reg, SMAX),
                Instruction.blez(reg, 4),
                "macro: gpr(1) = enc(41, uint)",
                Instruction.sysc(),
                Instruction.addi(SPT, SPT, size + 4)
        ).forEach(CodeGenerator.getInstance()::addInstruction);
        Configuration.getInstance().freeRegister(reg);     
    }
}
