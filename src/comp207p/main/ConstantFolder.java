package comp207p.main;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
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
	        System.out.println("Instruction list : " + mg.getInstructionList());
		}
		
        
		this.optimized = gen.getJavaClass();
	}
	
	private Method improveMethod(MethodGen mg) {
		
		InstructionList il = mg.getInstructionList();
		
		//We need too loop the following 3 (we might possibly need more) methods
		//until there are no more changes to be done 
		

			
			for(int i = 0; i < 15; i++) {
				simpleFolding(mg, il);
			}
			
			
//			The following while loop is appropriate for when we have all the functions
			
//			int count = 1;
//			
//			while(count > 0) {
//				count = 0;
//				count += simpleFolding(mg, il);
//				System.out.println("------------- " + count);
//				//count += propagation(mg,il);
//				//count += dynamicFolding()
//				//possibly more
//			}

		
		
		
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
		
		String regexp = "(PushInstruction ConversionInstruction) | (PushInstruction PushInstruction ArithmeticInstruction)";
		
		for (Iterator i = f.search(regexp); i.hasNext();) {
			
			InstructionHandle[] match = (InstructionHandle[]) i.next();
		
			
			if(match.length == 3) {
				
				PushInstruction l = (PushInstruction)match[0].getInstruction();
			    PushInstruction r = (PushInstruction)match[1].getInstruction();
			    Instruction op = match[2].getInstruction();
			  
				
				Number ln = getVal(l, cpgen, match);
				Number rn = getVal(r, cpgen, match);
				
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
			else if(match.length == 2) {
				
				PushInstruction l = (PushInstruction)match[0].getInstruction();
				ConversionInstruction op = (ConversionInstruction)match[1].getInstruction();
				
				
				Number ln = getVal(l, cpgen, match);
				
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
			
			
		}
		
		return counter;
	}
	
	
	private Number getVal(PushInstruction inst, ConstantPoolGen cpgen, InstructionHandle[] match) {
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
			
			InstructionHandle ih = match[0];
			int index = ((LoadInstruction) inst).getIndex();
			InstructionHandle i = match[0].getPrev();
			
			while (i != null ) {
				Instruction instruction = i.getInstruction();
				
				if(instruction instanceof StoreInstruction) {
					int currIndex = ((StoreInstruction) instruction).getIndex();
					
					
					if(index == currIndex && i.getPrev().getInstruction() instanceof PushInstruction) {

						match[0].setInstruction(i.getPrev().getInstruction());
						
						getVal((PushInstruction)match[0].getInstruction(), cpgen, match);
						
						break;
					}
				}
				
				i = i.getPrev();
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