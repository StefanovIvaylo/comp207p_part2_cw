package comp207p.main;

import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.*;
import org.apache.bcel.util.InstructionFinder;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;


public class ConstantFolder {
    ClassParser parser = null;
    ClassGen gen = null;

    JavaClass original = null;
    JavaClass optimized = null;

    public ConstantFolder(String classFilePath) {
        try {
            this.parser = new ClassParser(classFilePath);
            this.original = this.parser.parse();
            this.gen = new ClassGen(this.original);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public void optimize() {
        ClassGen cgen = new ClassGen(original);
        cgen.setMajor(49);
        ConstantPoolGen cpgen = cgen.getConstantPool();

        Method[] methods = cgen.getMethods();
        for (Method method : methods) {

            System.out.println(cgen.getClassName() + " > " + method.getName());
            System.out.println(new InstructionList(method.getCode().getCode()));

            Method improved = improveMethod(method, cgen, cpgen);

            System.out.println(cgen.getClassName() + " > " + method.getName() + " OPTIMISED!!!!");
            System.out.println(new InstructionList(improved.getCode().getCode()));

            cgen.replaceMethod(method, improved);
        }


        this.optimized = cgen.getJavaClass();
    }

    private Method improveMethod(Method method, ClassGen cgen, ConstantPoolGen cpgen) {
        Code methodCode = method.getCode();
        InstructionList instructionList = new InstructionList(methodCode.getCode());

        MethodGen methodGen = new MethodGen(method.getAccessFlags(), method.getReturnType(), method.getArgumentTypes(),
                null, method.getName(), cgen.getClassName(), instructionList, cpgen);


        for (int i = 0; i < 1; i++) {
        }

        int count = 1;

        while(count > 0) {
            count = 0;
            count += propagation(instructionList);
            count += simpleFolding(methodGen, instructionList);
        }

        instructionList.setPositions(true);
        methodGen.setMaxStack();
        methodGen.setMaxLocals();

        return methodGen.getMethod();
    }

    private int simpleFolding(MethodGen mg, InstructionList il) {
        ConstantPoolGen cpgen = mg.getConstantPool();
        InstructionFinder f = new InstructionFinder(il);

        int counter = 0;

        String regexp = "(PushInstruction ConversionInstruction) | (PushInstruction PushInstruction ArithmeticInstruction) | (PushInstruction PushInstruction IfInstruction) | (PushInstruction PushInstruction LCMP) | (PushInstruction IfInstruction)";

        for (Iterator i = f.search(regexp); i.hasNext();) {

            InstructionHandle[] match = (InstructionHandle[]) i.next();


            if(match.length == 3 && match[2].getInstruction() instanceof ArithmeticInstruction) {
                PushInstruction l = (PushInstruction)match[0].getInstruction();
                PushInstruction r = (PushInstruction)match[1].getInstruction();
                Instruction op = match[2].getInstruction();

                Number ln = getVal(l, cpgen);
                Number rn = getVal(r, cpgen);

                if (ln == null || rn == null) {
                    continue;
                }

                Instruction fold = arithmeticFold(mg, ln, rn, op);

                match[0].setInstruction(fold);

                try {
                    il.delete(match[1], match[2]);
                }
                catch (TargetLostException e) {
                    e.printStackTrace();
                }

                counter+=1;
            }
            else if(match.length == 2 && match[1].getInstruction() instanceof ConversionInstruction) {

                PushInstruction l = (PushInstruction)match[0].getInstruction();
                ConversionInstruction op = (ConversionInstruction)match[1].getInstruction();


                Number ln = getVal(l, cpgen);

                if(ln == null) {
                    continue;
                }

                Instruction fold = conversionFold(mg, ln, op);

                match[0].setInstruction(fold);
                try {
                    il.delete(match[1]);
                }
                catch (TargetLostException e) {
                    e.printStackTrace();
                }
                counter+=1;
            }
            else if(match.length == 3 && match[2].getInstruction() instanceof IfInstruction){
                PushInstruction l = (PushInstruction)match[0].getInstruction();
                PushInstruction r = (PushInstruction)match[1].getInstruction();
                Instruction branch = match[2].getInstruction();

                Number ln = getVal(l, cpgen);
                Number rn = getVal(r, cpgen);

                if (ln == null || rn == null) {
                    continue;
                }

                Instruction fold = branchFold(mg, ln, rn, branch);

                if(fold instanceof BranchInstruction){
                    match[2].setInstruction(fold);
                    try{
                        il.delete(match[0], match[1]);
                    }
                    catch (TargetLostException e){
                        e.printStackTrace();
                    }
                }
                else{
                    match[0].setInstruction(fold);
                    try {
                        il.delete(match[1], match[2]);
                    }
                    catch (TargetLostException e) {
                        e.printStackTrace();
                    }
                }
                counter += 1;
            }

            else if(match.length == 3 && match[2].getInstruction() instanceof LCMP) {
                PushInstruction l = (PushInstruction)match[0].getInstruction();
                PushInstruction r = (PushInstruction)match[1].getInstruction();
                Instruction op = match[2].getInstruction();

                System.out.println(op);

                Number ln = getVal(l, cpgen);
                Number rn = getVal(r, cpgen);

                if (ln == null || rn == null) {
                    continue;
                }

                Instruction fold = lcmpFold(mg, ln, rn, op);
                match[0].setInstruction(fold);

                try {
                    il.delete(match[1], match[2]);
                }
                catch (TargetLostException e) {
                    e.printStackTrace();
                }

                counter+=1;
            }
            else if(match.length == 2 && match[1].getInstruction() instanceof IfInstruction) {

                PushInstruction l = (PushInstruction)match[0].getInstruction();
                IfInstruction op = (IfInstruction) match[1].getInstruction();


                Number ln = getVal(l, cpgen);

                if(ln == null) {
                    continue;
                }

                Instruction fold = singleBranchFold(mg, ln, op);

                if(fold instanceof BranchInstruction){
                    match[1].setInstruction(fold);
                    try {
                        il.delete(match[0]);
                    }
                    catch (TargetLostException e) {
                        e.printStackTrace();
                    }
                }
                else{
                    match[0].setInstruction(fold);
                    try {
                        il.delete(match[1]);
                    }
                    catch (TargetLostException e) {
                        e.printStackTrace();
                    }
                }
                counter+=1;
            }

        }


        return counter;
    }


    private static class LoadsInfo {
        InstructionHandle storeInstruction; //like store_1
        InstructionHandle stored; //like push 70
        ArrayList<InstructionHandle> correspondingLoads;

        public LoadsInfo(InstructionHandle storeInstruction, InstructionHandle stored) {
            this.storeInstruction = storeInstruction;
            this.stored = stored;
            correspondingLoads = new ArrayList<>();
        }

        public void addLoad(InstructionHandle loadInstruction) {
            correspondingLoads.add(loadInstruction);
        }
    }


    void propagateStoreToLoads(LoadsInfo loadsInfo, InstructionList instructionList) {
        //replace all loads by the push value
        loadsInfo.correspondingLoads.forEach(
                instructionHandle -> instructionHandle.setInstruction(loadsInfo.stored.getInstruction().copy()));

//        delete store instruction
        try {
            instructionList.delete(loadsInfo.storeInstruction);
            instructionList.delete(loadsInfo.stored);
        } catch (TargetLostException e) {
            e.printStackTrace();
        }

//        return loadsInfo.stored;
    }


    private int propagation(InstructionList instructionList) {
        InstructionFinder instructionFinder = new InstructionFinder(instructionList);
        String LOAD_REGEX = "ILOAD|DLOAD|FLOAD|LLOAD";
        String STORE_REGEX = "ISTORE|DSTORE|FSTORE|LSTORE";
        String STORE_LOAD_REGEX = LOAD_REGEX + "|" + STORE_REGEX;

        Map<Integer, LoadsInfo> storeToLoadsMap = new HashMap<>();

        final int[] count = {0};

        for (Iterator iter = instructionFinder.search(STORE_LOAD_REGEX); iter.hasNext(); ) {
            InstructionHandle instructionHandle = ((InstructionHandle[]) iter.next())[0];
            Instruction instruction = instructionHandle.getInstruction();

            if (instruction instanceof StoreInstruction) {
                StoreInstruction storeInstruction = (StoreInstruction) instruction;
                int index = storeInstruction.getIndex();

                if (storeToLoadsMap.containsKey(index)) {
                    //replace all the loads
                    LoadsInfo loadsInfo = storeToLoadsMap.get(index);
                    propagateStoreToLoads(loadsInfo, instructionList);
                    count[0]++;
                }

                //creating/replacing/deleting store entry in map
                InstructionHandle prevInstruction = instructionHandle.getPrev();
                if (prevInstruction.getInstruction() instanceof PushInstruction) {
                    //add/replace
                    storeToLoadsMap.put(index, new LoadsInfo(instructionHandle, prevInstruction));
                } else {
                    //delete
                    storeToLoadsMap.remove(index);
                }
            } else if (instruction instanceof LoadInstruction) {
                LoadInstruction loadInstruction = (LoadInstruction) instruction;
                int index = loadInstruction.getIndex();

                if (storeToLoadsMap.containsKey(index)) {
                    LoadsInfo loadsInfo = storeToLoadsMap.get(index);
                    loadsInfo.addLoad(instructionHandle);
                }
                // else { can't be propagated yet}
            }
        }


        //EOF
        storeToLoadsMap.forEach((index, loadsInfo) -> {
            propagateStoreToLoads(loadsInfo, instructionList);
            count[0]++;
        });

        return count[0];
    }

    private Number getVal(PushInstruction inst, ConstantPoolGen cpgen) {
        Number n = null;

        if (inst instanceof LDC) {
            n = (Number) ((LDC) inst).getValue(cpgen);
        } else if (inst instanceof LDC_W) {
            n = (Number) ((LDC_W) inst).getValue(cpgen);
        } else if (inst instanceof LDC2_W) {
            n = (Number) ((LDC2_W) inst).getValue(cpgen);
        } else if (inst instanceof ConstantPushInstruction) {
            n = (Number) ((ConstantPushInstruction) inst).getValue();
        }

        return n;
    }

    private Instruction conversionFold(MethodGen mg, Number b, Instruction op) {
        ConstantPoolGen cpgen = mg.getConstantPool();
        Instruction inst = null;

        if (op instanceof I2D) {
            inst = new LDC2_W(cpgen.addDouble((double) b.doubleValue()));
        } else if (op instanceof I2F) {
            inst = new LDC(cpgen.addFloat((float) b.floatValue()));
        } else if (op instanceof I2L) {
            inst = new LDC2_W(cpgen.addLong((long) b.longValue()));
        } else if (op instanceof D2F) {
            inst = new LDC(cpgen.addFloat((float) b.floatValue()));
        } else if (op instanceof D2I) {
            inst = new LDC(cpgen.addInteger((int) b.intValue()));
        } else if (op instanceof D2L) {
            inst = new LDC2_W(cpgen.addLong((long) b.longValue()));
        } else if (op instanceof F2D) {
            inst = new LDC2_W(cpgen.addDouble((double) b.doubleValue()));
        } else if (op instanceof F2I) {
            inst = new LDC(cpgen.addInteger((int) b.intValue()));
        } else if (op instanceof F2L) {
            inst = new LDC2_W(cpgen.addLong((long) b.longValue()));
        } else if (op instanceof L2I) {
            inst = new LDC(cpgen.addInteger((int) b.intValue()));
        } else if (op instanceof L2D) {
            inst = new LDC2_W(cpgen.addDouble((double) b.doubleValue()));
        } else if (op instanceof L2F) {
            inst = new LDC(cpgen.addFloat((float) b.floatValue()));
        }


        return inst;
    }

    private Instruction arithmeticFold(MethodGen mg, Number a, Number b, Instruction op) {
        ConstantPoolGen cpgen = mg.getConstantPool();
        Instruction inst = null;

        if (op instanceof IADD) {
            inst = new LDC(cpgen.addInteger(a.intValue() + b.intValue()));
        } else if (op instanceof ISUB) {
            inst = new LDC(cpgen.addInteger(a.intValue() - b.intValue()));
        } else if (op instanceof IMUL) {
            inst = new LDC(cpgen.addInteger(a.intValue() * b.intValue()));
        } else if (op instanceof IDIV) {
            inst = new LDC(cpgen.addInteger(a.intValue() / b.intValue()));
        } else if (op instanceof IREM) {
            inst = new LDC(cpgen.addInteger(a.intValue() % b.intValue()));
        } else if (op instanceof DADD) {
            inst = new LDC2_W(cpgen.addDouble(a.doubleValue() + b.doubleValue()));
        } else if (op instanceof DSUB) {
            inst = new LDC2_W(cpgen.addDouble(a.doubleValue() - b.doubleValue()));
        } else if (op instanceof DMUL) {
            inst = new LDC2_W(cpgen.addDouble(a.doubleValue() * b.doubleValue()));
        } else if (op instanceof DDIV) {
            inst = new LDC2_W(cpgen.addDouble(a.doubleValue() / b.doubleValue()));
        } else if (op instanceof DREM) {
            inst = new LDC2_W(cpgen.addDouble(a.doubleValue() % b.doubleValue()));
        } else if (op instanceof FADD) {
            inst = new LDC(cpgen.addFloat(a.floatValue() + b.floatValue()));
        } else if (op instanceof FSUB) {
            inst = new LDC(cpgen.addFloat(a.floatValue() - b.floatValue()));
        } else if (op instanceof FMUL) {
            inst = new LDC(cpgen.addFloat(a.floatValue() * b.floatValue()));
        } else if (op instanceof FDIV) {
            inst = new LDC(cpgen.addFloat(a.floatValue() / b.floatValue()));
        } else if (op instanceof FREM) {
            inst = new LDC(cpgen.addFloat(a.floatValue() % b.floatValue()));
        } else if (op instanceof LADD) {
            inst = new LDC2_W(cpgen.addLong(a.longValue() + b.longValue()));
        } else if (op instanceof LSUB) {
            inst = new LDC2_W(cpgen.addLong(a.longValue() - b.longValue()));
        } else if (op instanceof LMUL) {
            inst = new LDC2_W(cpgen.addLong(a.longValue() * b.longValue()));
        } else if (op instanceof LDIV) {
            inst = new LDC2_W(cpgen.addLong(a.longValue() / b.longValue()));
        } else if (op instanceof LREM) {
            inst = new LDC2_W(cpgen.addLong(a.longValue() % b.longValue()));
        }

        return inst;
    }

    private Instruction branchFold(MethodGen mg, Number a, Number b, Instruction op){
        ConstantPoolGen cpgen = mg.getConstantPool();
        Instruction inst = null;

        if (op instanceof IF_ICMPEQ){
            if(a.intValue() == b.intValue()){
                inst = new GOTO(((IF_ICMPEQ) op).getTarget());
            }
            else{
                inst = new NOP();
            }
        }
        else if (op instanceof IF_ICMPGE){
            if(a.intValue() >= b.intValue()){
                inst = new GOTO(((IF_ICMPGE) op).getTarget());
            }
            else{
                inst = new NOP();
            }
        }
        else if (op instanceof IF_ICMPGT){
            if(a.intValue() > b.intValue()){
                inst = new GOTO(((IF_ICMPGT) op).getTarget());
            }
            else{
                inst = new NOP();
            }
        }
        else if (op instanceof IF_ICMPLE){
            if (a.intValue() <= b.intValue()){
                inst = new GOTO(((IF_ICMPLE) op).getTarget());
            }
            else {
                inst = new NOP();
            }
        }
        else if (op instanceof IF_ICMPLT){
            if (a.intValue() < b.intValue()){
                inst = new GOTO(((IF_ICMPLT) op).getTarget());
            }
            else{
                inst = new NOP();
            }
        }
        else if (op instanceof IF_ICMPNE){
            if (a.intValue() != b.intValue()){
                inst = new GOTO(((IF_ICMPNE) op).getTarget());
            }
            else{
                inst = new NOP();
            }
        }

        return inst;
    }

    private Instruction lcmpFold(MethodGen mg, Number a, Number b,  Instruction op){
        ConstantPoolGen cpgen = mg.getConstantPool();
        Instruction inst = null;

        if(a.longValue() == b.longValue()){
            inst = new LDC(cpgen.addInteger(0));
        }
        if(a.longValue() < b.longValue()){
            inst = new LDC(cpgen.addInteger(-1));
        }
        if(a.longValue() > b.longValue()){
            inst = new LDC(cpgen.addInteger(1));
        }
        return inst;
    }

    private Instruction singleBranchFold(MethodGen mg, Number a, Instruction op){
        ConstantPoolGen cpgen = mg.getConstantPool();
        Instruction inst = null;

        if (op instanceof IFEQ){
            if(a.intValue() == 0){
                inst = new GOTO(((IFEQ) op).getTarget());
            }
            else {
                inst = new NOP();
            }
        }
        else if (op instanceof IFGE){
            if(a.intValue() >= 0){
                inst = new GOTO(((IFGE) op).getTarget());
            }
            else {
                inst = new NOP();
            }
        }
        else if (op instanceof IFGT){
            if(a.intValue() > 0){
                inst = new GOTO(((IFGT) op).getTarget());
            }
            else {
                inst = new NOP();
            }
        }
        else if (op instanceof IFLE){
            if(a.intValue() <= 0){
                inst = new GOTO(((IFLE) op).getTarget());
            }
            else {
                inst = new NOP();
            }
        }
        else if (op instanceof IFLT){
            if(a.intValue() < 0){
                inst = new GOTO(((IFLT) op).getTarget());
            }
            else {
                inst = new NOP();
            }
        }
        else if (op instanceof IFNE){
            if(a.intValue() != 0){
                inst = new GOTO(((IFNE) op).getTarget());
            }
            else {
                inst = new NOP();
            }
        }
        return inst;
    }


    public void write(String optimisedFilePath) {
        this.optimize();

        try {
            FileOutputStream out = new FileOutputStream(new File(optimisedFilePath));
            this.optimized.dump(out);
        } catch (FileNotFoundException e) {
            // Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // Auto-generated catch block
            e.printStackTrace();
        }
    }
}