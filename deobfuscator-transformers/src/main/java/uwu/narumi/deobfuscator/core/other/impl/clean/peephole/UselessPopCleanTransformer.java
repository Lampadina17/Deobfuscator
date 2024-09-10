package uwu.narumi.deobfuscator.core.other.impl.clean.peephole;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.OriginalSourceValue;
import uwu.narumi.deobfuscator.api.asm.InstructionContext;
import uwu.narumi.deobfuscator.api.context.Context;
import uwu.narumi.deobfuscator.api.transformer.FramedInstructionsTransformer;

import java.util.stream.Stream;

// TODO: Handle DUP2
public class UselessPopCleanTransformer extends FramedInstructionsTransformer {
  public UselessPopCleanTransformer() {
    this.rerunOnChange = true;
  }

  @Override
  protected Stream<AbstractInsnNode> buildInstructionsStream(Stream<AbstractInsnNode> stream) {
    return stream
        .filter(insn -> insn.getOpcode() == POP || insn.getOpcode() == POP2);
  }

  @Override
  protected boolean transformInstruction(Context context, InstructionContext insnContext) {
    AbstractInsnNode insn = insnContext.insn();

    boolean shouldRemovePop = false;

    OriginalSourceValue firstValue = insnContext.frame().getStack(insnContext.frame().getStackSize() - 1);
    if (insn.getOpcode() == POP && areProducersConstant(firstValue)) {
      // Pop the value from the stack
      popSourceValue(firstValue, insnContext.methodNode());
      shouldRemovePop = true;
    } else if (insn.getOpcode() == POP2) {
      if (areTwoSizedValues(firstValue)) {
        // Pop 2-sized value from the stack
        popSourceValue(firstValue, insnContext.methodNode());
        shouldRemovePop = true;
      } else {
        int index = insnContext.frame().getStackSize() - 2;
        OriginalSourceValue secondValue = index >= 0 ? insnContext.frame().getStack(insnContext.frame().getStackSize() - 2) : null;

        // Pop two values from the stack
        if (areProducersConstant(firstValue) && (secondValue == null || areProducersConstant(secondValue))) {
          popSourceValue(firstValue, insnContext.methodNode());
          if (secondValue != null) {
            popSourceValue(secondValue, insnContext.methodNode());
          }

          shouldRemovePop = true;
        }
      }
    }

    if (shouldRemovePop) {
      insnContext.methodNode().instructions.remove(insn);
    }

    return shouldRemovePop;
  }

  private boolean isSourceValueRemovable(OriginalSourceValue sourceValue) {
    if (sourceValue.insns.isEmpty()) {
      // Nothing to remove. Probably a local variable
      return false;
    }
    if (!sourceValue.getChildren().isEmpty()) {
      // Other source values depends on this source value
      return false;
    }

    return true;
  }

  /**
   * Checks if all producers of the source value are constants
   */
  private boolean areProducersConstant(OriginalSourceValue sourceValue) {
    if (!isSourceValueRemovable(sourceValue)) return false;

    for (AbstractInsnNode producer : sourceValue.insns) {
      if (!(producer.isConstant() || producer.getOpcode() == DUP)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Checks if all producers of the source value are 2-sized values
   */
  private boolean areTwoSizedValues(OriginalSourceValue sourceValue) {
    if (!isSourceValueRemovable(sourceValue)) return false;

    return sourceValue.getSize() == 2;
  }

  private void popSourceValue(OriginalSourceValue value, MethodNode methodNode) {
    for (AbstractInsnNode producer : value.insns) {
      methodNode.instructions.remove(producer);
    }
  }
}
