package comp207p.main;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.*;
import org.apache.bcel.util.InstructionFinder;



public class ConstantFolder
{
	ClassParser parser = null;
	ClassGen gen = null;

	JavaClass original = null;
	JavaClass optimized = null;

	public ConstantFolder(String classFilePath)
	{
		try{
			this.parser = new ClassParser(classFilePath);
			this.original = this.parser.parse();
			this.gen = new ClassGen(this.original);
		} catch(IOException e){
			e.printStackTrace();
		}
	}


	public void optimize()
	{
		ClassGen cgen = new ClassGen(original);
		cgen.setMajor(50);
		ConstantPoolGen cpgen = cgen.getConstantPool();


		Method[] methods = cgen.getMethods();

		for(Method method : methods) {
			MethodGen mg = new MethodGen(method, cgen.getClassName(), cpgen);


			System.out.println(cgen.getClassName() + " > " + method.getName());
			System.out.println(mg.getInstructionList());


			Method improved = improveMethod(mg);

			cgen.replaceMethod(method, improved);
			method = improved;

			mg = new MethodGen(method, cgen.getClassName(), cpgen);

			System.out.println(cgen.getClassName() + " > " + method.getName() + " OPTIMISED!!!!");
			System.out.println(mg.getInstructionList());
		}


		this.optimized = cgen.getJavaClass();
	}

	private Method improveMethod(MethodGen mg) {

		InstructionList il = mg.getInstructionList();

		//We need too loop the following 3 (we might possibly need more) methods
		//until there are no more changes to be done



//		The following while loop is appropriate for when we have all the functions

			int count = 1;

			while(count > 0) {
				count = 0;
				count += simpleFolding(mg, il);
				//System.out.println("------------- " + count);
			}
//		System.out.println("optimised with stores");
//			deleteStores(mg, il);
//		System.out.println("optimised, deleted stores");




		mg.setMaxStack();
		mg.setMaxLocals();
		Method m = mg.getMethod();
		il.dispose();

		return m;
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

				Number ln = getVal(l, cpgen, match, il);
				Number rn = getVal(r, cpgen, match, il);

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


				Number ln = getVal(l, cpgen, match, il);

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

				Number ln = getVal(l, cpgen, match, il);
				Number rn = getVal(r, cpgen, match, il);

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

				Number ln = getVal(l, cpgen, match, il);
				Number rn = getVal(r, cpgen, match, il);

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


				Number ln = getVal(l, cpgen, match, il);

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

//	private int propogation(MethodGen mg, InstructionList il){
//		ConstantPoolGen cpgen = mg.getConstantPool();
//		InstructionFinder f = new InstructionFinder(il);
//
//		int counter = 0;
//
//		String regexp = "ILOAD | DLOAD | FLOAD | LLOAD";
//		InstructionHandle next = null;
//
//		for (Iterator i = f.search(regexp); i.hasNext();){
//			InstructionHandle[] match = (InstructionHandle[]) i.next();
//
//			System.out.println("we have a load instruction");
//			LoadInstruction load = (LoadInstruction)match[0].getInstruction();
//			Number loadedNumber = null;
//			Instruction replace = null;
//			Number loadIndex = load.getIndex();
//			System.out.println("index: " + load.getIndex());
//
//			InstructionHandle l = match[0].getPrev();
//
//			while (l != null){
//				Instruction instruction = l.getInstruction();
//
//				if(instruction instanceof StoreInstruction){
//					int matchingIndex = ((StoreInstruction) instruction).getIndex();
//					System.out.println("we got here, matching index: " + matchingIndex);
//					System.out.println(l.getPrev());
//
//					if ((load.getIndex() == matchingIndex) && l.getPrev().getInstruction() instanceof PushInstruction){
//						loadedNumber = getVal((PushInstruction)l.getPrev().getInstruction(), cpgen, match);
//						replace = l.getPrev().getInstruction();
//						System.out.println("loadedNumber");
//					}
//				}
//				l = l.getPrev();
//			}
//			if (replace != null) {
//				match[0].setInstruction(replace);
//			}
//		}
//
//		return counter;
//	}


	private Number getVal(PushInstruction inst, ConstantPoolGen cpgen, InstructionHandle[] match, InstructionList il) {
		Number n = null;

		if (inst instanceof LDC) {
			n = (Number) ((LDC) inst).getValue(cpgen);
		}
		else if(inst instanceof LDC_W) {
			n = (Number) ((LDC_W) inst).getValue(cpgen);
		}
		else if(inst instanceof LDC2_W) {
			n = (Number) ((LDC2_W) inst).getValue(cpgen);
		}
		else if(inst instanceof ConstantPushInstruction) {
			n = (Number) ((ConstantPushInstruction) inst).getValue();
		}
		else if(inst instanceof LoadInstruction) {
			int requiredIndex = ((LoadInstruction) inst).getIndex();
			InstructionHandle l = match[0].getPrev();
			while(l != null) {
				if(l.getInstruction() instanceof StoreInstruction && ((StoreInstruction) l.getInstruction()).getIndex() == requiredIndex) {
					if(l.getPrev().getInstruction() instanceof PushInstruction) {
						n = getVal((PushInstruction) l.getPrev().getInstruction(), cpgen, match, il);
						return n;
					}	
				}
			l = l.getPrev();
			}
		}

		return n;
	}

	private Instruction conversionFold(MethodGen mg, Number b,  Instruction op) {
		ConstantPoolGen cpgen = mg.getConstantPool();
		Instruction inst = null;

		if(op instanceof I2D) {
			inst = new LDC2_W(cpgen.addDouble((double) b.doubleValue()));
		}
		else if(op instanceof I2F) {
			inst = new LDC(cpgen.addFloat((float) b.floatValue()));
		}
		else if(op instanceof I2L) {
			inst = new LDC2_W(cpgen.addLong((long) b.longValue()));
		}
		else if(op instanceof D2F) {
			inst = new LDC(cpgen.addFloat((float) b.floatValue()));
		}
		else if(op instanceof D2I) {
			inst = new LDC(cpgen.addInteger((int) b.intValue()));
		}
		else if(op instanceof D2L) {
			inst = new LDC2_W(cpgen.addLong((long) b.longValue()));
		}
		else if(op instanceof F2D) {
			inst = new LDC2_W(cpgen.addDouble((double) b.doubleValue()));
		}
		else if(op instanceof F2I) {
			inst = new LDC(cpgen.addInteger((int) b.intValue()));
		}
		else if(op instanceof F2L) {
			inst = new LDC2_W(cpgen.addLong((long) b.longValue()));
		}
		else if(op instanceof L2I) {
			inst = new LDC(cpgen.addInteger((int) b.intValue()));
		}
		else if(op instanceof L2D) {
			inst = new LDC2_W(cpgen.addDouble((double) b.doubleValue()));
		}
		else if(op instanceof L2F) {
			inst = new LDC(cpgen.addFloat((float) b.floatValue()));
		}


		return inst;
	}

	private Instruction arithmeticFold(MethodGen mg, Number a, Number b,  Instruction op) {
		ConstantPoolGen cpgen = mg.getConstantPool();
		Instruction inst = null;

		if (op instanceof IADD) {
			inst = new LDC(cpgen.addInteger(a.intValue() + b.intValue()));
		}
		else if (op instanceof ISUB) {
			inst = new LDC(cpgen.addInteger(a.intValue() - b.intValue()));
		}
		else if (op instanceof IMUL) {
			inst = new LDC(cpgen.addInteger(a.intValue() * b.intValue()));
		}
		else if (op instanceof IDIV) {
			inst = new LDC(cpgen.addInteger(a.intValue() / b.intValue()));
		}
		else if (op instanceof IREM) {
			inst = new LDC(cpgen.addInteger(a.intValue() % b.intValue()));
		}
		else if (op instanceof DADD) {
			inst = new LDC2_W(cpgen.addDouble(a.doubleValue() + b.doubleValue()));
		}
		else if (op instanceof DSUB) {
			inst = new LDC2_W(cpgen.addDouble(a.doubleValue() - b.doubleValue()));
		}
		else if (op instanceof DMUL) {
			inst = new LDC2_W(cpgen.addDouble(a.doubleValue() * b.doubleValue()));
		}
		else if (op instanceof DDIV) {
			inst = new LDC2_W(cpgen.addDouble(a.doubleValue() / b.doubleValue()));
		}
		else if (op instanceof DREM) {
			inst = new LDC2_W(cpgen.addDouble(a.doubleValue() % b.doubleValue()));
		}
		else if (op instanceof FADD) {
			inst = new LDC(cpgen.addFloat(a.floatValue() + b.floatValue()));
		}
		else if (op instanceof FSUB) {
			inst = new LDC(cpgen.addFloat(a.floatValue() - b.floatValue()));
		}
		else if (op instanceof FMUL) {
			inst = new LDC(cpgen.addFloat(a.floatValue() * b.floatValue()));
		}
		else if (op instanceof FDIV) {
			inst = new LDC(cpgen.addFloat(a.floatValue() / b.floatValue()));
		}
		else if (op instanceof FREM) {
			inst = new LDC(cpgen.addFloat(a.floatValue() % b.floatValue()));
		}
		else if (op instanceof LADD) {
			inst = new LDC2_W(cpgen.addLong(a.longValue() + b.longValue()));
		}
		else if (op instanceof LSUB) {
			inst = new LDC2_W(cpgen.addLong(a.longValue() - b.longValue()));
		}
		else if (op instanceof LMUL) {
			inst = new LDC2_W(cpgen.addLong(a.longValue() * b.longValue()));
		}
		else if (op instanceof LDIV) {
			inst = new LDC2_W(cpgen.addLong(a.longValue() / b.longValue()));
		}
		else if (op instanceof LREM) {
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

	private void deleteStores(MethodGen mg, InstructionList il){
		ConstantPoolGen cpgen = mg.getConstantPool();
		InstructionFinder f = new InstructionFinder(il);

		String regexp = "PushInstruction StoreInstruction";
		for (Iterator i = f.search(regexp); i.hasNext();) {
			InstructionHandle[] match = (InstructionHandle[]) i.next();

			System.out.println(match[0].getInstruction());
			System.out.println(match[1].getInstruction());
			System.out.println(match[1].getNext().getInstruction());

			if(match[0].getPosition() != 0){
				try{
					il.delete(match[0], match[1]);
				}
				catch(TargetLostException e){
					e.printStackTrace();
				}
			}
			else{
				InstructionHandle l = match[1].getNext();
				while (l.getInstruction() instanceof PushInstruction && l.getNext().getInstruction() instanceof StoreInstruction){
					l = l.getNext().getNext();
				}
				match[0].setInstruction(l.getInstruction());
				try{
					il.delete(match[1], match[1].getNext());
				} catch (TargetLostException e) {
					e.printStackTrace();
				}
			}



//			if(match[1].getNext().getInstruction() != null){
//				match[0].setInstruction(match[1].getNext().getInstruction());
//				try{
//					il.delete(match[1], match[1].getNext());
//				} catch (TargetLostException e) {
//					e.printStackTrace();
//				}
//			}
//			else{
//				try{
//					il.delete(match[0], match[1]);
//				} catch (TargetLostException e) {
//					e.printStackTrace();
//				}
//			}
//
		}
	}

//	private void secondaryDeleteStores(MethodGen mg, InstructionList il){
//		ConstantPoolGen cpgen = mg.getConstantPool();
//		InstructionFinder f = new InstructionFinder(il);
//
//		ArrayList<InstructionHandle> toDelete = new ArrayList<>();
//		boolean deletingFirst = false;
//		String regexp = "PushInstruction StoreInstruction";
//		for (Iterator i = f.search(regexp); i.hasNext();) {
//			InstructionHandle[] match = (InstructionHandle[]) i.next();
//			toDelete.add(match[0]);
//			toDelete.add(match[1]);
//			if(match[0].getPosition() == 0){
//				deletingFirst = true;
//			}
//		}
//	}

	public void write(String optimisedFilePath)
	{
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
